package hudson.security.csrf;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class GlobalCrumbIssuerConfigurationTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void csrfSectionShownWhenNonDefaultIssuerConfigured() throws Exception {
        // DefaultCrumbIssuer is default, but other CrumbIssuer descriptors exist in test environment
        // so the CSRF section should be visible
        j.jenkins.setCrumbIssuer(new DefaultCrumbIssuer(false));

        JenkinsRule.WebClient wc = j.createWebClient();
        HtmlPage page = wc.goTo("configureSecurity");
        String pageContent = page.asNormalizedText();

        // With multiple CrumbIssuer descriptors available (from test extensions),
        // the CSRF Protection section should always be shown
        assertThat("CSRF Protection section should be shown when multiple issuers are available",
                   pageContent, containsString("CSRF Protection"));
    }

    @Test
    void csrfSectionShownWhenCsrfProtectionDisabled() throws Exception {
        boolean original = GlobalCrumbIssuerConfiguration.DISABLE_CSRF_PROTECTION;
        try {
            GlobalCrumbIssuerConfiguration.DISABLE_CSRF_PROTECTION = true;

            JenkinsRule.WebClient wc = j.createWebClient();
            HtmlPage page = wc.goTo("configureSecurity");
            String pageContent = page.asNormalizedText();

            assertThat("CSRF section should be shown when CSRF protection is disabled",
                       pageContent, containsString("CSRF Protection"));
        } finally {
            GlobalCrumbIssuerConfiguration.DISABLE_CSRF_PROTECTION = original;
        }
    }
}