package hudson.scm;

import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlButton;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import org.dom4j.Document;
import org.dom4j.io.DOMReader;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.HudsonHomeLoader.CopyExisting;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.recipes.PresetData;
import static org.jvnet.hudson.test.recipes.PresetData.DataSet.ANONYMOUS_READONLY;

import java.io.File;

/**
 * @author Kohsuke Kawaguchi
 */
public class SubversionSCMTest extends HudsonTestCase {
    @PresetData(ANONYMOUS_READONLY)
    @Bug(2380)
    public void testTaggingPermission() throws Exception {
        // create a build
        File svnRepo = new CopyExisting(getClass().getResource("/svn-repo.zip")).allocate();
        FreeStyleProject p = createFreeStyleProject();
        p.setScm(new SubversionSCM(
                new String[]{"file://"+svnRepo+"/trunk/a"},
                new String[]{null},
                true, null
        ));
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

        // and that tagging would fail
        html = wc.getPage(b,"tagBuild/");
        HtmlForm form = html.getFormByName("tag");
        try {
            form.submit((HtmlButton)last(form.getHtmlElementsByTagName("button")));
            fail("should have been denied");
        } catch (FailingHttpStatusCodeException e) {
            // make sure the request is denied
            assertEquals(e.getResponse().getStatusCode(),403);
        }

        // now login as alice and make sure that the tagging would succeed
        wc = new WebClient();
        wc.login("alice","alice");
        html = wc.getPage(b,"tagBuild/");
        form = html.getFormByName("tag");
        form.submit((HtmlButton)last(form.getHtmlElementsByTagName("button")));
    }
}
