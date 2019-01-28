package hudson.util;

import com.gargoylesoftware.htmlunit.Page;
import org.hamcrest.CoreMatchers;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class HudsonIsLoading {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    @Issue("JENKINS-55062")
    public void withPrefix() throws Exception {
        j.jenkins.servletContext.setAttribute("app", new HudsonIsLoading());
        JenkinsRule.WebClient wc = j.createWebClient();
        wc.getOptions().setThrowExceptionOnFailingStatusCode(false); // this is a failure page already
        wc.getOptions().setJavaScriptEnabled(false);

        Page p = wc.goTo("", "text/html");
        assertTrue( p.isHtmlPage() );
        String body = p.getWebResponse().getContentAsString();
        assertThat(body, CoreMatchers.containsString("data-resurl=\""));
        assertThat(body, CoreMatchers.containsString("data-rooturl=\""));
        assertThat(body, CoreMatchers.containsString("resURL=\""));
    }
}
