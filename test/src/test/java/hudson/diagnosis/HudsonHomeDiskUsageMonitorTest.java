package hudson.diagnosis;

import org.jvnet.hudson.test.HudsonTestCase;
import org.xml.sax.SAXException;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.ElementNotFoundException;

import java.io.IOException;

/**
 * @author Kohsuke Kawaguchi
 */
public class HudsonHomeDiskUsageMonitorTest extends HudsonTestCase {
    public void testFlow() throws Exception {
        // manually activate this
        HudsonHomeDiskUsageMonitor mon = HudsonHomeDiskUsageMonitor.get();
        mon.activated = true;

        // clicking yes should take us to somewhere
        submit(getForm(mon),"yes");
        assertTrue(mon.isEnabled());

        // now dismiss
        // submit(getForm(mon),"no"); TODO: figure out why this test is fragile
        mon.doAct("no");
        assertFalse(mon.isEnabled());

        // and make sure it's gone
        try {
            fail(getForm(mon)+" shouldn't be there");
        } catch (ElementNotFoundException e) {
            // as expected
        }
    }

    /**
     * Gets the warning form.
     */
    private HtmlForm getForm(HudsonHomeDiskUsageMonitor mon) throws IOException, SAXException {
        HtmlPage p = new WebClient().goTo("manage");
        return p.getFormByName(mon.id);
    }
}
