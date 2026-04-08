package jenkins.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URL;
import org.htmlunit.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * @author Kohsuke Kawaguchi
 */
@Issue("SECURITY-177")
@WithJenkins
class Security177Test {

    private JenkinsRule jenkins;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        jenkins = rule;
    }

    @Test
    void nosniff() throws Exception {
        WebClient wc = jenkins.createWebClient()
                .withThrowExceptionOnFailingStatusCode(false);

        URL u = jenkins.getURL();
        verifyNoSniff(wc.getPage(new URL(u, "adjuncts/507db12b/nosuch/adjunct.js")));
        verifyNoSniff(wc.getPage(new URL(u, "no-such-page")));
        verifyNoSniff(wc.getPage(new URL(u, "images/title.svg")));
        verifyNoSniff(wc.getPage(u));
    }

    private void verifyNoSniff(Page p) {
        String v = p.getWebResponse().getResponseHeaderValue("X-Content-Type-Options");
        assertEquals("nosniff", v);
    }
}
