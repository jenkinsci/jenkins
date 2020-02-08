package jenkins.model;

import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class JenkinsSystemReadPermissionTest {

    private static final String SYSTEM_READER = "systemReader";

    static {
        System.setProperty("jenkins.security.SystemReadPermission", "true");
    }

    @ClassRule
    public static JenkinsRule j = new JenkinsRule();

    private JenkinsRule.WebClient webClient;

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

    @AfterClass
    public static void reset() {
        System.clearProperty("jenkins.security.SystemReadPermission");
    }
}
