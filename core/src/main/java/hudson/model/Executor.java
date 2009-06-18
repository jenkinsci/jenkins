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

import hudson.Util;
import hudson.util.TimeUnit2;
import hudson.security.ACL;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.ExportedBean;
import org.kohsuke.stapler.export.Exported;
import org.acegisecurity.context.SecurityContextHolder;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.logging.Logger;
import java.util.logging.Level;


/**
 * Thread that executes builds.
 *
 * @author Kohsuke Kawaguchi
 */
@ExportedBean
public class Executor extends Thread implements ModelObject {
    private final Computer owner;
    private final Queue queue;

    private long startTime;
    /**
     * Used to track when a job was last executed.
     */
    private long finishTime;

    /**
     * Executor number that identifies it among other executors for the same {@link Computer}.
     */
    private int number;
    /**
     * {@link Queue.Executable} being executed right now, or null if the executor is idle.
     */
    private volatile Queue.Executable executable;

    private Throwable causeOfDeath;

    public Executor(Computer owner) {
        super("Executor #"+owner.getExecutors().size()+" for "+owner.getDisplayName());
        this.owner = owner;
        this.queue = Hudson.getInstance().getQueue();
        this.number = owner.getExecutors().size();
        start();
    }

    public void run() {
        // run as the system user. see ACL.SYSTEM for more discussion about why this is somewhat broken
        SecurityContextHolder.getContext().setAuthentication(ACL.SYSTEM);

        try {
            finishTime = System.currentTimeMillis();
            while(true) {
                if(Hudson.getInstance() == null || Hudson.getInstance().isTerminating())
                    return;

                synchronized(owner) {
                    if(owner.getNumExecutors()<owner.getExecutors().size()) {
                        // we've got too many executors.
                        owner.removeExecutor(this);
                        return;
                    }
                }

                // clear the interrupt flag as a precaution.
                // sometime an interrupt aborts a build but without clearing the flag.
                // see issue #1583
                Thread.interrupted();

                Queue.Item queueItem;
                try {
                	queueItem = queue.pop();
                } catch (InterruptedException e) {
                    continue;
                }

                Queue.Task task = queueItem.task;
                Throwable problems = null;
                owner.taskAccepted(this, task);
                try {
                    try {
                        startTime = System.currentTimeMillis();
                        executable = task.createExecutable();
                        if (executable instanceof Actionable) {
                        	for (Action action: queueItem.getActions()) {
                        		((Actionable) executable).addAction(action);
                        	}
                        }
                        queue.execute(executable, task);
                    } catch (Throwable e) {
                        // for some reason the executor died. this is really
                        // a bug in the code, but we don't want the executor to die,
                        // so just leave some info and go on to build other things
                        LOGGER.log(Level.SEVERE, "Executor throw an exception unexpectedly", e);
                        problems = e;
                    }
                } finally {
                    finishTime = System.currentTimeMillis();
                    if (problems == null) {
                        queueItem.future.set(executable);
                        owner.taskCompleted(this, task, finishTime - startTime);
                    } else {
                        queueItem.future.set(problems);
                        owner.taskCompletedWithProblems(this, task, finishTime - startTime, problems);
                    }
                }
                executable = null;
            }
        } catch(RuntimeException e) {
            causeOfDeath = e;
            throw e;
        } catch (Error e) {
            causeOfDeath = e;
            throw e;
        }
    }

    /**
     * Returns the current {@link Queue.Task} this executor is running.
     *
     * @return
     *      null if the executor is idle.
     */
    @Exported
    public Queue.Executable getCurrentExecutable() {
        return executable;
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
        return executable==null;
    }

    /**
     * The opposite of {@link #isIdle()} &mdash; the executor is doing some work.
     */
    public boolean isBusy() {
        return executable!=null;
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
        Queue.Executable e = executable;
        if(e==null)     return -1;
        long d = e.getParent().getEstimatedDuration();
        if(d<0)         return -1;

        int num = (int)(getElapsedTime()*100/d);
        if(num>=100)    num=99;
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
        Queue.Executable e = executable;
        if(e==null)     return false;

        long elapsed = getElapsedTime();
        long d = e.getParent().getEstimatedDuration();
        if(d>=0) {
            // if it's taking 10 times longer than ETA, consider it stuck
            return d*10 < elapsed;
        } else {
            // if no ETA is available, a build taking longer than a day is considered stuck
            return TimeUnit2.MILLISECONDS.toHours(elapsed)>24;
        }
    }

    public long getElapsedTime() {
        return System.currentTimeMillis() - startTime;
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
        Queue.Executable e = executable;
        if(e==null)     return Messages.Executor_NotAvailable();

        long d = e.getParent().getEstimatedDuration();
        if(d<0)         return Messages.Executor_NotAvailable();

        long eta = d-getElapsedTime();
        if(eta<=0)      return Messages.Executor_NotAvailable();

        return Util.getTimeSpanString(eta);
    }

    /**
     * The same as {@link #getEstimatedRemainingTime()} but return
     * it as a number of milli-seconds.
     */
    public long getEstimatedRemainingTimeMillis() {
        Queue.Executable e = executable;
        if(e==null)     return -1;

        long d = e.getParent().getEstimatedDuration();
        if(d<0)         return -1;

        long eta = d-getElapsedTime();
        if(eta<=0)      return -1;

        return eta;
    }

    /**
     * Stops the current build.
     */
    public void doStop( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        Queue.Executable e = executable;
        if(e!=null) {
            e.getParent().checkAbortPermission();
            interrupt();
        }
        rsp.forwardToPreviousPage(req);
    }

    /**
     * Checks if the current user has a permission to stop this build.
     */
    public boolean hasStopPermission() {
        Queue.Executable e = executable;
        return e!=null && e.getParent().hasAbortPermission();
    }

    public Computer getOwner() {
        return owner;
    }

    /**
     * Returns when this executor started or should start being idle.
     */
    public long getIdleStartMilliseconds() {
        if (isIdle())
            return finishTime;
        else {
            return Math.max(startTime + Math.max(0, executable.getParent().getEstimatedDuration()),
                    System.currentTimeMillis() + 15000);
        }
    }

    /**
     * Exposes the executor to the remote API.
     */
    public Api getApi() {
        return new Api(this);
    }

    /**
     * Returns the executor of the current thread or null if current thread is not an executor.
     */
    public static Executor currentExecutor() {
        Thread t = Thread.currentThread();
        return t instanceof Executor ? (Executor)t : null;
    }

    private static final Logger LOGGER = Logger.getLogger(Executor.class.getName());

}
