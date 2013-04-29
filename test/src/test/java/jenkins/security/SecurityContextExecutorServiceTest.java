package jenkins.security;

import static org.junit.Assert.assertEquals;

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
    private int NUM_THREADS = 10;
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
        
        Future<Object> result = SecurityContextExecutorService
                .wrapExecutorWithSecurityContext(service).submit(
                        new Callable<Object>() {
                            public Object call() throws Exception {
                                return SecurityContextHolder.getContext();
                            }
                        });
        SecurityContext threadContext = (SecurityContext) result.get();
        // Assert the thread context was identical to the calling context
        assertEquals(initialContext, threadContext);
        // Assert the calling context was not modified
        assertEquals(initialContext, SecurityContextHolder.getContext());
    }
}
