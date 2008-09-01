package hudson.scm;

import hudson.FilePath;
import hudson.model.AbstractProject;
import hudson.model.FreeStyleProject;
import hudson.model.Node;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.HudsonTestCase;

/**
 * @author Kohsuke Kawaguchi
 */
public class ScmTest extends HudsonTestCase {
    /**
     * Makes sure that {@link SCM#processWorkspaceBeforeDeletion(AbstractProject, FilePath, Node)} is called
     * before a project deletion.
     */
    @Bug(2271)
    public void testProjectDeletionAndCallback() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        final boolean[] callback = new boolean[1];
        p.setScm(new NullSCM() {
            public boolean processWorkspaceBeforeDeletion(AbstractProject<?, ?> project, FilePath workspace, Node node) {
                callback[0] = true;
                return true;
            }
        });
        p.scheduleBuild2(0).get();
        p.delete();
        assertTrue(callback[0]);
    }
}
