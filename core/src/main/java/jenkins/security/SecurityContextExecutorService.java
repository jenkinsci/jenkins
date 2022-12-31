/*
 * The MIT License
 *
 * Copyright (c) 2014-, Patrick McKeown, CloudBees, Inc.
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

import static org.springframework.security.core.context.SecurityContextHolder.getContext;
import static org.springframework.security.core.context.SecurityContextHolder.setContext;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import jenkins.util.InterceptingExecutorService;
import org.springframework.security.core.context.SecurityContext;

/**
 * Creates a delegating {@link ExecutorService}
 * implementation whose submit and related methods capture the current
 * SecurityContext and then wrap any runnable/callable objects in another
 * runnable/callable that sets the context before execution and resets it
 * afterwards.
 *
 * @author Patrick McKeown
 * @author Kohsuke Kawaguchi
 * @since 1.561
 */
public class SecurityContextExecutorService extends InterceptingExecutorService {

    public SecurityContextExecutorService(ExecutorService service) {
        super(service);
    }

    @Override
    protected Runnable wrap(final Runnable r) {
        final SecurityContext callingContext = getContext();
        return new Runnable() {
            @Override
            public void run() {
                SecurityContext old = getContext();
                setContext(callingContext);
                try {
                    r.run();
                } finally {
                    setContext(old);
                }
            }
        };
    }

    @Override
    protected <V> Callable<V> wrap(final Callable<V> c) {
        final SecurityContext callingContext = getContext();
        return new Callable<>() {
            @Override
            public V call() throws Exception {
                SecurityContext old = getContext();
                setContext(callingContext);
                try {
                    return c.call();
                } finally {
                    setContext(old);
                }
            }
        };
    }
}
