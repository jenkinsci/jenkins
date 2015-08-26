package hudson;

import com.gargoylesoftware.htmlunit.ScriptException;
import com.gargoylesoftware.htmlunit.WebClientUtil;
import org.junit.Assert;
import org.jvnet.hudson.test.HudsonTestCase;

/**
 * @author Kohsuke Kawaguchi
 */
public class ExceptionTest extends HudsonTestCase {
    /**
     * Makes sure that an AJAX handler error results in a fatal problem in the unit test.
     */
    public void testAjaxError() throws Exception {
        WebClient webClient = createWebClient();
        WebClientUtil.ExceptionListener exceptionListener = WebClientUtil.addExceptionListener(webClient);
        webClient.goTo("/self/ajaxError");

        // Check for the error.
        ScriptException e = exceptionListener.getExpectedScriptException();
        Assert.assertTrue(e.getMessage().contains("simulated error"));
    }
}
