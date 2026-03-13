package jenkins.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jenkins.model.Jenkins;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
public class Security3625Test {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    @Issue("SECURITY-3625")
    void noDropdownContentWithoutOverallRead() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(Jenkins.READ).everywhere().to("reader").grant(Jenkins.ADMINISTER).everywhere().to("admin"));

        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            assertTrue(containsDropdownLinks(wc, "admin"));
        }
        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            assertTrue(containsDropdownLinks(wc, "reader"));
        }
        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            assertFalse(containsDropdownLinks(wc, "nobody"));
        }
    }

    private static boolean containsDropdownLinks(JenkinsRule.WebClient wc, String username) throws Exception {
        final HtmlPage htmlPage = wc.withThrowExceptionOnFailingStatusCode(false).login(username).goTo("");
        return htmlPage.getWebResponse().getContentAsString().contains("data-dropdown-text=\"Security\" data-dropdown-type=\"ITEM\" data-dropdown-href=\"/jenkins/user/" + username + "/security\">");
    }
}
