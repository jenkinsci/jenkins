/*
 * The MIT License
 *
 * Copyright 2022 CloudBees, Inc.
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

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Executor service that logs unchecked exceptions / errors in {@link Runnable}.
 * Exceptions thrown from {@link Callable} are <em>not</em> not logged,
 * under the assumption that something is checking {@link Future#get()}.
 * @since 2.380
 */
public class ErrorLoggingExecutorService extends InterceptingExecutorService {

    private static final Logger LOGGER = Logger.getLogger(ErrorLoggingExecutorService.class.getName());

    public ErrorLoggingExecutorService(ExecutorService base) {
        super(base);
    }

    @Override
    protected Runnable wrap(Runnable r) {
        return () -> {
            try {
                r.run();
            } catch (Throwable x) {
                LOGGER.log(Level.WARNING, null, x);
                throw x;
            }
        };
    }

    @Override
    protected <V> Callable<V> wrap(Callable<V> r) {
        return r;
    }

}
