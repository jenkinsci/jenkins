/*
 * The MIT License
 *
 * Copyright 2015 Jesse Glick.
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

package jenkins.util;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Executor service that logs otherwise uncaught errors.
 * TODO is there anything in Guava for this?
 */
class ErrorLoggingScheduledThreadPoolExecutor extends ScheduledThreadPoolExecutor {

    private static final Logger LOGGER = Logger.getLogger(ErrorLoggingScheduledThreadPoolExecutor.class.getName());

    ErrorLoggingScheduledThreadPoolExecutor(int corePoolSize) {
        super(corePoolSize);
    }

    ErrorLoggingScheduledThreadPoolExecutor(int corePoolSize, ThreadFactory threadFactory) {
        super(corePoolSize, threadFactory);
    }

    ErrorLoggingScheduledThreadPoolExecutor(int corePoolSize, RejectedExecutionHandler handler) {
        super(corePoolSize, handler);
    }
    
    ErrorLoggingScheduledThreadPoolExecutor(int corePoolSize, ThreadFactory threadFactory, RejectedExecutionHandler handler) {
        super(corePoolSize, threadFactory, handler);
    }
    
    @Override protected void afterExecute(Runnable r, Throwable t) {
        super.afterExecute(r, t);
        if (t == null && r instanceof Future<?>) {
            Future<?> f = (Future<?>) r;
            if (f.isDone()) { // TODO super Javadoc does not suggest this, but without it, we hang in FutureTask.awaitDone!
                try {
                    f.get(/* just to be on the safe side, do not wait */0, TimeUnit.NANOSECONDS);
                } catch (TimeoutException x) {
                    // should not happen, right?
                } catch (CancellationException x) {
                    // probably best to ignore this
                } catch (ExecutionException x) {
                    t = x.getCause();
                } catch (InterruptedException x) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        if (t != null) {
            LOGGER.log(Level.WARNING, "failure in task not wrapped in SafeTimerTask", t);
        }
    }

}
