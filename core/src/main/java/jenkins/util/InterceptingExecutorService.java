package jenkins.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * {@link ExecutorService} that wraps all the tasks that run inside.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.557
 */
public abstract class InterceptingExecutorService implements ExecutorService {
    private final ExecutorService base;

    public InterceptingExecutorService(ExecutorService base) {
        this.base = base;
    }

    protected abstract Runnable wrap(Runnable r);

    protected abstract <V> Callable<V> wrap(Callable<V> r);

    protected ExecutorService delegate() {
        return base;
    }

    public <T> Future<T> submit(Callable<T> task) {
        return delegate().submit(wrap(task));
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        return delegate().submit(wrap(task), result);
    }

    @Override
    public Future<?> submit(Runnable task) {
        return delegate().submit(wrap(task));
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        return delegate().invokeAll(wrap(tasks));
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
        return delegate().invokeAll(wrap(tasks), timeout, unit);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        return delegate().invokeAny(wrap(tasks));
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return delegate().invokeAny(wrap(tasks), timeout, unit);
    }

    @Override
    public void execute(Runnable command) {
        delegate().execute(wrap(command));
    }


    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return delegate().awaitTermination(timeout, unit);
    }

    @Override
    public boolean isShutdown() {
        return delegate().isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return delegate().isTerminated();
    }

    @Override
    public void shutdown() {
        delegate().shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        return delegate().shutdownNow();
    }

    @Override
    public String toString() {
        return delegate().toString();
    }

    private <T> Collection<Callable<T>> wrap(Collection<? extends Callable<T>> callables) {
        List<Callable<T>> r = new ArrayList<>();
        for (Callable<T> c : callables) {
            r.add(wrap(c));
        }
        return r;
    }
}
