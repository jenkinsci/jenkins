package jenkins.model;

import com.gargoylesoftware.htmlunit.html.HtmlInput;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import org.jvnet.hudson.test.HudsonTestCase;

/**
 * @author Kohsuke Kawaguchi
 */
public class JenkinsLocationConfigurationTest extends HudsonTestCase {
    /**
     * Makes sure the use of "localhost" in the Hudson URL reports a warning.
     */
    public void testLocalHostWarning() throws Exception {
        HtmlPage p = new WebClient().goTo("configure");
        HtmlInput url = p.getFormByName("config").getInputByName("_.url");
        url.setValueAttribute("http://localhost:1234/");
        assertTrue(p.getDocumentElement().getTextContent().contains("instead of localhost"));
    }
}
