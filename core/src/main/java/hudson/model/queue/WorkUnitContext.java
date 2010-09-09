package hudson.model.queue;

import hudson.model.Executor;
import hudson.model.Queue;
import hudson.model.Queue.Task;

/**
 * Holds the information shared between {@link WorkUnit}s created from the same {@link Task}.
 *
 * @author Kohsuke Kawaguchi
 */
public final class WorkUnitContext {
    private final int workUnitSize;
    public final Task task;

    private final Latch startLatch, endLatch;

    public WorkUnitContext(Task _task) {
        this.task = _task;
        // +1 for the main task
        workUnitSize = task.getMemberExecutionUnits().size() + 1;
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
     * {@link Executor}s call this method to synchronize on the start of the task
     */
    public synchronized void synchronizeStart() throws InterruptedException {
        startLatch.synchronize();
    }

    public synchronized void synchronizeEnd(Queue.Executable executable, Throwable problems, long duration) throws InterruptedException {
        endLatch.synchronize();

        // the main thread will send a notification
        Executor e = Executor.currentExecutor();
        if (e.getCurrentWorkUnit().isMainWork()) {
            if (problems == null) {
                queueItem.future.set(executable);
                e.getOwner().taskCompleted(e, task, duration);
            } else {
                queueItem.future.set(problems);
                e.getOwner().taskCompletedWithProblems(e, task, duration, problems);
            }
        }
    }
}
