package jenkins.security;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.gargoylesoftware.htmlunit.AlertHandler;
import com.gargoylesoftware.htmlunit.ScriptResult;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.model.UnprotectedRootAction;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;

@RunWith(Parameterized.class)
public class Security2779Test {
    public static final String URL_NAME = "security2779";
    private final String selector;
    @Rule
    public JenkinsRule j = new JenkinsRule();

    // Dynamic tests are not fully supported by the maven surefire plugin
    // Failures of one parameter are treated as "flakes" of the named test
    // instead of being treated as failures.
    // Test output includes the test number and argument to make it a little easier to diagnose
    @Parameterized.Parameters(name = "test:{index}  arg:\"{0}\"")
    public static Collection<String> getSelectors() {
        return Arrays.asList("#link-panel a", "#icon-panel svg");
    }

    public Security2779Test(String selector) {
        this.selector = selector;
    }

    @Test
    public void noXssInHelp() throws Exception {
        final AtomicInteger alerts = new AtomicInteger();
        final JenkinsRule.WebClient webClient = j.createWebClient();
        webClient.setAlertHandler((AlertHandler) (p, s) -> alerts.addAndGet(1));
        final HtmlPage page = webClient.goTo(URL_NAME);
        final ScriptResult eventScript = page.executeJavaScript("document.querySelector('" + selector + "').dispatchEvent(new Event('mouseover'))");
        final Object eventResult = eventScript.getJavaScriptResult();
        assertThat(eventResult, instanceOf(boolean.class));
        Assert.assertTrue((boolean) eventResult);
        webClient.waitForBackgroundJavaScript(2000);
        // Assertion includes the selector for easier diagnosis
        Assert.assertEquals("Alert with selector '" + selector + "'", 0, alerts.get());

        final ScriptResult innerHtmlScript = page.executeJavaScript("document.querySelector('#tt').innerHTML");
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
