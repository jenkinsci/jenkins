package hudson;

import com.gargoylesoftware.htmlunit.ScriptException;
import com.gargoylesoftware.htmlunit.WebClientUtil;
import hudson.model.InvisibleAction;
import hudson.model.RootAction;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.jvnet.hudson.test.TestExtension;

/**
 * @author Kohsuke Kawaguchi
 */
public class ExceptionTest {

    @Rule public JenkinsRule j = new JenkinsRule();

    /**
     * Makes sure that an AJAX handler error results in a fatal problem in the unit test.
     */
    @Test
    public void testAjaxError() throws Exception {
        WebClient webClient = j.createWebClient();
        WebClientUtil.ExceptionListener exceptionListener = WebClientUtil.addExceptionListener(webClient);
        webClient.goTo("self/ajaxError");

        // Check for the error.
        ScriptException e = exceptionListener.getExpectedScriptException();
        Assert.assertTrue(e.getMessage().contains("simulated error"));
    }

    @TestExtension
    public static final class RootActionImpl extends InvisibleAction implements RootAction {
        @Override
        public String getUrlName() {
            return "self";
        }
    }
}
