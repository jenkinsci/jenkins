/*
 * The MIT License
 * 
 * Copyright (c) 2004-2012, Sun Microsystems, Inc., Kohsuke Kawaguchi, Erik Ramfelt,
 * Vincent Latombe
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

import static org.junit.Assert.assertEquals;

import java.util.Collection;
import java.util.LinkedList;
import java.util.concurrent.Callable;
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
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    @PresetData(PresetData.DataSet.NO_ANONYMOUS_READACCESS)
    public void testExecutorServiceWithSecurity() throws Exception {
        ScheduledThreadPoolExecutor service = new ScheduledThreadPoolExecutor(
                NUM_THREADS);
        final SecurityContext initialContext = SecurityContextHolder
                .getContext();

        SecurityContextExecutorService.wrapExecutorWithSecurityContext(service)
        .execute(new Runnable() {
            public void run() {
                SecurityContext threadContext = SecurityContextHolder
                        .getContext();
                // Assert the thread context is identical to the calling
                // context
                assertEquals(initialContext, threadContext);
            }
        });
        
        Callable<Object> c = new Callable<Object>() {
            public Object call() throws Exception {
                return SecurityContextHolder.getContext();
            }
        };
        
        Collection<Callable<Object>> callables = new LinkedList<Callable<Object>>();
        callables.add(c);
        callables.add(c);
        callables.add(c);
        Collection<Future<Object>> results = SecurityContextExecutorService
                .wrapExecutorWithSecurityContext(service).invokeAll(callables);
        for (Future<Object> result : results){
            SecurityContext threadContext = (SecurityContext) result.get();
            // Assert each thread context was identical to the calling context
            assertEquals(initialContext, threadContext);
        }
        
        Future<Object> result = SecurityContextExecutorService
                .wrapExecutorWithSecurityContext(service).submit(c);
        SecurityContext threadContext = (SecurityContext) result.get();
        // Assert the thread context was identical to the calling context
        assertEquals(initialContext, threadContext);
        
        // Assert the calling context was not modified
        assertEquals(initialContext, SecurityContextHolder.getContext());
    }
}
