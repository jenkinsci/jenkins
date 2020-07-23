package hudson.model;

import hudson.FilePath;
import hudson.security.ACL;
import jenkins.model.Jenkins;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;

import java.io.File;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import org.springframework.security.core.Authentication;

/**
 * @author Kohsuke Kawaguchi
 */
public class ComputerTest {
    @Test
    public void testRelocate() throws Exception {
        File d = File.createTempFile("jenkins", "test");
        FilePath dir = new FilePath(d);
        try {
            dir.delete();
            dir.mkdirs();
            dir.child("slave-abc.log").touch(0);
            dir.child("slave-def.log.5").touch(0);

            Computer.relocateOldLogs(d);

            assert dir.list().size()==1; // asserting later that this one child is the logs/ directory
            assert dir.child("logs/slaves/abc/slave.log").exists();
            assert dir.child("logs/slaves/def/slave.log.5").exists();
        } finally {
            dir.deleteRecursive();
        }
    }

    @Issue("JENKINS-50296")
    @Test
    public void testThreadPoolForRemotingActsAsSystemUser() throws InterruptedException, ExecutionException {
        Future<Authentication> job = Computer.threadPoolForRemoting.submit(Jenkins::getAuthentication2);
        assertThat(job.get(), is(ACL.SYSTEM2));
    }
}
