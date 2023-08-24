package jenkins.security;

import static org.junit.Assert.assertNull;

import hudson.model.FreeStyleProject;
import hudson.model.InvisibleAction;
import hudson.model.UnprotectedRootAction;
import org.htmlunit.html.DomElement;
import org.htmlunit.html.HtmlElement;
import org.htmlunit.html.HtmlPage;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;

public class Security3135Test {
    public static final String ACTION_URL = "security3135";
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    @Issue("SECURITY-3135")
    public void contextMenuShouldNotBypassCSRFProtection() throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient();
        FreeStyleProject project = j.createFreeStyleProject("FreestyleProject");

        HtmlPage page = wc.goTo(ACTION_URL);
        DomElement anchor = page.getElementById("context-menu");
        HtmlElement nextSibling = (HtmlElement) anchor.getFirstChild().getNextSibling();
        nextSibling.click();

        j.waitUntilNoActivityUpTo(2000); // Give the job 2 seconds to be submitted
        assertNull("Build should not be scheduled", j.jenkins.getQueue().getItem(project));
        assertNull("Build should not be scheduled", project.getBuildByNumber(1));
    }

    @TestExtension
    public static class ViewHolder extends InvisibleAction implements UnprotectedRootAction {
        @Override
        public String getUrlName() {
            return ACTION_URL;
        }

        public String getPayload() {
            return "../job/FreestyleProject/build?delay=0sec";
        }
    }
}
