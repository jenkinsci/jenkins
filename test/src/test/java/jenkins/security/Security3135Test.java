package jenkins.security;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import hudson.model.FreeStyleProject;
import hudson.model.InvisibleAction;
import hudson.model.UnprotectedRootAction;
import org.htmlunit.FailingHttpStatusCodeException;
import org.htmlunit.html.DomElement;
import org.htmlunit.html.HtmlPage;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;

public class Security3135Test {
    public static final String ACTION_URL = "security3135";
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    @Issue("SECURITY-3135")
    public void contextMenuShouldNotBypassCSRFProtection() throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient();
        FreeStyleProject project = j.createFreeStyleProject("FreestyleProject");
        ViewHolder viewHolder = j.jenkins.getExtensionList(ViewHolder.class).get(ViewHolder.class);
        boolean exceptionThrown = false;

        HtmlPage page = wc.goTo(ACTION_URL);
        DomElement standardMenu = page.getElementById("standard-menu");
        standardMenu.click();
        DomElement contextMenu = page.getElementById("context-menu");
        try {
            contextMenu.click();
        } catch (FailingHttpStatusCodeException e) {
            if (e.getStatusCode() == 405) {
                exceptionThrown = true;
            }
        }

        j.waitUntilNoActivityUpTo(2000); // Give the job 2 seconds to be submitted

        assertTrue("Request was not made", viewHolder.isRequestMade());
        assertTrue("Expected 405 Method Not Allowed", exceptionThrown);
        assertNull("Build should not be scheduled", j.jenkins.getQueue().getItem(project));
        assertNull("Build should not be scheduled", project.getBuildByNumber(1));
    }

    @TestExtension
    public static class ViewHolder extends InvisibleAction implements UnprotectedRootAction {

        public boolean requestMade = false;

        @Override
        public String getUrlName() {
            return ACTION_URL;
        }

        public String getPayload() {
            return "../job/FreestyleProject/build?delay=0sec&";
        }

        public String getStandardPayload() {
            return "../security3135/validate?";
        }

        public boolean isRequestMade() {
            return requestMade;
        }

        public void doValidate(StaplerRequest2 request, StaplerResponse2 response) {
            requestMade = true;
        }
    }
}
