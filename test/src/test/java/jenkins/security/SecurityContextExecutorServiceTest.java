/*
 * The MIT License
 *
 * Copyright (c) 2013, Patrick McKeown
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

import hudson.model.User;
import hudson.security.ACL;
import static org.junit.Assert.assertEquals;

import java.util.Collection;
import java.util.LinkedList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextHolder;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.PresetData;

/**
 * @author Patrick McKeown
 */
public class SecurityContextExecutorServiceTest {
    final private int NUM_THREADS = 10;
    final private int TIME_OUT = 1;
    private ExecutorService wrappedService = null;
    private SecurityContext systemContext = null;
    private SecurityContext userContext = null;
    private SecurityContext nullContext = null;
    private volatile SecurityContext runnableThreadContext;
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    @PresetData(PresetData.DataSet.NO_ANONYMOUS_READACCESS)
    public void testSecurityContextExecutorService() throws Exception {
        ScheduledThreadPoolExecutor service = new ScheduledThreadPoolExecutor(NUM_THREADS);
        // Create a system level context with ACL.SYSTEM
        systemContext = ACL.impersonate(ACL.SYSTEM);

        User u = User.get("bob");
        // Create a sample user context
        ACL.impersonate(u.impersonate());
        // TODO Figure out the proper way to get that user's context
        userContext = ACL.impersonate(ACL.SYSTEM);

        // Create a null context
        SecurityContextHolder.clearContext();
        nullContext = SecurityContextHolder.getContext();

        // Create a service with the system context
        SecurityContextHolder.setContext(systemContext);
        wrappedService = SecurityContextExecutorService.wrapExecutorWithSecurityContext(service);

        testRunnableAgainstAllContexts();

        testCallableAgainstAllContexts();

        testCallableCollectionAgainstAllContexts();

        testFailedRunnableResetsContext();
    }

    private void testRunnableAgainstAllContexts() throws Exception {
        Runnable r = new Runnable() {
            public void run() {
                runnableThreadContext = SecurityContextHolder.getContext();
            }
        };
        SecurityContextHolder.setContext(systemContext);
        wrappedService.execute(r);
        wrappedService.awaitTermination(TIME_OUT, TimeUnit.SECONDS);
        // Assert the context inside the runnable thread was set to ACL.SYSTEM
        assertEquals(systemContext, runnableThreadContext);

        SecurityContextHolder.setContext(userContext);
        wrappedService.execute(r);
        wrappedService.awaitTermination(TIME_OUT, TimeUnit.SECONDS);
        // Assert the context inside the runnable thread was set to the user's context
        assertEquals(userContext, runnableThreadContext);

        SecurityContextHolder.setContext(nullContext);
        wrappedService.execute(r);
        wrappedService.awaitTermination(TIME_OUT, TimeUnit.SECONDS);
        // Assert the context inside the runnable thread was set to null
        assertEquals(nullContext, runnableThreadContext);
    }

    private void testCallableAgainstAllContexts() throws Exception {
        Callable<SecurityContext> c = new Callable<SecurityContext>() {
            public SecurityContext call() throws Exception {
                return SecurityContextHolder.getContext();
            }
        };
        SecurityContextHolder.setContext(systemContext);
        Future<SecurityContext> result = wrappedService.submit(c);
        // Assert the context inside the callable thread was set to ACL.SYSTEM
        assertEquals(systemContext, result.get());

        SecurityContextHolder.setContext(userContext);
        result = wrappedService.submit(c);
        // Assert the context inside the callable thread was set to the user's context
        assertEquals(userContext, result.get());

        SecurityContextHolder.setContext(nullContext);
        result = wrappedService.submit(c);
        // Assert the context inside the callable thread was set to the user's context
        assertEquals(nullContext, result.get());
    }

    private void testCallableCollectionAgainstAllContexts() throws Exception {
        Collection<Callable<SecurityContext>> callables = new LinkedList<Callable<SecurityContext>>();
        Callable<SecurityContext> c = new Callable<SecurityContext>() {
            public SecurityContext call() throws Exception {
                return SecurityContextHolder.getContext();
            }
        };
        callables.add(c);
        callables.add(c);
        callables.add(c);

        SecurityContextHolder.setContext(systemContext);
        Collection<Future<SecurityContext>> results = wrappedService.invokeAll(callables);
        for (Future<SecurityContext> result : results) {
            // Assert each thread context was identical to the initial service context
            SecurityContext value = result.get();
            System.err.println(value);
            assertEquals(systemContext, value);
        }

        SecurityContextHolder.setContext(userContext);
        results = wrappedService.invokeAll(callables);
        for (Future<SecurityContext> result : results) {
            // Assert each thread context was identical to the initial service context
            assertEquals(userContext, result.get());
        }

        SecurityContextHolder.setContext(nullContext);
        results = wrappedService.invokeAll(callables);
        for (Future<SecurityContext> result : results) {
            // Assert each thread context was identical to the initial service context
            assertEquals(nullContext, result.get());
        }
    }

    private void testFailedRunnableResetsContext() throws Exception {
        Runnable r = new Runnable() {
            public void run() {
                SecurityContextHolder.setContext(nullContext);
                assert (false);
            }
        };
        SecurityContextHolder.setContext(systemContext);
        wrappedService.execute(r);
        wrappedService.awaitTermination(TIME_OUT, TimeUnit.SECONDS);
        // Assert the current context is once again the systemContext
        assertEquals(systemContext, SecurityContextHolder.getContext());

        SecurityContextHolder.setContext(userContext);
        wrappedService.execute(r);
        wrappedService.awaitTermination(TIME_OUT, TimeUnit.SECONDS);
        // Assert the current context is once again the systemContext
        assertEquals(userContext, SecurityContextHolder.getContext());
    }
}
