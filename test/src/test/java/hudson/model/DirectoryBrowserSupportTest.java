package hudson.model;

import hudson.tasks.Shell;
import org.jvnet.hudson.test.Email;
import org.jvnet.hudson.test.HudsonTestCase;

/**
 * @author Kohsuke Kawaguchi
 */
public class DirectoryBrowserSupportTest extends HudsonTestCase {
    @Email("http://www.nabble.com/Status-Code-400-viewing-or-downloading-artifact-whose-filename-contains-two-consecutive-periods-tt21407604.html")
    public void testDoubleDots() throws Exception {
        // create a problematic file name in the workspace
        FreeStyleProject p = createFreeStyleProject();
        p.getBuildersList().add(new Shell("touch abc..def"));
        p.scheduleBuild2(0).get();

        // can we see it?
        new WebClient().goTo("job/"+p.getName()+"/ws/abc..def","application/octet-stream");

        // TODO: implement negative check to make sure we aren't serving unexpected directories.
        // the following trivial attempt failed. Someone in between is normalizing.
//        // but this should fail
//        try {
//            new WebClient().goTo("job/" + p.getName() + "/ws/abc/../", "application/octet-stream");
//        } catch (FailingHttpStatusCodeException e) {
//            assertEquals(400,e.getStatusCode());
//        }
    }
}
