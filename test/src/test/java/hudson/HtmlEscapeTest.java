package hudson;

import static org.junit.Assert.assertEquals;

import hudson.model.InvisibleAction;
import hudson.model.RootAction;
import org.htmlunit.html.HtmlPage;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;

/**
 * Tests the HTML escape behaviour.
 *
 * @author Kohsuke Kawaguchi
 */
public class HtmlEscapeTest {

    @Rule public JenkinsRule j = new JenkinsRule();

    @Test
    public void test1() throws Exception {
        HtmlPage p = j.createWebClient().goTo("self/foo");
        // literal HTML in message resources are allowed
        assertEquals("test", p.getElementById("d1").getTextContent());
        // likewise, but the portion that comes from arguments should be escaped
        assertEquals("<b>test</b>", p.getElementById("d2").getTextContent());
        // JEXL evaluation by default gets escaped
        assertEquals("<b>test</b>", p.getElementById("d3").getTextContent());
    }

    @TestExtension
    public static final class RootActionImpl extends InvisibleAction implements RootAction {
        public String getStr() {
            return "<b>test</b>";
        }

        @Override
        public String getUrlName() {
            return "self";
        }
    }
}
