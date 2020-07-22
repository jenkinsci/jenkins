package jenkins.security;

import com.gargoylesoftware.htmlunit.Page;
import hudson.model.UnprotectedRootAction;
import hudson.security.ACL;
import hudson.security.FullControlOnceLoggedInAuthorizationStrategy;
import hudson.util.HttpResponses;
import jenkins.model.Jenkins;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.HttpResponse;

public class Security380Test {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Issue("SECURITY-380")
    @Test
    public void testGetItemsWithoutAnonRead() throws Exception {
        FullControlOnceLoggedInAuthorizationStrategy strategy = new FullControlOnceLoggedInAuthorizationStrategy();
        strategy.setAllowAnonymousRead(false);
        Jenkins.get().setAuthorizationStrategy(strategy);

        Jenkins.get().setSecurityRealm(j.createDummySecurityRealm());

        j.createFreeStyleProject();
        ACL.impersonate2(Jenkins.ANONYMOUS2, new Runnable() {
            @Override
            public void run() {
                Assert.assertEquals("no items", 0, Jenkins.get().getItems().size());
            }
        });
    }

    @Issue("SECURITY-380")
    @Test
    public void testGetItems() throws Exception {
        FullControlOnceLoggedInAuthorizationStrategy strategy = new FullControlOnceLoggedInAuthorizationStrategy();
        strategy.setAllowAnonymousRead(true);
        Jenkins.get().setAuthorizationStrategy(strategy);

        Jenkins.get().setSecurityRealm(j.createDummySecurityRealm());

        j.createFreeStyleProject();
        ACL.impersonate2(Jenkins.ANONYMOUS2, new Runnable() {
            @Override
            public void run() {
                Assert.assertEquals("one item", 1, Jenkins.get().getItems().size());
            }
        });
    }

    @Issue("SECURITY-380")
    @Test
    public void testWithUnprotectedRootAction() throws Exception {
        FullControlOnceLoggedInAuthorizationStrategy strategy = new FullControlOnceLoggedInAuthorizationStrategy();
        strategy.setAllowAnonymousRead(false);
        Jenkins.get().setAuthorizationStrategy(strategy);

        Jenkins.get().setSecurityRealm(j.createDummySecurityRealm());
        j.createFreeStyleProject();

        JenkinsRule.WebClient wc = j.createWebClient();
        Page page = wc.goTo("listJobs", "text/plain");
        // return "0\r\n"
        Assert.assertEquals("expect 0 items", "0", page.getWebResponse().getContentAsString().trim());
    }

    @TestExtension
    public static class JobListingUnprotectedRootAction implements UnprotectedRootAction {

        @Override
        public String getIconFileName() {
            return null;
        }

        @Override
        public String getDisplayName() {
            return null;
        }

        @Override
        public String getUrlName() {
            return "listJobs";
        }

        public HttpResponse doIndex() throws Exception {
            return HttpResponses.plainText(Integer.toString(Jenkins.get().getItems().size()));
        }
    }
}
