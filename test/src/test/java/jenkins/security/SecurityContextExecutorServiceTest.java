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
