package jenkins.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlPage;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;

public class JenkinsSystemReadPermissionTest {

    private static final String SYSTEM_READER = "systemReader";

    @ClassRule
    public static JenkinsRule j = new JenkinsRule();

    private JenkinsRule.WebClient webClient;

    @BeforeClass
    public static void enablePermission() {
        System.setProperty("jenkins.security.SystemReadPermission", "true");
    }

    @AfterClass
    public static void disablePermission() {
        System.clearProperty("jenkins.security.SystemReadPermission");
    }

    @Before
    public void setup() {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ, Jenkins.SYSTEM_READ).everywhere().to(SYSTEM_READER));

        webClient = j.createWebClient();
        webClient.setThrowExceptionOnFailingStatusCode(false);
    }

    @Test
    public void configureReadAllowedWithSystemReadPermission() throws Exception {
        HtmlPage configure = webClient.login(SYSTEM_READER)
                .goTo("configure");
        assertThat(configure.getWebResponse().getStatusCode(), is(200));
    }

    @Test
    public void configureConfigSubmitBlockedWithSystemReadPermission() throws Exception {
        HtmlPage configure = webClient.login(SYSTEM_READER)
                .goTo("configure");
        assertThat(configure.getWebResponse().getStatusCode(), is(200));

        HtmlForm configureForm = configure.getFormByName("config");
        HtmlPage submit = j.submit(configureForm);

        assertThat(submit.getWebResponse().getStatusCode(), is(403));
    }
}
