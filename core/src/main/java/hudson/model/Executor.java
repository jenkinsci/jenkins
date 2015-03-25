/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Brian Westrich, Red Hat, Inc., Stephen Connolly, Tom Huybrechts
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.model;

import hudson.FilePath;
import hudson.Util;
import hudson.model.Queue.Executable;
import hudson.model.queue.Executables;
import hudson.model.queue.SubTask;
import hudson.model.queue.Tasks;
import hudson.model.queue.WorkUnit;
import hudson.security.ACL;
import hudson.util.InterceptingProxy;
import hudson.util.TimeUnit2;
import jenkins.model.CauseOfInterruption;
import jenkins.model.CauseOfInterruption.UserInterruption;
import jenkins.model.InterruptedBuildAction;
import jenkins.model.Jenkins;
import org.acegisecurity.Authentication;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import org.kohsuke.stapler.interceptor.RequirePOST;

import javax.annotation.concurrent.GuardedBy;
import javax.servlet.ServletException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import static hudson.model.queue.Executables.*;
import static java.util.logging.Level.*;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;


/**
 * Thread that executes builds.
 * Since 1.536, {@link Executor}s start threads on-demand.
 * The entire logic should use {@link #isActive()} instead of {@link #isAlive()}
 * in order to check if the {@link Executor} it ready to take tasks.
 * @author Kohsuke Kawaguchi
 */
@ExportedBean
public class Executor extends Thread implements ModelObject {
    protected final @Nonnull Computer owner;
    private final Queue queue;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    @GuardedBy("lock")
    private long startTime;
    /**
     * Used to track when a job was last executed.
     */
    private final long creationTime = System.currentTimeMillis();

    /**
     * Executor number that identifies it among other executors for the same {@link Computer}.
     */
    private int number;
    /**
     * {@link hudson.model.Queue.Executable} being executed right now, or null if the executor is idle.
     */
    @GuardedBy("lock")
    private Queue.Executable executable;

    /**
     * When {@link Queue} allocates a work for this executor, this field is set
     * and the executor is {@linkplain Thread#start() started}.
     */
    @GuardedBy("lock")
    private WorkUnit workUnit;

    private Throwable causeOfDeath;

    private boolean induceDeath;

    @GuardedBy("lock")
    private boolean started;

    /**
     * When the executor is interrupted, we allow the code that interrupted the thread to override the
     * result code it prefers.
     */
    @GuardedBy("lock")
    private Result interruptStatus;

    /**
     * Cause of interruption. Access needs to be synchronized.
     */
    @GuardedBy("lock")
    private final List<CauseOfInterruption> causes = new Vector<CauseOfInterruption>();

    public Executor(@Nonnull Computer owner, int n) {
        super("Executor #"+n+" for "+owner.getDisplayName());
        this.owner = owner;
        this.queue = Jenkins.getInstance().getQueue();
        this.number = n;
    }

    @Override
    public void interrupt() {
        interrupt(Result.ABORTED);
    }

    /**
     * Interrupt the execution,
     * but instead of marking the build as aborted, mark it as specified result.
     *
     * @since 1.417
     */
    public void interrupt(Result result) {
        Authentication a = Jenkins.getAuthentication();
        if (a == ACL.SYSTEM)
            interrupt(result, new CauseOfInterruption[0]);
        else {
            // worth recording who did it
            // avoid using User.get() to avoid deadlock.
            interrupt(result, new UserInterruption(a.getName()));
        }
    }

    /**
     * Interrupt the execution. Mark the cause and the status accordingly.
     */
    public void interrupt(Result result, CauseOfInterruption... causes) {
        if (LOGGER.isLoggable(FINE))
            LOGGER.log(FINE, String.format("%s is interrupted(%s): %s", getDisplayName(), result, Util.join(Arrays.asList(causes),",")), new InterruptedException());

        lock.writeLock().lock();
        try {
            if (!started) {
                // not yet started, so simply dispose this
                owner.removeExecutor(this);
                return;
            }

            interruptStatus = result;

            for (CauseOfInterruption c : causes) {
                if (!this.causes.contains(c))
                    this.causes.add(c);
            }
        } finally {
            lock.writeLock().unlock();
        }
        super.interrupt();
    }

    public Result abortResult() {
        lock.readLock().lock();
        try {
            Result r = interruptStatus;
            if (r == null) r =
                    Result.ABORTED; // this is when we programmatically throw InterruptedException instead of calling the interrupt method.

            return r;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * report cause of interruption and record it to the build, if available.
     *
     * @since 1.425
     */
    public void recordCauseOfInterruption(Run<?,?> build, TaskListener listener) {
        List<CauseOfInterruption> r;

        // atomically get&clear causes.
        lock.writeLock().lock();
        try {
            if (causes.isEmpty())   return;
            r = new ArrayList<CauseOfInterruption>(causes);
            causes.clear();
        } finally {
            lock.writeLock().unlock();
        }

        build.addAction(new InterruptedBuildAction(r));
        for (CauseOfInterruption c : r)
            c.print(listener);
    }

    /**
     * There are some cases where an executor is started but the node is removed or goes off-line before we are ready
     * to start executing the assigned work unit. This method is called to clear the assigned work unit so that
     * the {@link Queue#maintain()} method can return the item to the buildable state.
     *
     * Note: once we create the {@link Executable} we cannot unwind the state and the build will have to end up being
     * marked as a failure.
     */
    private void resetWorkUnit(String reason) {
        StringWriter writer = new StringWriter();
        PrintWriter pw = new PrintWriter(writer);
        try {
            pw.printf("%s grabbed %s from queue but %s %s. ", getName(), workUnit, owner.getDisplayName(), reason);
            if (owner.getTerminatedBy().isEmpty()) {
                pw.print("No termination trace available.");
            } else {
                pw.println("Termination trace follows:");
                for (Computer.TerminationRequest request: owner.getTerminatedBy()) {
                    request.printStackTrace(pw);
                }
            }
        } finally {
            pw.close();
        }
        LOGGER.log(WARNING, writer.toString());
        lock.writeLock().lock();
        try {
            if (executable != null) {
                throw new IllegalStateException("Cannot reset the work unit after the executable has been created");
            }
            workUnit = null;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void run() {
        if (!owner.isOnline()) {
            resetWorkUnit("went off-line before the task's worker thread started");
            owner.removeExecutor(this);
            queue.scheduleMaintenance();
            return;
        }
        if (owner.getNode() == null) {
            resetWorkUnit("was removed before the task's worker thread started");
            owner.removeExecutor(this);
            queue.scheduleMaintenance();
            return;
        }
        final WorkUnit workUnit;
        lock.writeLock().lock();
        try {
            startTime = System.currentTimeMillis();
            workUnit = this.workUnit;
        } finally {
            lock.writeLock().unlock();
        }

        ACL.impersonate(ACL.SYSTEM);

        try {
            if (induceDeath)        throw new ThreadDeath();

            SubTask task;
            // transition from idle to building.
            // perform this state change as an atomic operation wrt other queue operations
            task = Queue.withLock(new java.util.concurrent.Callable<SubTask>() {
                @Override
                public SubTask call() throws Exception {
                    if (!owner.isOnline()) {
                        resetWorkUnit("went off-line before the task's worker thread was ready to execute");
                        return null;
                    }
                    if (owner.getNode() == null) {
                        resetWorkUnit("was removed before the task's worker thread was ready to execute");
                        return null;
                    }
                    // after this point we cannot unwind the assignment of the work unit, if the owner
                    // is removed or goes off-line then the build will just have to fail.
                    workUnit.setExecutor(Executor.this);
                    queue.onStartExecuting(Executor.this);
                    if (LOGGER.isLoggable(FINE))
                        LOGGER.log(FINE, getName()+" grabbed "+workUnit+" from queue");
                    SubTask task = workUnit.work;
                    Executable executable = task.createExecutable();
                    lock.writeLock().lock();
                    try {
                        Executor.this.executable = executable;
                    } finally {
                        lock.writeLock().unlock();
                    }
                    workUnit.setExecutable(executable);
                    return task;
                }
            });
            Executable executable;
            lock.readLock().lock();
            try {
                if (this.workUnit == null) {
                    // we called resetWorkUnit, so bail. Outer finally will remove this and schedule queue maintenance
                    return;
                }
                executable = this.executable;
            } finally {
                lock.readLock().unlock();
            }
            if (LOGGER.isLoggable(FINE))
                LOGGER.log(FINE, getName()+" is going to execute "+executable);

            Throwable problems = null;
            try {
                workUnit.context.synchronizeStart();

                // this code handles the behavior of null Executables returned
                // by tasks. In such case Jenkins starts the workUnit in order
                // to report results to console outputs.
                if (executable == null) {
                    throw new Error("The null Executable has been created for "+workUnit+". The task cannot be executed");
                }

                if (executable instanceof Actionable) {
                    for (Action action: workUnit.context.actions) {
                        ((Actionable) executable).addAction(action);
                    }
                }

                ACL.impersonate(workUnit.context.item.authenticate());
                setName(getName() + " : executing " + executable.toString());
                if (LOGGER.isLoggable(FINE))
                    LOGGER.log(FINE, getName()+" is now executing "+executable);
                queue.execute(executable, task);
            } catch (Throwable e) {
                // for some reason the executor died. this is really
                // a bug in the code, but we don't want the executor to die,
                // so just leave some info and go on to build other things
                LOGGER.log(Level.SEVERE, "Executor threw an exception", e);
                workUnit.context.abort(e);
                problems = e;
            } finally {
                long time = System.currentTimeMillis()-startTime;
                if (LOGGER.isLoggable(FINE))
                    LOGGER.log(FINE, getName()+" completed "+executable+" in "+time+"ms");
                try {
                    workUnit.context.synchronizeEnd(executable,problems,time);
                } catch (InterruptedException e) {
                    workUnit.context.abort(e);
                } finally {
                    workUnit.setExecutor(null);
                }
            }
        } catch (InterruptedException e) {
            LOGGER.log(FINE, getName()+" interrupted",e);
            // die peacefully
        } catch(Exception e) {
            causeOfDeath = e;
            LOGGER.log(SEVERE, "Unexpected executor death", e);
        } catch (Error e) {
            causeOfDeath = e;
            LOGGER.log(SEVERE, "Unexpected executor death", e);
        } finally {
            for (RuntimeException e1: owner.getTerminatedBy()) LOGGER.log(Level.WARNING, String.format("%s termination trace", getName()), e1);
            if (causeOfDeath==null)
                // let this thread die and be replaced by a fresh unstarted instance
                owner.removeExecutor(this);

            queue.scheduleMaintenance();
        }
    }

    /**
     * For testing only. Simulate a fatal unexpected failure.
     */
    public void killHard() {
        induceDeath = true;
    }

    /**
     * Returns the current build this executor is running.
     *
     * @return
     *      null if the executor is idle.
     */
    @Exported
    public @CheckForNull Queue.Executable getCurrentExecutable() {
        lock.readLock().lock();
        try {
            return executable;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Returns the current {@link WorkUnit} (of {@link #getCurrentExecutable() the current executable})
     * that this executor is running.
     *
     * @return
     *      null if the executor is idle.
     */
    @Exported
    public WorkUnit getCurrentWorkUnit() {
        lock.readLock().lock();
        try {
            return workUnit;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * If {@linkplain #getCurrentExecutable() current executable} is {@link AbstractBuild},
     * return the workspace that this executor is using, or null if the build hasn't gotten
     * to that point yet.
     */
    public FilePath getCurrentWorkspace() {
        lock.readLock().lock();
        try {
            if (executable == null) {
                return null;
            }
            if (executable instanceof AbstractBuild) {
                AbstractBuild ab = (AbstractBuild) executable;
                return ab.getWorkspace();
            }
            return null;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Same as {@link #getName()}.
     */
    public String getDisplayName() {
        return "Executor #"+getNumber();
    }

    /**
     * Gets the executor number that uniquely identifies it among
     * other {@link Executor}s for the same computer.
     *
     * @return
     *      a sequential number starting from 0.
     */
    @Exported
    public int getNumber() {
        return number;
    }

    /**
     * Returns true if this {@link Executor} is ready for action.
     */
    @Exported
    public boolean isIdle() {
        lock.readLock().lock();
        try {
            return workUnit == null && executable == null;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * The opposite of {@link #isIdle()} &mdash; the executor is doing some work.
     */
    public boolean isBusy() {
        lock.readLock().lock();
        try {
            return workUnit != null || executable != null;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Check if executor is ready to accept tasks.
     * This method becomes the critical one since 1.536, which introduces the
     * on-demand creation of executor threads. The entire logic should use
     * this method instead of {@link #isAlive()}, because it provides wrong
     * information for non-started threads.
     * @return True if the executor is available for tasks
     * @since 1.536
     */
    public boolean isActive() {
        lock.readLock().lock();
        try {
            return !started || isAlive();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Returns true if this executor is waiting for a task to execute.
     */
    public boolean isParking() {
        lock.readLock().lock();
        try {
            return !started;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * If this thread dies unexpectedly, obtain the cause of the failure.
     *
     * @return null if the death is expected death or the thread is {@link #isAlive() still alive}.
     * @since 1.142
     */
    public Throwable getCauseOfDeath() {
        return causeOfDeath;
    }

    /**
     * Returns the progress of the current build in the number between 0-100.
     *
     * @return -1
     *      if it's impossible to estimate the progress.
     */
    @Exported
    public int getProgress() {
        long d;
        lock.readLock().lock();
        try {
            if (executable == null) {
                return -1;
            }
            d = Executables.getEstimatedDurationFor(executable);
        } finally {
            lock.readLock().unlock();
        }
        if (d < 0) {
            return -1;
        }

        int num = (int) (getElapsedTime() * 100 / d);
        if (num >= 100) {
            num = 99;
        }
        return num;
    }

    /**
     * Returns true if the current build is likely stuck.
     *
     * <p>
     * This is a heuristics based approach, but if the build is suspiciously taking for a long time,
     * this method returns true.
     */
    @Exported
    public boolean isLikelyStuck() {
        long d;
        long elapsed;
        lock.readLock().lock();
        try {
            if (executable == null) {
                return false;
            }

            elapsed = getElapsedTime();
            d = Executables.getEstimatedDurationFor(executable);
        } finally {
            lock.readLock().unlock();
        }
        if (d >= 0) {
            // if it's taking 10 times longer than ETA, consider it stuck
            return d * 10 < elapsed;
        } else {
            // if no ETA is available, a build taking longer than a day is considered stuck
            return TimeUnit2.MILLISECONDS.toHours(elapsed) > 24;
        }
    }

    public long getElapsedTime() {
        lock.readLock().lock();
        try {
            return System.currentTimeMillis() - startTime;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Returns the number of milli-seconds the currently executing job spent in the queue
     * waiting for an available executor. This excludes the quiet period time of the job.
     * @since 1.440
     */
    public long getTimeSpentInQueue() {
        lock.readLock().lock();
        try {
            return startTime - workUnit.context.item.buildableStartMilliseconds;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Gets the string that says how long since this build has started.
     *
     * @return
     *      string like "3 minutes" "1 day" etc.
     */
    public String getTimestampString() {
        return Util.getPastTimeString(getElapsedTime());
    }

    /**
     * Computes a human-readable text that shows the expected remaining time
     * until the build completes.
     */
    public String getEstimatedRemainingTime() {
        long d;
        lock.readLock().lock();
        try {
            if (executable == null) {
                return Messages.Executor_NotAvailable();
            }

            d = Executables.getEstimatedDurationFor(executable);
        } finally {
            lock.readLock().unlock();
        }
        if (d < 0) {
            return Messages.Executor_NotAvailable();
        }

        long eta = d - getElapsedTime();
        if (eta <= 0) {
            return Messages.Executor_NotAvailable();
        }

        return Util.getTimeSpanString(eta);
    }

    /**
     * The same as {@link #getEstimatedRemainingTime()} but return
     * it as a number of milli-seconds.
     */
    public long getEstimatedRemainingTimeMillis() {
        long d;
        lock.readLock().lock();
        try {
            if (executable == null) {
                return -1;
            }

            d = Executables.getEstimatedDurationFor(executable);
        } finally {
            lock.readLock().unlock();
        }
        if (d < 0) {
            return -1;
        }

        long eta = d - getElapsedTime();
        if (eta <= 0) {
            return -1;
        }

        return eta;
    }

    /**
     * Can't start executor like you normally start a thread.
     *
     * @see #start(WorkUnit)
     */
    @Override
    public void start() {
        throw new UnsupportedOperationException();
    }

    /*protected*/ void start(WorkUnit task) {
        lock.writeLock().lock();
        try {
            this.workUnit = task;
            super.start();
            started = true;
        } finally {
            lock.writeLock().unlock();
        }
    }


    /**
     * @deprecated as of 1.489
     *      Use {@link #doStop()}.
     */
    @RequirePOST
    public void doStop( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        doStop().generateResponse(req,rsp,this);
    }

    /**
     * Stops the current build.
     *
     * @since 1.489
     */
    @RequirePOST
    public HttpResponse doStop() {
        lock.writeLock().lock(); // need write lock as interrupt will change the field
        try {
            if (executable != null) {
                Tasks.getOwnerTaskOf(getParentOf(executable)).checkAbortPermission();
                interrupt();
            }
        } finally {
            lock.writeLock().unlock();
        }
        return HttpResponses.forwardToPreviousPage();
    }

    /**
     * Throws away this executor and get a new one.
     */
    @RequirePOST
    public HttpResponse doYank() {
        Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
        if (isAlive())
            throw new Failure("Can't yank a live executor");
        owner.removeExecutor(this);
        return HttpResponses.redirectViaContextPath("/");
    }

    /**
     * Checks if the current user has a permission to stop this build.
     */
    public boolean hasStopPermission() {
        lock.readLock().lock();
        try {
            return executable != null && Tasks.getOwnerTaskOf(getParentOf(executable)).hasAbortPermission();
        } finally {
            lock.readLock().unlock();
        }
    }

    public @Nonnull Computer getOwner() {
        return owner;
    }

    /**
     * Returns when this executor started or should start being idle.
     */
    public long getIdleStartMilliseconds() {
        lock.readLock().lock();
        try {
            if (isIdle())
                return Math.max(creationTime, owner.getConnectTime());
            else {
                return Math.max(startTime + Math.max(0, Executables.getEstimatedDurationFor(executable)),
                        System.currentTimeMillis() + 15000);
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Exposes the executor to the remote API.
     */
    public Api getApi() {
        return new Api(this);
    }

    /**
     * Creates a proxy object that executes the callee in the context that impersonates
     * this executor. Useful to export an object to a remote channel.
     */
    public <T> T newImpersonatingProxy(Class<T> type, T core) {
        return new InterceptingProxy() {
            protected Object call(Object o, Method m, Object[] args) throws Throwable {
                final Executor old = IMPERSONATION.get();
                IMPERSONATION.set(Executor.this);
                try {
                    return m.invoke(o,args);
                } finally {
                    IMPERSONATION.set(old);
                }
            }
        }.wrap(type,core);
    }

    /**
     * Returns the executor of the current thread or null if current thread is not an executor.
     */
    public static @CheckForNull Executor currentExecutor() {
        Thread t = Thread.currentThread();
        if (t instanceof Executor) return (Executor) t;
        return IMPERSONATION.get();
    }

    /**
     * Returns the estimated duration for the executable.
     * Protects against {@link AbstractMethodError}s if the {@link Executable} implementation
     * was compiled against Hudson < 1.383
     *
     * @deprecated as of 1.388
     *      Use {@link Executables#getEstimatedDurationFor(Queue.Executable)}
     */
    public static long getEstimatedDurationFor(Executable e) {
        return Executables.getEstimatedDurationFor(e);
    }

    /**
     * Mechanism to allow threads (in particular the channel request handling threads) to
     * run on behalf of {@link Executor}.
     */
    private static final ThreadLocal<Executor> IMPERSONATION = new ThreadLocal<Executor>();

    private static final Logger LOGGER = Logger.getLogger(Executor.class.getName());
}
