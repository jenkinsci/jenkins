package hudson;

import static org.junit.Assert.assertTrue;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.util.StreamTaskListener;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * @author Kohsuke Kawaguchi
 */
public class FileSystemProvisionerTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    @Issue("JENKINS-13165")
    public void test() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        FreeStyleBuild b = j.assertBuildStatusSuccess(p.scheduleBuild2(0));
        b.getWorkspace().child(".dot").touch(0);
        StreamTaskListener listener = StreamTaskListener.fromStdout();

        WorkspaceSnapshot s = j.jenkins.getFileSystemProvisioner().snapshot(b, b.getWorkspace(), "**/*", listener);
        FilePath dst = new FilePath(tmp.getRoot());
        s.restoreTo(b, dst, listener);
        assertTrue(dst.child(".dot").exists());
    }
}
