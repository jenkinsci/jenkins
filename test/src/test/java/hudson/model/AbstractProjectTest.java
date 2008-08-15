package hudson.model;

import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.tasks.Shell;
import org.jvnet.hudson.test.HudsonTestCase;

/**
 * @author Kohsuke Kawaguchi
 */
public class AbstractProjectTest extends HudsonTestCase {
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
        WebClient webClient = new WebClient();
        HtmlPage page = webClient.getPage(project);

        page = (HtmlPage)page.getFirstAnchorByText("Workspace").click();
        page = (HtmlPage)page.getFirstAnchorByText("Wipe Out Workspace").click();
        page = (HtmlPage)((HtmlForm)page.getElementById("confirmation")).submit(null);

        assertFalse("Workspace should be gone by now",
                project.getWorkspace().exists());
    }
}
