package hudson.remoting;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * {@link Future} that converts the return type.
 *
 * @author Kohsuke Kawaguchi
 */
abstract class FutureAdapter<X,Y> implements Future<X> {
    protected final Future<Y> core;

    protected FutureAdapter(Future<Y> core) {
        this.core = core;
    }

    public boolean cancel(boolean mayInterruptIfRunning) {
        return core.cancel(mayInterruptIfRunning);
    }

    public boolean isCancelled() {
        return core.isCancelled();
    }

    public boolean isDone() {
        return core.isDone();
    }

    public X get() throws InterruptedException, ExecutionException {
        return adapt(core.get());
    }

    public X get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return adapt(core.get(timeout, unit));
    }

    protected abstract X adapt(Y y) throws ExecutionException;
}
