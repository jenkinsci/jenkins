package hudson.security.csrf;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import hudson.ExtensionList;
import hudson.model.Descriptor;
import java.util.ArrayList;
import java.util.List;
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
    void csrfSectionHiddenWhenOnlyDefaultIssuerAvailable() throws Exception {
        // Remove all non-default CrumbIssuer descriptors
        ExtensionList<Descriptor<CrumbIssuer>> descriptors = j.jenkins.getDescriptorList(CrumbIssuer.class);
        List<Descriptor<CrumbIssuer>> toRemove = new ArrayList<>();
        for (Descriptor<CrumbIssuer> d : descriptors) {
            if (!(d instanceof DefaultCrumbIssuer.DescriptorImpl)) {
                toRemove.add(d);
            }
        }
        descriptors.removeAll(toRemove);

        j.jenkins.setCrumbIssuer(new DefaultCrumbIssuer(false));

        JenkinsRule.WebClient wc = j.createWebClient();
        HtmlPage page = wc.goTo("configureSecurity");
        String pageContent = page.asNormalizedText();

        assertThat(pageContent, not(containsString("CSRF Protection")));
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
}
