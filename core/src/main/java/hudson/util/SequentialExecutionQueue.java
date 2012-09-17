package hudson.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

/**
 * {@link Executor} that collapses two equal {@link Runnable}s into one,
 * and makes sure no two equal {@link Runnable}s get executed simultaneously.
 *
 * <p>
 * That is, if a {@link Runnable} is executing and another one gets submitted,
 * the 2nd one waits for the completion of the 1st one.
 *
 * {@link Object#equals(Object)} is used on {@link Runnable} to identify
 * two equal {@link Runnable}s.
 *
 * @author Kohsuke Kawaguchi
 */
public class SequentialExecutionQueue implements Executor {
    /**
     * Access is sycnhronized by {@code Queue.this}
     */
    private final Map<Runnable,QueueEntry> entries = new HashMap<Runnable,QueueEntry>();
    private ExecutorService executors;

    /**
     * {@link Runnable}s that are currently executing. Useful for trouble-shooting.
     */
    private final Set<QueueEntry> inProgress = new HashSet<QueueEntry>();

    public SequentialExecutionQueue(ExecutorService executors) {
        this.executors = executors;
    }

    /**
     * Gets the base underlying executors.,
     */
    public synchronized ExecutorService getExecutors() {
        return executors;
    }

    /**
     * Starts using a new {@link ExecutorService} to carry out executions.
     *
     * <p>
     * The older {@link ExecutorService} will be shut down (but it's still expected to
     * complete whatever they are doing and scheduled.)
     */
    public synchronized void setExecutors(ExecutorService svc) {
        ExecutorService old = this.executors;
        this.executors = svc;
        // gradually executions will be taken over by a new pool
        old.shutdown();
    }


    public synchronized void execute(Runnable item) {
        QueueEntry e = entries.get(item);
        if(e==null) {
            e = new QueueEntry(item);
            entries.put(item,e);
            e.submit();
        } else {
            e.queued = true;
        }
    }

    /**
     * Returns true if too much time is spent since some {@link Runnable} is submitted into the queue
     * until they get executed. 
     */
    public synchronized boolean isStarving(long threshold) {
        long now = System.currentTimeMillis();
        for (QueueEntry e : entries.values())
            if (now-e.submissionTime > threshold)
                return true;
        return false;
    }

    /**
     * Gets {@link Runnable}s that are currently executed by a live thread.
     */
    public synchronized Set<Runnable> getInProgress() {
        Set<Runnable> items = new HashSet<Runnable>();
        for (QueueEntry entry : inProgress) {
            items.add(entry.item);
        }
        return items;
    }

    private final class QueueEntry implements Runnable {
        private final Runnable item;
        private boolean queued;
        private long submissionTime;

        private QueueEntry(Runnable item) {
            this.item = item;
            this.queued = true;
        }

        // Caller must have a lock
        private void submit() {
            submissionTime = System.currentTimeMillis();
            executors.submit(this);
        }

        public void run() {
            try {
                synchronized (SequentialExecutionQueue.this) {
                    assert queued;
                    queued = false;
                    inProgress.add(this);
                }
                item.run();
            } finally {
                synchronized (SequentialExecutionQueue.this) {
                    if(queued)
                        // another polling for this job is requested while we were doing the polling. do it again.
                        submit();
                    else
                        entries.remove(item);
                    inProgress.remove(this);
                }
            }
        }
    }
}
