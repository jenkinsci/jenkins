/*
 * The MIT License
 *
 * Copyright (c) 2015 Red Hat, Inc.
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
package jenkins.model;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import jenkins.security.SlaveToMasterCallable;
import hudson.Extension;
import hudson.model.AdministrativeMonitor;
import hudson.util.DaemonThreadFactory;
import hudson.util.ExceptionCatchingThreadFactory;
import hudson.util.NamingThreadFactory;

/**
 * @author ogondza
 */
@Extension
public class AsyncResourceDisposer extends AdministrativeMonitor {

    private static final ExecutorService disposer = new ThreadPoolExecutor (
            0, 1, 5L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(),
            new ExceptionCatchingThreadFactory(new NamingThreadFactory(new DaemonThreadFactory(), "AsyncResourceDisposer"))
    );

    /**
     * Persist all entries to dispose in order to survive restart.
     */
    private HashMap</*@Nonnull*/Disposable, /*@Nullable*/Exception> toDispose = new HashMap<Disposable, Exception>();

    public AsyncResourceDisposer() {
        this(5 * 60 * 1000);
    }

    /*package*/ AsyncResourceDisposer(int interval) {
        super("AsyncResourceDisposer");
        new Maintainer(interval).start();
    }

    @Override
    public boolean isActivated() {
        return true;
        //return !getProblems().isEmpty();
    }

    /**
     * Schedule resource to be disposed.
     */
    public void dispose(final Disposable disposable) {
        disposer.submit(new Runnable() {
            public void run() {
                try {
                    boolean disposed = disposable.dispose();
                    if (disposed) {
                        // Remove failure
                        synchronized (toDispose) {
                            toDispose.remove(disposable);
                        }
                    } else {
                        // Reschedule
                        disposer.submit(this);
                    }
                } catch (Exception ex) {
                    // Track the failure to retry later
                    synchronized (toDispose) {
                        toDispose.put(disposable, ex);
                    }
                }
            }
        });
    }

    /**
     * Get map of failed {@link Disposable} and an {@link Exception} from last attempt.
     */
    public Map<Disposable, Exception> getProblems() {
        HashMap<Disposable, Exception> problems;
        synchronized (toDispose) {
            problems = new HashMap<Disposable, Exception>(toDispose);
        }

        for (Entry<Disposable, Exception> f: problems.entrySet()) {
            if (f.getValue() == null) {
                problems.remove(f.getKey());
            }
        }
        return problems;
    }

    /**
     * Reschedule failed attempts.
     *
     * @author ogondza
     */
    private class Maintainer extends Thread {
        private final long interval;
        private Maintainer(long interval) {
            super("AsyncResourceDisposer.Maintainer");
            setDaemon(true);
            this.interval = interval;
        }

        @Override
        public void run() {
            while (!this.isInterrupted()) {
                for (Entry<Disposable, Exception> f: getProblems().entrySet()) {
                    dispose(f.getKey());
                }

                try {
                    Thread.sleep(interval);
                } catch (InterruptedException ex) {
                    throw new AssertionError(ex);
                }
            }
        }
    }

    /**
     * @author ogondza
     */
    public interface Disposable extends Serializable {
        /**
         * Dispose the resource.
         *
         * Implementation should deal gracefully (return <tt>true</tt>) in
         * case the resource get disposed externally. This is expected in case
         * resource get disposed by administrator after failed attempts was reported.
         *
         * @return true if successful, false if operation needs to be retried later.
         * @throws Exception Problem disposing the resource.
         */
        public abstract boolean dispose() throws Exception;

        /**
         * Text description of the disposable.
         */
        public abstract String getDisplayName();

        public static final class Register extends SlaveToMasterCallable<Boolean, RuntimeException> {
            private static final long serialVersionUID = 1L;

            private final Disposable disposable;

            public Register(Disposable disposable) {
                this.disposable = disposable;
            }

            /**
             * Register disposable to {@link AsyncResourceDisposer} over channel from slave.
             *
             * @return false if failed to register because we are not talking to master.
             */
            public final Boolean call() throws RuntimeException {
                if (Jenkins.getInstance() == null) return false;

                AsyncResourceDisposer disposer = AdministrativeMonitor.all().get(AsyncResourceDisposer.class);
                disposer.dispose(disposable);
                return true;
            }
        }
    }
}
