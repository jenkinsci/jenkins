package hudson.util;

import hudson.remoting.Future;

import java.util.concurrent.TimeUnit;

/**
 * Various {@link Future} implementations.
 * 
 * @author Kohsuke Kawaguchi
 */
public class Futures {
    /**
     * Creates a {@link Future} instance that already has its value pre-computed.
     */
    public static <T> Future<T> precomputed(final T value) {
        return new Future<T>() {
            public boolean cancel(boolean mayInterruptIfRunning) {
                return false;
            }

            public boolean isCancelled() {
                return false;
            }

            public boolean isDone() {
                return true;
            }

            public T get() {
                return value;
            }

            public T get(long timeout, TimeUnit unit) {
                return value;
            }
        };
    }
}
