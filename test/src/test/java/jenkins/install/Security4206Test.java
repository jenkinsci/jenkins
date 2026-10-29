package jenkins.install;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertAll;

import hudson.ProxyConfiguration;
import java.net.HttpURLConnection;
import java.util.List;
import jenkins.model.Jenkins;
import org.htmlunit.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@Issue("SECURITY-4206")
@WithJenkins
class Security4206Test {

    private static final String PROXY_SERVER = "proxy.corp.internal";
    private static final int PROXY_PORT = 3128;
    private static final String PROXY_USER_NAME = "svc-jenkins-proxy";
    private static final String PROXY_PASSWORD = "s3cr3t-pass";
    private static final String PROXY_NO_PROXY_HOST = ".corp.internal,10.0.0.0/8";

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
        assertThat("this test assumes setup already completed, like a running Jenkins instance",
                j.jenkins.getInstallState().isSetupComplete(), is(true));

        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.ADMINISTER).everywhere().to("alice")
                .grant(Jenkins.READ).onRoot().to("anonymous"));
        j.jenkins.setProxy(new ProxyConfiguration(PROXY_SERVER, PROXY_PORT, PROXY_USER_NAME, PROXY_PASSWORD, PROXY_NO_PROXY_HOST));
    }

    @Test
    void anonymousOverallReadCannotReadProxyConfigurationViaSetupWizard() {
        try (JenkinsRule.WebClient wc = j.createWebClient()
                .withThrowExceptionOnFailingStatusCode(false)
                .withRedirectEnabled(false)
                .withJavaScriptEnabled(false)) {
            wc.setRefreshHandler((page, url, seconds) -> {});
            assertAll(
                    () -> assertProxyConfigurationNotExposed(wc, "setupWizard/proxy-configuration"),
                    () -> assertProxyConfigurationNotExposed(wc, "descriptorByName/jenkins.install.SetupWizard/proxy-configuration")
            );
        }
    }

    @Test
    void administratorCanStillReachProxyConfigurationViaSetupWizard() throws Exception {
        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            wc.login("alice");
            for (String endPoint : List.of("setupWizard/proxy-configuration", "descriptorByName/jenkins.install.SetupWizard/proxy-configuration")) {
                Page page = wc.goTo(endPoint, null);
                assertThat(page.getWebResponse().getStatusCode(), is(HttpURLConnection.HTTP_OK));
                var content = page.getWebResponse().getContentAsString();
                assertThat(content, containsString(PROXY_SERVER));
                // good to have, if we forgot to make password a password field.
                assertThat(content, not(containsString(PROXY_PASSWORD)));
            }
        }
    }

    private void assertProxyConfigurationNotExposed(JenkinsRule.WebClient wc, String path) throws Exception {
        Page page = wc.goTo(path, null);
        String content = page.getWebResponse().getContentAsString();

        assertThat("a user with only Overall/Read must not be able to load " + path,
                page.getWebResponse().getStatusCode(), is(HttpURLConnection.HTTP_FORBIDDEN));
        assertThat(content, not(containsString(PROXY_SERVER)));
        assertThat(content, not(containsString(PROXY_USER_NAME)));
        assertThat(content, not(containsString(PROXY_NO_PROXY_HOST)));
    }
}
