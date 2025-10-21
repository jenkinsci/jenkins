package jenkins.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.DataBoundSetter;

@WithJenkins
class JenkinsSystemReadAndManagePermissionTest {

    private static final String SYSTEM_READER_AND_MANAGER = "systemReaderAndManager";

    @BeforeAll
    static void setUp(JenkinsRule rule) {
        j = rule;
        System.setProperty("jenkins.security.SystemReadPermission", "true");
        System.setProperty("jenkins.security.ManagePermission", "true");
    }

    @AfterAll
    static void disablePermissions() {
        System.clearProperty("jenkins.security.SystemReadPermission");
        System.clearProperty("jenkins.security.ManagePermission");
    }

    private JenkinsRule.WebClient webClient;

    private static JenkinsRule j;

    @BeforeEach
    void setup() {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.MANAGE, Jenkins.SYSTEM_READ, Jenkins.READ).everywhere().to(SYSTEM_READER_AND_MANAGER));

        webClient = j.createWebClient();
        webClient.setThrowExceptionOnFailingStatusCode(false);
    }

    @Test
    void configureReadAllowedWithSystemReadAndManagePermission() throws Exception {
        HtmlPage configure = webClient.login(SYSTEM_READER_AND_MANAGER)
                .goTo("configure");
        assertThat(configure.getWebResponse().getStatusCode(), is(200));
    }

    @Test
    void configureConfigSubmitAllowedWithSystemReadAndManagePermission() throws Exception {
        HtmlPage configure = webClient.login(SYSTEM_READER_AND_MANAGER)
                .goTo("configure");
        assertThat(configure.getWebResponse().getStatusCode(), is(200));

        HtmlForm configureForm = configure.getFormByName("config");
        HtmlPage submit = j.submit(configureForm);

        assertThat(submit.getWebResponse().getStatusCode(), is(200));
    }

    @Test
    void cannotModifyReadOnlyConfiguration() throws Exception {
        HtmlPage configure = webClient.login(SYSTEM_READER_AND_MANAGER)
                .goTo("configure");

        //GIVEN the Global Configuration Form, with some changes unsaved
        HtmlForm form = configure.getFormByName("config");

        // WHEN a user with Jenkins.MANAGE and Jenkins.SYSTEM_READ permission tries to save the changes
        j.submit(form);
        // THEN the changes on fields forbidden to a Jenkins.MANAGE permission are not saved
        Config config = GlobalConfiguration.all().get(Config.class);

        assertNotNull(config);
        assertNull(config.getNumber(), "shouldn't be allowed to change a GlobalConfiguration that needs Overall/Administer");
    }

    @TestExtension
    public static class Config extends GlobalConfiguration {

        private String number;

        @SuppressWarnings("checkstyle:redundantmodifier")
        public Config() {
        }

        public String getNumber() {
            return number;
        }

        @DataBoundSetter
        public void setNumber(String number) {
            this.number = number;
            save();
        }
    }
}
