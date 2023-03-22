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

package hudson.util;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link ThreadFactory} that creates a thread, which in turn displays a stack trace
 * when it terminates unexpectedly.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.226
 * @see jenkins.util.ErrorLoggingExecutorService
 */
public class ExceptionCatchingThreadFactory implements ThreadFactory, Thread.UncaughtExceptionHandler {
    private final ThreadFactory core;

    public ExceptionCatchingThreadFactory() {
        this(Executors.defaultThreadFactory());
    }

    public ExceptionCatchingThreadFactory(ThreadFactory core) {
        this.core = core;
    }

    @Override
    public Thread newThread(Runnable r) {
        Thread t = core.newThread(r);
        t.setUncaughtExceptionHandler(this);
        return t;
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        LOGGER.log(Level.WARNING, "Thread " + t.getName() + " terminated unexpectedly", e);
    }

    private static final Logger LOGGER = Logger.getLogger(ExceptionCatchingThreadFactory.class.getName());
}
