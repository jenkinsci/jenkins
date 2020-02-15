package jenkins.model;

import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class JenkinsSystemReadAndManagePermissionTest {

    private static final String SYSTEM_READER = "systemReader";

    @BeforeClass
    public static void enablePermissions() {
        System.setProperty("jenkins.security.SystemReadPermission", "true");
        System.setProperty("jenkins.security.ManagePermission", "true");
    }

    @AfterClass
    public static void disablePermissions() {
        System.clearProperty("jenkins.security.SystemReadPermission");
        System.clearProperty("jenkins.security.ManagePermission");
    }

    @ClassRule
    public static JenkinsRule j = new JenkinsRule();

    private JenkinsRule.WebClient webClient;

    @Before
    public void setup() {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.MANAGE, Jenkins.SYSTEM_READ).everywhere().to(SYSTEM_READER));

        webClient = j.createWebClient();
        webClient.setThrowExceptionOnFailingStatusCode(false);
    }

    @Test
    public void configureReadAllowedWithSystemReadAndManagePermission() throws Exception {
        HtmlPage configure = webClient.login(SYSTEM_READER)
                .goTo("configure");
        assertThat(configure.getWebResponse().getStatusCode(), is(200));
    }

    @Test
    public void configureConfigSubmitAllowedWithSystemReadAndManagePermission() throws Exception {
        HtmlPage configure = webClient.login(SYSTEM_READER)
                .goTo("configure");
        assertThat(configure.getWebResponse().getStatusCode(), is(200));

        HtmlForm configureForm = configure.getFormByName("config");
        HtmlPage submit = j.submit(configureForm);

        assertThat(submit.getWebResponse().getStatusCode(), is(200));
    }
}
