package jenkins.model;

import static jenkins.model.Security3594Test.ActionImpl.ROOT_ACTION_URL_NAME;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import hudson.model.InvisibleAction;
import hudson.model.UnprotectedRootAction;
import java.io.IOException;
import org.htmlunit.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.xml.sax.SAXException;

@WithJenkins
public class Security3594Test {
    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule j) {
        this.j = j;
    }

    @Issue("SECURITY-3594")
    @Test
    public void signupPageNeverHasSidepanel() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(Jenkins.ADMINISTER).everywhere().to("authenticated"));

        try (JenkinsRule.WebClient wc = j.createWebClient().withThrowExceptionOnFailingStatusCode(false)) {
            String content = getSignupPageContent(wc);
            assertThat(content, not(containsString("Build Executor Status")));
            assertThat(content, not(containsString("Build Queue")));
            assertThat(content, not(containsString("Build History")));
            assertThat(content, not(containsString("RSS 2.0"))); // Feed links in header

            wc.login("alice");

            content = getSignupPageContent(wc);

            // we removed the sidepanel from this page entirely
            assertThat(content, not(containsString("Build Executor Status")));
            assertThat(content, not(containsString("Build Queue")));
            assertThat(content, not(containsString("Build History")));
            assertThat(content, not(containsString("RSS 2.0"))); // Feed links in header
        }
    }

    private static String getSignupPageContent(JenkinsRule.WebClient wc) throws IOException, SAXException {
        Page page = wc.goTo("securityRealm/signup");
        String content = page.getWebResponse().getContentAsString();
        assertThat(content, containsString("This is not supported in the current configuration"));
        return content;
    }

    @Issue("SECURITY-3594")
    @Test
    public void sidepanelIsEmptyWithoutOverallRead() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(Jenkins.ADMINISTER).everywhere().to("authenticated"));


        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            String content = getSecurity3594ActionContent(wc);

            assertThat(content, not(containsString("Build Executor Status")));
            assertThat(content, not(containsString("Build Queue")));
            assertThat(content, not(containsString("Build History")));
            assertThat(content, not(containsString("RSS 2.0"))); // Feed links in header

            wc.login("alice");

            content = getSecurity3594ActionContent(wc);

            // this page has a sidepanel, so this content shows with Overall/Read:
            assertThat(content, containsString("Build Executor Status"));
            assertThat(content, containsString("Build Queue"));
            assertThat(content, containsString("Build History"));
            assertThat(content, containsString("RSS 2.0")); // Feed links in header
        }
    }

    private static String getSecurity3594ActionContent(JenkinsRule.WebClient wc) throws IOException, SAXException {
        Page page = wc.goTo(ROOT_ACTION_URL_NAME);
        String content = page.getWebResponse().getContentAsString();
        assertThat(content, containsString(Security3594Test.class.getSimpleName()));
        return content;
    }

    @TestExtension
    public static class ActionImpl extends InvisibleAction implements UnprotectedRootAction {
        public static final String ROOT_ACTION_URL_NAME = "security3594";

        @Override
        public String getUrlName() {
            return ROOT_ACTION_URL_NAME;
        }
    }
}
