package jenkins.security;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

import hudson.Functions;
import hudson.model.InvisibleAction;
import hudson.model.UnprotectedRootAction;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.htmlunit.ScriptResult;
import org.htmlunit.html.HtmlPage;
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
        assertExpectedBehaviorForTooltip("#symbol-icons .unsafe svg",
                "&lt;img src=\"x\" onerror=\"alert(1)\"&gt;");
        assertExpectedBehaviorForTooltip("#symbol-icons .safe svg",
                Functions.htmlAttributeEscape(_getSafeTooltip()));
        assertExpectedBehaviorForTooltip("#png-icons .unsafe img",
                "&lt;img src=\"x\" onerror=\"alert(1)\"&gt;");
        assertExpectedBehaviorForTooltip("#png-icons .safe img",
                Functions.htmlAttributeEscape(_getSafeTooltip()));

        // Outlier after the fix for SECURITY-1955
        assertExpectedBehaviorForTooltip("#svgIcons .unsafe svg",
                "&lt;img src=\"x\" onerror=\"alert(1)\"&gt;");
        assertExpectedBehaviorForTooltip("#svgIcons .safe svg",
                "&amp;lt;img src=&amp;quot;x&amp;quot; onerror=&amp;quot;alert(1)&amp;quot;&amp;gt;");
    }

    private void assertExpectedBehaviorForTooltip(String selector, String expectedResult) throws IOException, SAXException {
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
        assertThat(jsResultString, is(expectedResult));
        Assert.assertFalse("No alert expected", alerts.get());
    }

    private static String _getUnsafeTooltip() {
        return "<img src=\"x\" onerror=\"alert(1)\">";
    }

    private static String _getSafeTooltip() {
        return Functions.htmlAttributeEscape(_getUnsafeTooltip());
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
