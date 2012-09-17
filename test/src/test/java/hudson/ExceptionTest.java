package hudson;

import com.gargoylesoftware.htmlunit.ScriptException;
import org.jvnet.hudson.test.HudsonTestCase;

/**
 * @author Kohsuke Kawaguchi
 */
public class ExceptionTest extends HudsonTestCase {
    /**
     * Makes sure that an AJAX handler error results in a fatal problem in the unit test.
     */
    public void testAjaxError() throws Exception {
        try {
            createWebClient().goTo("/self/ajaxError");
            fail("should have resulted in a ScriptException");
        } catch (ScriptException e) {
            if (e.getMessage().contains("simulated error"))
                return; // as expected
            throw e;

        }
    }
}
