package jenkins.util;

import hudson.remoting.AtmostOneThreadExecutor;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * {@link Executor}-like class that executes a single task repeatedly, in such a way that a single execution
 * can cover multiple pending queued requests.
 *
 * <p>
 * This is akin to doing laundry &mdash; when you put a dirty cloth into the laundry box, you mentally "schedule"
 * a laundry task, regardless of whether there already is some cloths in the box or not. When you later actually get around
 * doing laundry, you wash all the dirty cloths in the box, not just your cloths. And if someone brings
 * more dirty cloths while a washer and dryer are in operation, the person has to mentally "schedule" the task
 * and run the machines another time later, as the current batch is already in progress.
 *
 * <p>
 * Since this class collapses multiple submitted tasks into just one run, it only makes sense when everyone
 * submits the same task. Thus {@link #submit()} method does not take {@link Callable} as a parameter,
 * instead you pass that in the constructor.
 *
 * @author Kohsuke Kawaguchi
 * @see AtmostOneThreadExecutor
 */
public class AtmostOneTaskExecutor<V> {
    /**
     * The actual executor that executes {@link #task}
     */
    private final ExecutorService base;

    /**
     * Task to be executed.
     */
    private final Callable<V> task;

    /**
     * If a task is already submitted and pending execution, non-null.
     * Guarded by "synchronized(this)"
     */
    private Future<V> pending;

    public AtmostOneTaskExecutor(ExecutorService base, Callable<V> task) {
        this.base = base;
        this.task = task;
    }

    public AtmostOneTaskExecutor(Callable<V> task) {
        this(new AtmostOneThreadExecutor(),task);
    }

    public synchronized Future<V> submit() {
        if (pending!=null)
            // if a task is already pending, just join that
            return pending;

        pending = base.submit(new Callable<V>() {
            @Override
            public V call() throws Exception {
                // before we get going, everyone who submits after this
                // should form a next batch
                synchronized (AtmostOneTaskExecutor.this) {
                    pending = null;
                }

                return task.call();
            }
        });
        return pending;
    }
}
