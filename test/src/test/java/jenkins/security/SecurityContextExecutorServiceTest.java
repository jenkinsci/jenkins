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
import static org.junit.Assert.assertNull;

import java.util.Collection;
import java.util.LinkedList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;

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
    private ExecutorService wrappedService = null;
    private SecurityContext systemContext = null;
    private SecurityContext userContext = null;
    private SecurityContext nullContext = null;
    private volatile SecurityContext runnableThreadContext;
    @Rule
    public JenkinsRule j = new JenkinsRule() {
        public void before() throws Throwable {
            setPluginManager(null);
            super.before();

            ScheduledThreadPoolExecutor service = new ScheduledThreadPoolExecutor(NUM_THREADS);
            // Create a system level context with ACL.SYSTEM
            systemContext = ACL.impersonate(ACL.SYSTEM);

            User u = User.get("bob");
            // Create a sample user context
            userContext = new NonSerializableSecurityContext(u.impersonate());

            // Create a null context
            SecurityContextHolder.clearContext();
            nullContext = SecurityContextHolder.getContext();

            // Create a wrapped service 
            wrappedService = new SecurityContextExecutorService(service);
        }
    };

    @Test
    @PresetData(PresetData.DataSet.NO_ANONYMOUS_READACCESS)
    public void testRunnableAgainstAllContexts() throws Exception {
        Runnable r = new Runnable() {
            public void run() {
                runnableThreadContext = SecurityContextHolder.getContext();
            }
        };
        SecurityContextHolder.setContext(systemContext);
        Future systemResult = wrappedService.submit(r);
        // Assert the runnable completed successfully
        assertNull(systemResult.get());
        // Assert the context inside the runnable thread was set to ACL.SYSTEM
        assertEquals(systemContext, runnableThreadContext);

        SecurityContextHolder.setContext(userContext);
        Future userResult = wrappedService.submit(r);
        // Assert the runnable completed successfully
        assertNull(userResult.get());
        // Assert the context inside the runnable thread was set to the user's context
        assertEquals(userContext, runnableThreadContext);

        SecurityContextHolder.setContext(nullContext);
        Future nullResult = wrappedService.submit(r);
        // Assert the runnable completed successfully
        assertNull(nullResult.get());
        // Assert the context inside the runnable thread was set to the null context
        assertEquals(nullContext, runnableThreadContext);
    }

    @Test
    @PresetData(PresetData.DataSet.NO_ANONYMOUS_READACCESS)
    public void testCallableAgainstAllContexts() throws Exception {
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
        // Assert the context inside the callable thread was set to the null context
        assertEquals(nullContext, result.get());
    }

    @Test
    @PresetData(PresetData.DataSet.NO_ANONYMOUS_READACCESS)
    public void testCallableCollectionAgainstAllContexts() throws Exception {
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

    @Test
    @PresetData(PresetData.DataSet.NO_ANONYMOUS_READACCESS)
    public void testFailedRunnableResetsContext() throws Exception {
        Runnable r = new Runnable() {
            public void run() {
                SecurityContextHolder.setContext(nullContext);
                throw new RuntimeException("Simulate a failure");
            }
        };

        SecurityContextHolder.setContext(systemContext);
        try {
            wrappedService.execute(r);
        } catch (AssertionError expectedException) {
            // Assert the current context is once again ACL.SYSTEM
            assertEquals(systemContext, SecurityContextHolder.getContext());
        }

        SecurityContextHolder.setContext(userContext);
        try {
            wrappedService.execute(r);
        } catch (AssertionError expectedException) {
            // Assert the current context is once again the userContext
            assertEquals(userContext, SecurityContextHolder.getContext());
        }
    }
}
