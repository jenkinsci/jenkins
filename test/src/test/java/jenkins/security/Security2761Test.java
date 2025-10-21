package jenkins.security;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertFalse;

import hudson.model.InvisibleAction;
import hudson.model.UnprotectedRootAction;
import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class Security2761Test {
    private static final String ACTION_URL = "security2761";

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Issue("SECURITY-2761")
    @Test
    void symbolIconAltIsEscaped() throws Exception {
        final AtomicBoolean alerted = new AtomicBoolean(false);

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.setAlertHandler((page, s) -> alerted.set(true));
        HtmlPage page = wc.getPage(new URI(wc.getContextPath() + ACTION_URL).toURL());
        String responseContent = page.getWebResponse().getContentAsString();
        wc.waitForBackgroundJavaScript(5000);

        assertThat(responseContent, not(containsString("<img src=x")));
        assertThat(responseContent, containsString("<span class=\"jenkins-visually-hidden\">&lt;img src=x"));
        assertFalse(alerted.get(), "no alert expected");
    }

    @TestExtension
    public static class ViewHolder extends InvisibleAction implements UnprotectedRootAction {
        @Override
        public String getUrlName() {
            return ACTION_URL;
        }

        public String getTitle() {
            return "<img src=x onerror=alert(1)>";
        }
    }
}
