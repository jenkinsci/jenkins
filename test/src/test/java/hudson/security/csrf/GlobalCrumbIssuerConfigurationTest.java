package hudson.security.csrf;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import jakarta.servlet.ServletRequest;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class GlobalCrumbIssuerConfigurationTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void csrfSectionHiddenWhenOnlyDefaultIssuerAvailable() throws Exception {
        // No @TestExtension here, so only DefaultCrumbIssuer is registered
        j.jenkins.setCrumbIssuer(new DefaultCrumbIssuer(false));

        JenkinsRule.WebClient wc = j.createWebClient();
        HtmlPage page = wc.goTo("configureSecurity");
        String pageContent = page.asNormalizedText();

        // When only DefaultCrumbIssuer exists, the CSRF section should be HIDDEN
        assertThat(pageContent, not(containsString("Dummy Crumb Issuer")));
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
        assertThat(pageContent, containsString("Crumb Issuer"));
    }

    @Test
    void csrfSectionShownWhenCsrfProtectionDisabled() throws Exception {
        boolean original = GlobalCrumbIssuerConfiguration.DISABLE_CSRF_PROTECTION;
        try {
            GlobalCrumbIssuerConfiguration.DISABLE_CSRF_PROTECTION = true;

            JenkinsRule.WebClient wc = j.createWebClient();
            HtmlPage page = wc.goTo("configureSecurity");
            String pageContent = page.asNormalizedText();

            assertThat(pageContent, containsString("This configuration is unavailable because"));
        } finally {
            GlobalCrumbIssuerConfiguration.DISABLE_CSRF_PROTECTION = original;
        }
    }

    @TestExtension("csrfSectionShownWhenNonDefaultIssuerConfigured")
    public static class DummyCrumbIssuer extends CrumbIssuer {

        @Override
        public String getCrumbRequestField() {
            return "dummy";
        }

        @Override
        public String issueCrumb(ServletRequest request, String salt) {
            return "dummy-crumb";
        }

        public static class DescriptorImpl extends CrumbIssuerDescriptor<DummyCrumbIssuer> {

            DescriptorImpl() {
                super(
                    DummyCrumbIssuer.class.getName(),
                    "Dummy Crumb Issuer"
                );
            }

            @Override
            public String getDisplayName() {
                return "Dummy Crumb Issuer";
            }
        }
    }
}
