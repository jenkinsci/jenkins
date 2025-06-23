package jenkins.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class JenkinsSystemReadPermissionTest {

    private static final String SYSTEM_READER = "systemReader";

    private JenkinsRule.WebClient webClient;

    private static JenkinsRule j;

    @BeforeAll
    static void setUp(JenkinsRule rule) {
        j = rule;
        System.setProperty("jenkins.security.SystemReadPermission", "true");
    }

    @AfterAll
    static void disablePermission() {
        System.clearProperty("jenkins.security.SystemReadPermission");
    }

    @BeforeEach
    void setup() {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ, Jenkins.SYSTEM_READ).everywhere().to(SYSTEM_READER));

        webClient = j.createWebClient();
        webClient.setThrowExceptionOnFailingStatusCode(false);
    }

    @Test
    void configureReadAllowedWithSystemReadPermission() throws Exception {
        HtmlPage configure = webClient.login(SYSTEM_READER)
                .goTo("configure");
        assertThat(configure.getWebResponse().getStatusCode(), is(200));
    }

    @Test
    void configureConfigSubmitBlockedWithSystemReadPermission() throws Exception {
        HtmlPage configure = webClient.login(SYSTEM_READER)
                .goTo("configure");
        assertThat(configure.getWebResponse().getStatusCode(), is(200));

        HtmlForm configureForm = configure.getFormByName("config");
        HtmlPage submit = j.submit(configureForm);

        assertThat(submit.getWebResponse().getStatusCode(), is(403));
    }
}
