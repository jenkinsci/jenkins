package jenkins.security;

import java.io.IOException; // CAP AL
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
        extractedMethod86262(strategy); // CAP AL
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
        extractedMethod86262(strategy); // CAP AL
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
        extractedMethod86262(strategy); // CAP AL

        JenkinsRule.WebClient wc = j.createWebClient();
        Page page = wc.goTo("listJobs", "text/plain");
        // return "0\r\n"
        Assert.assertEquals("expect 0 items", "0", page.getWebResponse().getContentAsString().trim());
    }
 // CAP AL
    private void extractedMethod86262(final FullControlOnceLoggedInAuthorizationStrategy strategy) throws IOException { // CAP AL
        Jenkins.get().setAuthorizationStrategy(strategy); // CAP AL
         // CAP AL
        Jenkins.get().setSecurityRealm(j.createDummySecurityRealm()); // CAP AL
         // CAP AL
        j.createFreeStyleProject(); // CAP AL
    } // CAP AL

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
