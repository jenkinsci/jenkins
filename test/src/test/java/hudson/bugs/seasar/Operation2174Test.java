package hudson.bugs.seasar;

import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.maven.MavenModuleSet;
import hudson.model.FreeStyleProject;
import hudson.tasks.BuildTrigger;
import org.jvnet.hudson.test.HudsonTestCase;

import java.util.Collections;

/**
 * See http://ml.seasar.org/archives/operation/2008-November/004003.html
 *
 * @author Kohsuke Kawaguchi
 */
public class Operation2174Test extends HudsonTestCase {
    /**
     * Upstream/downstream relationship lost.
     */
    public void testBuildChains() throws Exception {
        FreeStyleProject up = createFreeStyleProject("up");
        MavenModuleSet dp = createMavenProject("dp");

        // designate 'dp' as the downstream in 'up'
        WebClient webClient = new WebClient();
        HtmlPage page = webClient.getPage(up,"configure");

        HtmlForm form = page.getFormByName("config");

        // configure downstream build
        form.getInputByName(BuildTrigger.DESCRIPTOR.getJsonSafeClassName()).click();
        form.getInputByName("buildTrigger.childProjects").setValueAttribute("dp");
        submit(form);

        // verify that the relationship is set up
        BuildTrigger trigger = (BuildTrigger) up.getPublishersList().get(BuildTrigger.DESCRIPTOR);
        assertEquals(trigger.getChildProjects(), Collections.singletonList(dp));

        // now go ahead and edit the downstream
        page = webClient.getPage(dp,"configure");
        form = page.getFormByName("config");
        submit(form);

        // verify that the relationship is set up
        trigger = (BuildTrigger) up.getPublishersList().get(BuildTrigger.DESCRIPTOR);
        assertNotNull(trigger);
        assertEquals(trigger.getChildProjects(), Collections.singletonList(dp));
    }
}
