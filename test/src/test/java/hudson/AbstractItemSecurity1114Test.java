package hudson;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.model.AbstractItem;
import hudson.model.Item;
import hudson.model.RootAction;
import hudson.security.ACL;
import hudson.security.ACLContext;
import jenkins.model.Jenkins;
import org.htmlunit.FailingHttpStatusCodeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.For;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class AbstractItemSecurity1114Test {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    @Issue("SECURITY-1114")
    @For(AbstractItem.class)
    void testAccess() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        MockAuthorizationStrategy authorizationStrategy = new MockAuthorizationStrategy();
        authorizationStrategy.grant(Jenkins.READ).onRoot().toEveryone();
        authorizationStrategy.grant(Item.DISCOVER).everywhere().to("alice");
        authorizationStrategy.grant(Item.READ).everywhere().to("bob");
        j.jenkins.setAuthorizationStrategy(authorizationStrategy);

        j.createFreeStyleProject("myproject");

        // alice can discover project
        JenkinsRule.WebClient alice = j.createWebClient().login("alice");
        FailingHttpStatusCodeException e = assertThrows(FailingHttpStatusCodeException.class, () -> alice.goTo("bypass/myproject"));
        assertEquals(403, e.getStatusCode(), "alice can discover");

        // bob can read project
        JenkinsRule.WebClient bob = j.createWebClient().login("bob");
        bob.goTo("bypass/myproject"); // success


        // carol has no permissions
        JenkinsRule.WebClient carol = j.createWebClient().login("carol");
        e = assertThrows(FailingHttpStatusCodeException.class, () -> carol.goTo("bypass/nonexisting"));
        assertEquals(404, e.getStatusCode(), "carol gets 404 for nonexisting project");
        e = assertThrows(FailingHttpStatusCodeException.class, () -> carol.goTo("bypass/myproject"));
        assertEquals(404, e.getStatusCode(), "carol gets 404 for invisible project");
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
