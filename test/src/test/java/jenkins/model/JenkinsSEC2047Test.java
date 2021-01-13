package jenkins.model;

import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.model.RootAction;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.TestExtension;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.fail;

//TODO merge back to JenkinsTest (or put it somewhere else)
public class JenkinsSEC2047Test {

    @Rule public JenkinsRule j = new JenkinsRule();

    @Issue("SECURITY-2047")
    @Test
    public void testLogin123() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy());
        WebClient wc = j.createWebClient();

        try {
            HtmlPage login123 = wc.goTo("login123");
            fail("Page should be protected.");
        } catch (FailingHttpStatusCodeException e) {
            assertThat(e.getStatusCode(), is(403));
        }
    }

    @Issue("SECURITY-2047")
    @Test
    public void testLogin123WithRead() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().
                grant(Jenkins.READ).everywhere().to("bob"));
        WebClient wc = j.createWebClient();

        wc.login("bob");
        HtmlPage login123 = wc.goTo("login123");
        assertThat(login123.getWebResponse().getStatusCode(), is(200));
        assertThat(login123.getWebResponse().getContentAsString(), containsString("This should be protected"));
    }

    @Test
    public void testLogin() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().
                grant(Jenkins.READ).everywhere().to("bob"));
        WebClient wc = j.createWebClient();

        HtmlPage login = wc.goTo("login");
        assertThat(login.getWebResponse().getStatusCode(), is(200));
        assertThat(login.getWebResponse().getContentAsString(), containsString("login"));
    }

    @TestExtension({"testLogin123", "testLogin123WithRead"})
    public static class ProtectedRootAction implements RootAction {
        @Override
        public String getIconFileName() {
            return "document.png";
        }

        @Override
        public String getDisplayName() {
            return "I am PROTECTED";
        }

        @Override
        public String getUrlName() {
            return "login123";
        }
    }

}
