package jenkins.security;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;

import hudson.model.UnprotectedRootAction;
import java.util.concurrent.atomic.AtomicInteger;
import org.htmlunit.AlertHandler;
import org.htmlunit.ScriptResult;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class Security2779Test {
    private static final String URL_NAME = "security2779";

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void noXssInHelpLinkPanel() throws Exception {
        noCrossSiteScriptingInHelp("#link-panel a");
    }

    @Test
    void noXssInHelpIconPanel() throws Exception {
        noCrossSiteScriptingInHelp("#icon-panel svg");
    }

    private void noCrossSiteScriptingInHelp(String selector) throws Exception {
        final AtomicInteger alerts = new AtomicInteger();
        final JenkinsRule.WebClient webClient = j.createWebClient();
        webClient.setAlertHandler((AlertHandler) (p, s) -> alerts.addAndGet(1));
        final HtmlPage page = webClient.goTo(URL_NAME);
        page.executeJavaScript("document.querySelector('" + selector + "')._tippy.show()");
        webClient.waitForBackgroundJavaScript(2000);
        // Assertion includes the selector for easier diagnosis
        assertEquals(0, alerts.get(), "Alert with selector '" + selector + "'");

        final ScriptResult innerHtmlScript = page.executeJavaScript("document.querySelector('.tippy-content').innerHTML");
        Object jsResult = innerHtmlScript.getJavaScriptResult();
        assertThat(jsResult, instanceOf(String.class));
        String jsResultString = (String) jsResult;

        // assert leading space to identify unintentional double-escaping (&amp;lt;) as test failure
        assertThat("tooltip does not contain dangerous HTML", jsResultString, not(containsString(" <img src=x")));
        assertThat("tooltip contains safe text", jsResultString, containsString(" &lt;img src=x"));
    }

    @TestExtension
    public static class ViewHolder implements UnprotectedRootAction {

        @Override
        public String getIconFileName() {
            return null;
        }

        @Override
        public String getDisplayName() {
            return null;
        }

        @Override
        public String getUrlName() {
            return URL_NAME;
        }

        public String getFeatureName() {
            return " <img src=x onerror=alert(1)>";
        }
    }
}
