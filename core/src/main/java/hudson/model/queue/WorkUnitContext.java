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

import hudson.model.Action;
import hudson.model.Executor;
import hudson.model.Queue;
import hudson.model.Queue.BuildableItem;
import hudson.model.Queue.Task;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Holds the information shared between {@link WorkUnit}s created from the same {@link Task}.
 *
 * @author Kohsuke Kawaguchi
 */
public final class WorkUnitContext {

    public final BuildableItem item;

    public final Task task;

    /**
     * Once the execution is complete, update this future object with the outcome.
     */
    public final FutureImpl future;

    /**
     * Associated parameters to the build.
     */
    public final List<Action> actions;

    private final Latch startLatch, endLatch;

    private List<WorkUnit> workUnits = new ArrayList<WorkUnit>();

    public WorkUnitContext(BuildableItem item) {
        this.item = item;
        this.task = item.task;
        this.future = (FutureImpl)item.getFuture();
        this.actions = item.getActions();
        
        // +1 for the main task
        int workUnitSize = task.getSubTasks().size();
        startLatch = new Latch(workUnitSize) {
            @Override
            protected void onCriteriaMet() {
                // on behalf of the member Executors,
                // the one that executes the main thing will send notifications
                Executor e = Executor.currentExecutor();
                if (e.getCurrentWorkUnit().isMainWork()) {
                    e.getOwner().taskAccepted(e,task);
                }
            }
        };

        endLatch = new Latch(workUnitSize);
    }

    /**
     * Called by the executor that executes a member {@link SubTask} that belongs to this task
     * to create its {@link WorkUnit}.
     */
    public WorkUnit createWorkUnit(SubTask execUnit) {
        future.addExecutor(Executor.currentExecutor());
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
    public synchronized void synchronizeStart() throws InterruptedException {
        startLatch.synchronize();
    }

    public synchronized void synchronizeEnd(Queue.Executable executable, Throwable problems, long duration) throws InterruptedException {
        endLatch.synchronize();

        // the main thread will send a notification
        Executor e = Executor.currentExecutor();
        WorkUnit wu = e.getCurrentWorkUnit();
        if (wu.isMainWork()) {
            if (problems == null) {
                future.set(executable);
                e.getOwner().taskCompleted(e, task, duration);
            } else {
                future.set(problems);
                e.getOwner().taskCompletedWithProblems(e, task, duration, problems);
            }
        }
    }

    public void abort() {
        startLatch.abort();
        endLatch.abort();
    }
}
