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
        SecurityContext initialContext = SecurityContextHolder.getContext();

        Future<Object> result = SecurityContextExecutorService
                .wrapExecutorWithSecurityContext(service).submit(
                        new Callable<Object>() {
                            public Object call() throws Exception {
                                return SecurityContextHolder.getContext();
                            }
                        });
        SecurityContext threadContext = (SecurityContext) result.get();
        assertEquals(initialContext, threadContext);
    }
}
