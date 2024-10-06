package jenkins.security;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;

import hudson.model.UnprotectedRootAction;
import java.util.concurrent.atomic.AtomicInteger;
import org.htmlunit.AlertHandler;
import org.htmlunit.ScriptResult;
import org.htmlunit.html.HtmlPage;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;

public class Security2779Test {
    public static final String URL_NAME = "security2779";

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void noXssInHelpLinkPanel() throws Exception {
        noCrossSiteScriptingInHelp("#link-panel a");
    }

    @Test
    public void noXssInHelpIconPanel() throws Exception {
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
        Assert.assertEquals("Alert with selector '" + selector + "'", 0, alerts.get());

        final ScriptResult innerHtmlScript = page.executeJavaScript("document.querySelector('.tippy-content').innerHTML");
        Object jsResult = innerHtmlScript.getJavaScriptResult();
        assertThat(jsResult, instanceOf(String.class));
        String jsResultString = (String) jsResult;

        // assert leading space to identify unintentional double-escaping (&amp;lt;) as test failure
        assertThat("tooltip does not contain dangerous HTML", jsResultString, not(containsString(" <img src=x")));
        assertThat("tooltip contains safe text", jsResultString, containsString("lt;img src=x"));
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
