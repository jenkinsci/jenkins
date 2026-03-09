package hudson.security.csrf;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class GlobalCrumbIssuerConfigurationWithDummyTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void csrfSectionShownWhenNonDefaultIssuerConfigured() throws Exception {
        // TestCrumbIssuer is registered by Jenkins test harness automatically
        // So CrumbIssuer.all() has more than just DefaultCrumbIssuer and CSRF section shown
        JenkinsRule.WebClient wc = j.createWebClient();
        HtmlPage page = wc.goTo("configureSecurity");
        String pageContent = page.asNormalizedText();

        assertThat(pageContent, containsString("CSRF Protection"));
    }
}
