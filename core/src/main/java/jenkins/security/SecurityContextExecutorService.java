/*
 * The MIT License
 *
 * Copyright (c) 2011, CloudBees, Inc.
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
package jenkins.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextHolder;

/**
 * Creates a delegating ExecutorService implementation whose submit and related
 * methods capture the current SecurityContext and then wrap any runnable/callable objects
 * in another runnable/callable that sets the context before execution and resets it afterwards.
 * 
 * 
 * @author Patrick McKeown
 * @since 1.512
 */
public class SecurityContextExecutorService implements ExecutorService {
    private final ExecutorService service;

    public SecurityContextExecutorService(ExecutorService service) {
        this.service = service;
    }

    public static SecurityContextExecutorService wrapExecutorWithSecurityContext(
            ExecutorService service) {
        return new SecurityContextExecutorService(service);
    }

    private static Runnable wrapRunnableWithSecurityContext(final Runnable r) {
        final SecurityContext callingContext = SecurityContextHolder
                .getContext();
        return new Runnable() {
            public void run() {
                SecurityContext originalExecutorContext = SecurityContextHolder
                        .getContext();
                SecurityContextHolder.setContext(callingContext);
                try {
                    r.run();
                } finally {
                    SecurityContextHolder.setContext(originalExecutorContext);
                }
            }
        };
    }

    private static <T> Callable<T> wrapCallableWithSecurityContext(
            final Callable<T> c) {
        final SecurityContext callingContext = SecurityContextHolder
                .getContext();
        return new Callable<T>() {
            public T call() throws Exception {
                SecurityContext originalExecutorContext = SecurityContextHolder
                        .getContext();
                SecurityContextHolder.setContext(callingContext);
                T result = null;
                try {
                    result = c.call();
                } finally {
                    SecurityContextHolder.setContext(originalExecutorContext);
                }
                return result;
            }
        };
    }

    private static <T> ArrayList<Callable<T>> wrapCallableCollectionWithSecurityContext(
            final Collection<? extends Callable<T>> tasks) {
        final SecurityContext callingContext = SecurityContextHolder
                .getContext();
        ArrayList<Callable<T>> wrappedTasks = new ArrayList<Callable<T>>();
        for (final Callable<T> task : tasks) {
            wrappedTasks.add(new Callable<T>() {
                public T call() throws Exception {
                    SecurityContext originalExecutorContext = SecurityContextHolder
                            .getContext();
                    SecurityContextHolder.setContext(callingContext);
                    T result = null;
                    try {
                        result = task.call();
                    } finally {
                        SecurityContextHolder.setContext(originalExecutorContext);
                    }
                    return result;
                }
            });
        }
        return wrappedTasks;
    }

    public void execute(Runnable arg0) {
        service.execute(wrapRunnableWithSecurityContext(arg0));
    }

    public boolean awaitTermination(long timeout, TimeUnit unit)
            throws InterruptedException {
        return service.awaitTermination(timeout, unit);
    }

    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
            throws InterruptedException {
        return service.invokeAll(wrapCallableCollectionWithSecurityContext(tasks));
    }

    public <T> List<Future<T>> invokeAll(
            Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException {
        return service.invokeAll(wrapCallableCollectionWithSecurityContext(tasks), timeout, unit);
    }

    public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
            throws InterruptedException, ExecutionException {
        return service.invokeAny(wrapCallableCollectionWithSecurityContext(tasks));
    }

    public <T> T invokeAny(Collection<? extends Callable<T>> tasks,
            long timeout, TimeUnit unit) throws InterruptedException,
            ExecutionException, TimeoutException {
        return service.invokeAny(wrapCallableCollectionWithSecurityContext(tasks), timeout, unit);
    }

    public boolean isShutdown() {
        return service.isShutdown();
    }

    public boolean isTerminated() {
        return service.isTerminated();
    }

    public void shutdown() {
        service.shutdown();
    }

    public List<Runnable> shutdownNow() {
        return service.shutdownNow();
    }

    public <T> Future<T> submit(Callable<T> task) {
        return service.submit(wrapCallableWithSecurityContext(task));
    }

    public Future<?> submit(Runnable task) {
        return service.submit(wrapRunnableWithSecurityContext(task));
    }

    public <T> Future<T> submit(Runnable task, T result) {
        return service.submit(wrapRunnableWithSecurityContext(task), result);
    }
}
