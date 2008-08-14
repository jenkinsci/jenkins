package hudson.model;

import hudson.tasks.Shell;
import org.jvnet.hudson.test.HudsonTestCase;
import com.meterware.httpunit.WebResponse;

/**
 * @author Kohsuke Kawaguchi
 */
public class AbstractProjectTest extends HudsonTestCase {
    public AbstractProjectTest(String name) {
        super(name);
    }

    /**
     * Tests the workspace deletion.
     */
    public void testWipeWorkspace() throws Exception {
        FreeStyleProject project = createFreeStyleProject();
        project.getBuildersList().add(new Shell("echo hello"));

        project.scheduleBuild2(0).get();

        assertTrue("Workspace should exist by now",
                project.getWorkspace().exists());

        // emulate the user behavior
        WebResponse rsp = new WebConversation().getResponse(project);
        rsp = rsp.getLinkWith("Workspace").click();
        rsp = rsp.getLinkWith("Wipe Out Workspace").click();
        rsp = rsp.getFormWithID("confirmation").submit();

        assertFalse("Workspace should be gone by now",
                project.getWorkspace().exists());
    }
}
