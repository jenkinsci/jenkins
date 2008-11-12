package hudson.tasks;

import com.gargoylesoftware.htmlunit.html.HtmlButton;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.model.FreeStyleProject;
import org.jvnet.hudson.test.HudsonTestCase;

/**
 * @author Kohsuke Kawaguchi
 */
public class MavenTest extends HudsonTestCase {
    /**
     * Tests the round-tripping of the configuration.
     */
    public void testConfigRoundtrip() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        p.getBuildersList().add(new Maven("a",null,"b.pom","c=d","-e"));

        WebClient webClient = new WebClient();
        HtmlPage page = webClient.getPage(p,"configure");

        HtmlForm form = page.getFormByName("config");
        form.submit((HtmlButton)last(form.getHtmlElementsByTagName("button")));

        Maven m = (Maven)p.getBuildersList().get(Maven.DESCRIPTOR);
        assertNotNull(m);
        assertEquals("a",m.targets);
        assertNull(m.mavenName);
        assertEquals("b.pom",m.pom);
        assertEquals("c=d",m.properties);
        assertEquals("-e",m.jvmOptions);
    }
}
