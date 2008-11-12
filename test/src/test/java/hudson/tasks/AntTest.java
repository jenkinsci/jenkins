package hudson.tasks;

import org.jvnet.hudson.test.HudsonTestCase;
import hudson.model.FreeStyleProject;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlButton;

/**
 * @author Kohsuke Kawaguchi
 */
public class AntTest extends HudsonTestCase {
    /**
     * Tests the round-tripping of the configuration.
     */
    public void testConfigRoundtrip() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        p.getBuildersList().add(new Ant("a",null,"-b","c.xml","d=e"));

        WebClient webClient = new WebClient();
        HtmlPage page = webClient.getPage(p,"configure");

        HtmlForm form = page.getFormByName("config");
        form.submit((HtmlButton)last(form.getHtmlElementsByTagName("button")));

        Ant a = (Ant)p.getBuildersList().get(Ant.DESCRIPTOR);
        assertNotNull(a);
        assertEquals("a",a.getTargets());
        assertNull(a.getAnt());
        assertEquals("-b",a.getAntOpts());
        assertEquals("c.xml",a.getBuildFile());
        assertEquals("d=e",a.getProperties());
    }
}
