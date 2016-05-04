package jenkins.security;

import com.gargoylesoftware.htmlunit.Page;
import java.net.URL;
import static org.junit.Assert.assertEquals;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;

/**
 * @author Kohsuke Kawaguchi
 */
@Issue("SECURITY-177")
public class Security177Test {
    @Rule
    public JenkinsRule jenkins = new JenkinsRule();
    
    @Test
    public void nosniff() throws Exception {
        WebClient wc = jenkins.createWebClient();
        wc.getOptions().setThrowExceptionOnFailingStatusCode(false);

        URL u = jenkins.getURL();
        verifyNoSniff(wc.getPage(new URL(u, "adjuncts/507db12b/nosuch/adjunct.js")));
        verifyNoSniff(wc.getPage(new URL(u, "no-such-page")));
        verifyNoSniff(wc.getPage(new URL(u, "images/title.svg")));
        verifyNoSniff(wc.getPage(u));
    }

    private void verifyNoSniff(Page p) {
        String v = p.getWebResponse().getResponseHeaderValue("X-Content-Type-Options");
        assertEquals(v,"nosniff");
    }
}
