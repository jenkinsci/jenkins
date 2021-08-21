package hudson.util;

import com.gargoylesoftware.htmlunit.Page;
import org.hamcrest.CoreMatchers;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import static javax.servlet.http.HttpServletResponse.SC_SERVICE_UNAVAILABLE;
import static org.junit.Assert.assertEquals;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

public class HudsonIsRestartingTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    @Issue("JENKINS-55062")
    public void withPrefix() throws Exception {
        j.jenkins.servletContext.setAttribute("app", new HudsonIsRestarting());
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
        assertThat(body, CoreMatchers.containsString("data-resurl=\""));
        assertThat(body, CoreMatchers.containsString("data-rooturl=\""));
        assertThat(body, CoreMatchers.containsString("resURL=\""));
    }
}
