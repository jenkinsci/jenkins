package hudson.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.FilePath;
import hudson.security.ACL;
import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import jenkins.model.Jenkins;
import jenkins.util.SetContextClassLoader;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.springframework.security.core.Authentication;

/**
 * @author Kohsuke Kawaguchi
 */
class ComputerTest {

    @Test
    void testRelocate() throws Exception {
        File d = File.createTempFile("jenkins", "test");
        FilePath dir = new FilePath(d);
        try {
            dir.delete();
            dir.mkdirs();
            dir.child("slave-abc.log").touch(0);
            dir.child("slave-def.log.5").touch(0);

            Computer.relocateOldLogs(d);

            assertEquals(1, dir.list().size()); // asserting later that this one child is the logs/ directory
            assertTrue(dir.child("logs/slaves/abc/slave.log").exists());
            assertTrue(dir.child("logs/slaves/def/slave.log.5").exists());
        } finally {
            dir.deleteRecursive();
        }
    }

    @Issue("JENKINS-50296")
    @Test
    void testThreadPoolForRemotingActsAsSystemUser() throws InterruptedException, ExecutionException {
        Future<Authentication> job = Computer.threadPoolForRemoting.submit(Jenkins::getAuthentication2);
        assertThat(job.get(), is(ACL.SYSTEM2));
    }

    @Issue("JENKINS-72796")
    @Test
    void testThreadPoolForRemotingContextClassLoaderIsSet() throws Exception {
        // as the threadpool is cached, any other tests here pollute this test so we need enough threads to
        // avoid any cached.
        final int numThreads = 5;

        // simulate the first call to Computer.threadPoolForRemoting with a non default classloader
        try (var ignored = new SetContextClassLoader(new ClassLoader() {})) {
            obtainAndCheckThreadsContextClassloaderAreCorrect(numThreads);
        }
        // now repeat this as the checking that the pollution of the context classloader is handled
        obtainAndCheckThreadsContextClassloaderAreCorrect(numThreads);
    }

    private static void obtainAndCheckThreadsContextClassloaderAreCorrect(int numThreads) throws Exception {
        ArrayList<Future<ClassLoader>> classloaderFuturesList = new ArrayList<>();
        // block all calls to getContextClassloader() so we create more threads.
        synchronized (WaitAndGetContextClassLoader.class) {
            for (int i = 0; i < numThreads; i++) {
                classloaderFuturesList.add(Computer.threadPoolForRemoting.submit(WaitAndGetContextClassLoader::getContextClassloader));
            }
        }
        for (Future<ClassLoader> fc : classloaderFuturesList) {
            assertThat(fc.get(), is(Jenkins.class.getClassLoader()));
        }
    }

    private static class WaitAndGetContextClassLoader {

        public static synchronized ClassLoader getContextClassloader() throws InterruptedException {
            ClassLoader ccl = Thread.currentThread().getContextClassLoader();
            // intentionally pollute the Threads context classloader
            Thread.currentThread().setContextClassLoader(new ClassLoader() {});
            return ccl;
        }
    }
}
