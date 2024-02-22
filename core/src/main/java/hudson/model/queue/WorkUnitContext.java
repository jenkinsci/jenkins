/*
 * The MIT License
 *
 * Copyright (c) 2010, InfraDNA, Inc.
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

package hudson.model.queue;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.ExtensionList;
import hudson.model.Action;
import hudson.model.Executor;
import hudson.model.ExecutorListener;
import hudson.model.Queue;
import hudson.model.Queue.BuildableItem;
import hudson.model.Queue.Task;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Holds the information shared between {@link WorkUnit}s created from the same {@link Task}.
 *
 * @author Kohsuke Kawaguchi
 */
public final class WorkUnitContext {

    private static final Logger LOGGER = Logger.getLogger(WorkUnitContext.class.getName());

    public final BuildableItem item;

    public final Task task;

    /**
     * Once the execution starts and completes, update this future object with the outcome.
     */
    public final FutureImpl future;

    /**
     * Associated parameters to the build.
     */
    public final List<Action> actions;

    private final Latch startLatch, endLatch;

    private List<WorkUnit> workUnits = new ArrayList<>();

    /**
     * If the execution is aborted, set to non-null that indicates where it was aborted.
     */
    private volatile Throwable aborted;

    public WorkUnitContext(BuildableItem item) {
        this.item = item;
        this.task = item.task;
        this.future = (FutureImpl) item.getFuture();
        // JENKINS-51584 do not use item.getAllActions() here.
        this.actions = new ArrayList<>(item.getActions());
        // +1 for the main task
        int workUnitSize = task.getSubTasks().size();
        startLatch = new Latch(workUnitSize) {
            @SuppressFBWarnings(value = "NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE", justification = "TODO needs triage")
            @Override
            protected void onCriteriaMet() {
                // on behalf of the member Executors,
                // the one that executes the main thing will send notifications
                // Unclear if this will work with AsynchronousExecution; it *seems* this is only called from synchronize which is only called from synchronizeStart which is only called from an executor thread.
                Executor e = Executor.currentExecutor();
                if (e.getCurrentWorkUnit().isMainWork()) {
                    e.getOwner().taskAccepted(e, task);
                    for (ExecutorListener listener : ExtensionList.lookup(ExecutorListener.class)) {
                        try {
                            listener.taskAccepted(e, task);
                        } catch (RuntimeException x) {
                            LOGGER.log(Level.WARNING, null, x);
                        }
                    }
                }
            }
        };

        endLatch = new Latch(workUnitSize);
    }

    /**
     * Called within the queue maintenance process to create a {@link WorkUnit} for the given {@link SubTask}
     */
    public WorkUnit createWorkUnit(SubTask execUnit) {
        WorkUnit wu = new WorkUnit(this, execUnit);
        workUnits.add(wu);
        return wu;
    }

    public List<WorkUnit> getWorkUnits() {
        return Collections.unmodifiableList(workUnits);
    }

    public WorkUnit getPrimaryWorkUnit() {
        return workUnits.get(0);
    }

    /**
     * All the {@link Executor}s that jointly execute a {@link Task} call this method to synchronize on the start.
     */
    @Restricted(NoExternalUse.class)
    public void synchronizeStart() throws InterruptedException {
        // Let code waiting for the start (future.start.get()) finishes when there is a faulty SubTask by setting the
        // future always.
        try {
            startLatch.synchronize();
        } finally {
            // the main thread will send a notification
            Executor e = Executor.currentExecutor();
            WorkUnit wu = e.getCurrentWorkUnit();
            if (wu.isMainWork()) {
                future.start.set(e.getCurrentExecutable());
                for (ExecutorListener listener : ExtensionList.lookup(ExecutorListener.class)) {
                    try {
                        listener.taskStarted(e, task);
                    } catch (RuntimeException x) {
                        LOGGER.log(Level.WARNING, null, x);
                    }
                }
            }
        }
    }

    @Deprecated
    public void synchronizeEnd(Queue.Executable executable, Throwable problems, long duration) throws InterruptedException {
        synchronizeEnd(Executor.currentExecutor(), executable, problems, duration);
    }

    /**
     * All the {@link Executor}s that jointly execute a {@link Task} call this method to synchronize on the end of the task.
     *
     * @throws InterruptedException
     *      If any of the member thread is interrupted while waiting for other threads to join, all
     *      the member threads will report {@link InterruptedException}.
     */
    @Restricted(NoExternalUse.class)
    public void synchronizeEnd(Executor e, Queue.Executable executable, Throwable problems, long duration) throws InterruptedException {
        // Let code waiting for the build to finish (future.get()) finishes when there is a faulty SubTask by setting
        // the future always.
        try {
            endLatch.synchronize();
        } finally {
            // the main thread will send a notification
            WorkUnit wu = e.getCurrentWorkUnit();
            if (wu.isMainWork()) {
                if (problems == null) {
                    future.set(executable);
                    e.getOwner().taskCompleted(e, task, duration);
                    for (ExecutorListener listener : ExtensionList.lookup(ExecutorListener.class)) {
                        try {
                            listener.taskCompleted(e, task, duration);
                        } catch (RuntimeException x) {
                            LOGGER.log(Level.WARNING, null, x);
                        }
                    }
                } else {
                    future.set(problems);
                    e.getOwner().taskCompletedWithProblems(e, task, duration, problems);
                    for (ExecutorListener listener : ExtensionList.lookup(ExecutorListener.class)) {
                        try {
                            listener.taskCompletedWithProblems(e, task, duration, problems);
                        } catch (RuntimeException x) {
                            LOGGER.log(Level.WARNING, null, x);
                        }
                    }
                }
                future.finished();
            }
        }
    }

    /**
     * When one of the work unit is aborted, call this method to abort all the other work units.
     */
    public synchronized void abort(Throwable cause) {
        if (cause == null)        throw new IllegalArgumentException();
        if (aborted != null)      return; // already aborted
        aborted = cause;
        startLatch.abort(cause);
        endLatch.abort(cause);

        Thread c = Thread.currentThread();
        for (WorkUnit wu : workUnits) {
            Executor e = wu.getExecutor();
            if (e != null && e != c)
                e.interrupt();
        }
    }
}
