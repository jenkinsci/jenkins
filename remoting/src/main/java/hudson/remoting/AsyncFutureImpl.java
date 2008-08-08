package hudson.remoting;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * {@link Future} implementation whose computation is carried out elsewhere.
 *
 * Call the {@link #set(Object)} method or {@link #set(Throwable)} method to set the value to the future.
 * 
 * @author Kohsuke Kawaguchi
 */
public class AsyncFutureImpl<V> implements Future<V> {
    private V value;
    private Throwable problem;
    private boolean completed;

    /**
     * Not cancellable.
     */
    public boolean cancel(boolean mayInterruptIfRunning) {
        return false;
    }

    public boolean isCancelled() {
        return false;
    }

    public synchronized boolean isDone() {
        return completed;
    }

    public synchronized V get() throws InterruptedException, ExecutionException {
        while(!completed)
            wait();
        if(problem!=null)
            throw new ExecutionException(problem);
        return value;
    }

    public synchronized V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        if(!completed)
            wait(unit.toMillis(timeout));
        if(!completed)
            throw new TimeoutException();
        return get();
    }

    public synchronized void set(V value) {
        completed = true;
        this.value = value;
        notifyAll();
    }

    public synchronized void set(Throwable problem) {
        completed = true;
        this.problem = problem;
        notifyAll();
    }
}
