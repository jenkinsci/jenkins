package hudson.scm;

import com.gargoylesoftware.htmlunit.ElementNotFoundException;
import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.*;
import org.dom4j.Document;
import org.dom4j.io.DOMReader;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.Email;
import org.jvnet.hudson.test.HudsonHomeLoader.CopyExisting;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.recipes.PresetData;
import static org.jvnet.hudson.test.recipes.PresetData.DataSet.ANONYMOUS_READONLY;

/**
 * @author Kohsuke Kawaguchi
 */
public class SubversionSCMTest extends HudsonTestCase {
    @PresetData(ANONYMOUS_READONLY)
    @Bug(2380)
    public void testTaggingPermission() throws Exception {
        // create a build
        FreeStyleProject p = createFreeStyleProject();
        p.setScm(loadSvnRepo());
        FreeStyleBuild b = p.scheduleBuild2(0).get();
        System.out.println(b.getLog());
        assertBuildStatus(Result.SUCCESS,b);

        SubversionTagAction action = b.getAction(SubversionTagAction.class);
        assertFalse(b.hasPermission(action.getPermission()));

        WebClient wc = new WebClient();
        HtmlPage html = wc.getPage(b);

        // make sure there's no link to the 'tag this build'
        Document dom = new DOMReader().read(html);
        assertNull(dom.selectSingleNode("//A[text()='Tag this build']"));
        for( HtmlAnchor a : html.getAnchors() )
            assertFalse(a.getHrefAttribute().contains("/tagBuild/"));

        // and no tag form on tagBuild page
        html = wc.getPage(b,"tagBuild/");
        try {
            html.getFormByName("tag");
            fail("should not have been found");
        } catch (ElementNotFoundException e) {
        }

        // and that tagging would fail
        try {
            wc.getPage(b,"tagBuild/submit?name0=test&Submit=Tag");
            fail("should have been denied");
        } catch (FailingHttpStatusCodeException e) {
            // make sure the request is denied
            assertEquals(e.getResponse().getStatusCode(),403);
        }

        // now login as alice and make sure that the tagging would succeed
        wc = new WebClient();
        wc.login("alice","alice");
        html = wc.getPage(b,"tagBuild/");
        HtmlForm form = html.getFormByName("tag");
        submit(form);
    }

    /**
     * Loads a test Subversion repository into a temporary directory, and creates {@link SubversionSCM} for it.
     */
    private SubversionSCM loadSvnRepo() throws Exception {
        return new SubversionSCM(
                new String[] { "file://" + new CopyExisting(getClass().getResource("/svn-repo.zip")).allocate().toURI().toURL().getPath() + "trunk/a" },
                new String[]{null},
                true, null
        );
    }

    @Email("http://www.nabble.com/Hudson-1.266-and-1.267%3A-Subversion-authentication-broken--td21156950.html")
    public void testHttpsCheckOut() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        p.setScm(new SubversionSCM(
                new String[]{"https://svn.dev.java.net/svn/hudson/trunk/hudson/test-projects/trivial-ant"},
                new String[]{null},
                true, null
        ));

        FreeStyleBuild b = p.scheduleBuild2(0).get();
        System.out.println(b.getLog());
        assertBuildStatus(Result.SUCCESS,b);
        assertTrue(p.getWorkspace().child("trivial-ant/build.xml").exists());
    }

    @Email("http://www.nabble.com/Hudson-1.266-and-1.267%3A-Subversion-authentication-broken--td21156950.html")
    public void testHttpCheckOut() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        p.setScm(new SubversionSCM(
                new String[]{"http://svn.codehaus.org/plexus/tags/JASF_INIT/plexus-avalon-components/jasf/"},
                new String[]{null},
                true, null
        ));

        FreeStyleBuild b = p.scheduleBuild2(0).get();
        System.out.println(b.getLog());
        assertBuildStatus(Result.SUCCESS,b);
        assertTrue(p.getWorkspace().child("jasf/maven.xml").exists());
    }

    /**
     * Tests the "URL@REV" format in SVN URL.
     */
    @Bug(262)
    public void testRevisionedCheckout() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        p.setScm(new SubversionSCM(
                new String[]{"https://svn.dev.java.net/svn/hudson/trunk/hudson/test-projects/trivial-ant@13000"},
                new String[]{null},
                true, null
        ));

        FreeStyleBuild b = p.scheduleBuild2(0).get();
        System.out.println(b.getLog());
        assertTrue(b.getLog().contains("At revision 13000"));
        assertBuildStatus(Result.SUCCESS,b);

        b = p.scheduleBuild2(0).get();
        System.out.println(b.getLog());
        assertTrue(b.getLog().contains("At revision 13000"));
        assertBuildStatus(Result.SUCCESS,b);
    }

    /**
     * {@link SubversionSCM#pollChanges(AbstractProject, Launcher, FilePath, TaskListener)} should notice
     * if the workspace and the current configuration is inconsistent and schedule a new build.
     */
    @Email("http://www.nabble.com/Proper-way-to-switch---relocate-SVN-tree---tt21173306.html")
    public void testPollingAfterRelocation() throws Exception {
        // fetch the current workspace
        FreeStyleProject p = createFreeStyleProject();
        p.setScm(loadSvnRepo());
        p.scheduleBuild2(0).get();

        // as a baseline, this shouldn't detect any change
        TaskListener listener = createTaskListener();
        assertFalse(p.pollSCMChanges(listener));

        // now switch the repository to a new one.
        // this time the polling should indicate that we need a new build
        p.setScm(loadSvnRepo());
        assertTrue(p.pollSCMChanges(listener));

        // build it once again to switch
        p.scheduleBuild2(0).get();

        // then no more change should be detected
        assertFalse(p.pollSCMChanges(listener));
    }
}
