package hudson;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.util.StreamTaskListener;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.HudsonTestCase;

/**
 * @author Kohsuke Kawaguchi
 */
public class FileSystemProvisionerTest extends HudsonTestCase {
    @Bug(13165)
    public void test13165() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        FreeStyleBuild b = assertBuildStatusSuccess(p.scheduleBuild2(0));
        b.getWorkspace().child(".dot").touch(0);
        StreamTaskListener listener = StreamTaskListener.fromStdout();

        WorkspaceSnapshot s = jenkins.getFileSystemProvisioner().snapshot(b, b.getWorkspace(), "**/*", listener);
        FilePath tmp = new FilePath(createTmpDir());
        s.restoreTo(b, tmp,listener);
        assertTrue(tmp.child(".dot").exists());
    }
}
