package hudson;

import com.gargoylesoftware.htmlunit.html.HtmlPage;
import org.jvnet.hudson.test.HudsonTestCase;

/**
 * Tests the HTML escape behaviour.
 *
 * @author Kohsuke Kawaguchi
 */
public class HtmlEscapeTest extends HudsonTestCase {

    public void test1() throws Exception {
        HtmlPage p = createWebClient().goTo("self/foo");
        // literal HTML in message resources are allowed
        assertEquals("test",p.getElementById("d1").getTextContent());
        // likewise, but the portion that comes from arguments should be escaped
        assertEquals("<b>test</b>",p.getElementById("d2").getTextContent());
        // JEXL evaluation by default gets escaped
        assertEquals("<b>test</b>",p.getElementById("d3").getTextContent());
    }

    public String getStr() {
        return "<b>test</b>";
    }
}
