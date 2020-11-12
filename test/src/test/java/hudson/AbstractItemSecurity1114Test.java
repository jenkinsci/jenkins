package hudson;

import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import hudson.model.AbstractItem;
import hudson.model.Item;
import hudson.model.RootAction;
import hudson.security.ACL;
import hudson.security.ACLContext;
import jenkins.model.Jenkins;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.For;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.TestExtension;

import edu.umd.cs.findbugs.annotations.CheckForNull;

public class AbstractItemSecurity1114Test {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    @Issue("SECURITY-1114")
    @For(AbstractItem.class)
    public void testAccess() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        MockAuthorizationStrategy authorizationStrategy = new MockAuthorizationStrategy();
        authorizationStrategy.grant(Jenkins.READ).onRoot().toEveryone();
        authorizationStrategy.grant(Item.DISCOVER).everywhere().to("alice");
        authorizationStrategy.grant(Item.READ).everywhere().to("bob");
        j.jenkins.setAuthorizationStrategy(authorizationStrategy);

        j.createFreeStyleProject("myproject");

        // alice can discover project
        JenkinsRule.WebClient wc = j.createWebClient().login("alice");
        try {
            wc.goTo("bypass/myproject");
            Assert.fail("expected exception");
        } catch (FailingHttpStatusCodeException e) {
            Assert.assertEquals("alice can discover", 403, e.getStatusCode());
        }

        // bob can read project
        wc = j.createWebClient().login("bob");
        wc.goTo("bypass/myproject"); // success


        // carol has no permissions
        wc = j.createWebClient().login("carol");
        try {
            wc.goTo("bypass/nonexisting");
            Assert.fail("expected exception");
        } catch (FailingHttpStatusCodeException e) {
            Assert.assertEquals("carol gets 404 for nonexisting project", 404, e.getStatusCode());
        }
        try {
            wc.goTo("bypass/myproject");
            Assert.fail("expected exception");
        } catch (FailingHttpStatusCodeException e) {
            Assert.assertEquals("carol gets 404 for invisible project", 404, e.getStatusCode());
        }

    }

    @TestExtension
    public static class BypassAccess implements RootAction {
        public Item getDynamic(String name) {
            Item item;
            try (ACLContext ctx = ACL.as2(ACL.SYSTEM2)) {
                 item = Jenkins.get().getItemByFullName(name);
            }
            return item;
        }

        @CheckForNull
        @Override
        public String getIconFileName() {
            return null;
        }

        @CheckForNull
        @Override
        public String getDisplayName() {
            return null;
        }

        @CheckForNull
        @Override
        public String getUrlName() {
            return "bypass";
        }
    }
}
