/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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
package hudson.remoting;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * {@link VirtualChannel} that performs computation on the local JVM.
 * 
 * @author Kohsuke Kawaguchi
 */
public class LocalChannel implements VirtualChannel {
    private final ExecutorService executor;

    public LocalChannel(ExecutorService executor) {
        this.executor = executor;
    }

    public <V, T extends Throwable> V call(Callable<V,T> callable) throws T {
        return callable.call();
    }

    public <V, T extends Throwable> Future<V> callAsync(final Callable<V,T> callable) throws IOException {
        final java.util.concurrent.Future<V> f = executor.submit(new java.util.concurrent.Callable<V>() {
            public V call() throws Exception {
                try {
                    return callable.call();
                } catch (Exception t) {
                    throw t;
                } catch (Error t) {
                    throw t;
                } catch (Throwable t) {
                    throw new ExecutionException(t);
                }
            }
        });

        return new Future<V>() {
            public boolean cancel(boolean mayInterruptIfRunning) {
                return f.cancel(mayInterruptIfRunning);
            }

            public boolean isCancelled() {
                return f.isCancelled();
            }

            public boolean isDone() {
                return f.isDone();
            }

            public V get() throws InterruptedException, ExecutionException {
                return f.get();
            }

            public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
                return f.get(timeout,unit);
            }
        };
    }

    public void close() {
        // noop
    }

    public void join() throws InterruptedException {
        // noop
    }

    public void join(long timeout) throws InterruptedException {
        // noop
    }

    public <T> T export(Class<T> intf, T instance) {
        return instance;
    }
}
