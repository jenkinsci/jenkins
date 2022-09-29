package jenkins.security;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;

import com.gargoylesoftware.htmlunit.ScriptResult;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.Util;
import hudson.model.InvisibleAction;
import hudson.model.UnprotectedRootAction;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.xml.sax.SAXException;

public class Security2776Test {
    public static final String URL_NAME = "security2776";
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void escapedTooltipIsEscaped() throws Exception {
        assertExpectedBehaviorForTooltip("#symbol-icons .unsafe svg");
        assertExpectedBehaviorForTooltip("#symbol-icons .safe svg");
        assertExpectedBehaviorForTooltip("#png-icons .unsafe img");
        assertExpectedBehaviorForTooltip("#png-icons .safe img");

        // Outlier after the fix for SECURITY-1955
        assertExpectedBehaviorForTooltip("#svgIcons .unsafe svg");
        assertExpectedBehaviorForTooltip("#svgIcons .safe svg");
    }

    private void assertExpectedBehaviorForTooltip(String selector) throws IOException, SAXException {
        final AtomicBoolean alerts = new AtomicBoolean();
        final JenkinsRule.WebClient wc = j.createWebClient();
        wc.setAlertHandler((p, s) -> alerts.set(true));
        final HtmlPage page = wc.goTo(URL_NAME);
        page.executeJavaScript("document.querySelector('" + selector + "')._tippy.show()");
        wc.waitForBackgroundJavaScript(2000L);
        ScriptResult result = page.executeJavaScript("document.querySelector('.tippy-content').innerHTML;");
        Object jsResult = result.getJavaScriptResult();
        assertThat(jsResult, instanceOf(String.class));
        String jsResultString = (String) jsResult;
        assertThat(jsResultString, containsString(_getSafeTooltip()));
        Assert.assertFalse("No alert expected", alerts.get());
    }

    private static String _getUnsafeTooltip() {
        return "<img src=\"x\" onerror=\"alert(1)\">";
    }

    private static String _getSafeTooltip() {
        return Util.xmlEscape(_getUnsafeTooltip());
    }

    @TestExtension
    public static class ViewHolder extends InvisibleAction implements UnprotectedRootAction {
        @Override
        public String getUrlName() {
            return URL_NAME;
        }

        public String getUnsafeTooltip() {
            return _getUnsafeTooltip();
        }

        public String getSafeTooltip() {
            return _getSafeTooltip();
        }
    }
}
