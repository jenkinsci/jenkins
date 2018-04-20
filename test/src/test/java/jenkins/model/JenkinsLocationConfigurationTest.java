package jenkins.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

import com.gargoylesoftware.htmlunit.html.HtmlInput;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * @author Kohsuke Kawaguchi
 */
public class JenkinsLocationConfigurationTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    /**
     * Makes sure the use of "localhost" in the Hudson URL reports a warning.
     */
    @Test
    public void localhostWarning() throws Exception {
        HtmlPage p = j.createWebClient().goTo("configure");
        HtmlInput url = p.getFormByName("config").getInputByName("_.url");
        url.setValueAttribute("http://localhost:1234/");
        assertThat(p.getDocumentElement().getTextContent(), containsString("instead of localhost"));
    }
}
