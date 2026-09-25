package jenkins.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import hudson.model.UnprotectedRootAction;
import hudson.security.ACL;
import hudson.security.FullControlOnceLoggedInAuthorizationStrategy;
import hudson.util.HttpResponses;
import jenkins.model.Jenkins;
import org.htmlunit.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.HttpResponse;

@WithJenkins
class Security380Test {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Issue("SECURITY-380")
    @Test
    void testGetItemsWithoutAnonRead() throws Exception {
        FullControlOnceLoggedInAuthorizationStrategy strategy = new FullControlOnceLoggedInAuthorizationStrategy();
        strategy.setAllowAnonymousRead(false);
        Jenkins.get().setAuthorizationStrategy(strategy);

        Jenkins.get().setSecurityRealm(j.createDummySecurityRealm());

        j.createFreeStyleProject();
        ACL.impersonate2(Jenkins.ANONYMOUS2, () -> assertEquals(0, Jenkins.get().getItems().size(), "no items"));
    }

    @Issue("SECURITY-380")
    @Test
    void testGetItems() throws Exception {
        FullControlOnceLoggedInAuthorizationStrategy strategy = new FullControlOnceLoggedInAuthorizationStrategy();
        strategy.setAllowAnonymousRead(true);
        Jenkins.get().setAuthorizationStrategy(strategy);

        Jenkins.get().setSecurityRealm(j.createDummySecurityRealm());

        j.createFreeStyleProject();
        ACL.impersonate2(Jenkins.ANONYMOUS2, () -> assertEquals(1, Jenkins.get().getItems().size(), "one item"));
    }

    @Issue("SECURITY-380")
    @Test
    void testWithUnprotectedRootAction() throws Exception {
        FullControlOnceLoggedInAuthorizationStrategy strategy = new FullControlOnceLoggedInAuthorizationStrategy();
        strategy.setAllowAnonymousRead(false);
        Jenkins.get().setAuthorizationStrategy(strategy);

        Jenkins.get().setSecurityRealm(j.createDummySecurityRealm());
        j.createFreeStyleProject();

        JenkinsRule.WebClient wc = j.createWebClient();
        Page page = wc.goTo("listJobs", "text/plain");
        // return "0\r\n"
        assertEquals("0", page.getWebResponse().getContentAsString().trim(), "expect 0 items");
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
            return HttpResponses.text(Integer.toString(Jenkins.get().getItems().size()));
        }
    }
}
