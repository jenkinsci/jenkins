package hudson.util;

import static jakarta.servlet.http.HttpServletResponse.SC_SERVICE_UNAVAILABLE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.htmlunit.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class HudsonIsRestartingTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    @Issue("JENKINS-55062")
    void withPrefix() throws Exception {
        j.jenkins.getServletContext().setAttribute("app", new HudsonIsRestarting(false));
        JenkinsRule.WebClient wc = j.createWebClient()
                // this is a failure page already
                .withThrowExceptionOnFailingStatusCode(false)
                .withJavaScriptEnabled(false);

        checkingPage(wc, "");
        checkingPage(wc, "anyRandomString");
        checkingPage(wc, "multiple/layer/ofRelative.xml");
    }

    private void checkingPage(JenkinsRule.WebClient wc, String relativePath) throws Exception {
        Page p = wc.goTo(relativePath, "text/html");
        assertTrue(p.isHtmlPage());
        assertEquals(SC_SERVICE_UNAVAILABLE, p.getWebResponse().getStatusCode());
        String body = p.getWebResponse().getContentAsString();
        assertThat(body, containsString("data-resurl=\""));
        assertThat(body, containsString("data-rooturl=\""));
        assertThat(body, containsString("resURL=\""));
    }
}
