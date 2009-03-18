package hudson.diagnosis;

import org.jvnet.hudson.test.HudsonTestCase;
import org.xml.sax.SAXException;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlForm;

import java.io.IOException;

/**
 * @author Kohsuke Kawaguchi
 */
public class HudsonHomeDiskUsageMonitorTest extends HudsonTestCase {
    public void testFlow() throws Exception {
        // manually activate this
        HudsonHomeDiskUsageMonitor mon = HudsonHomeDiskUsageMonitor.get();
        mon.activated = true;

        // clikcing yes should take us to somewhere
        submit(getForm(mon),"yes");

        // TODO: the test doesn't work today because the submit button doesn't send the name in the form.
        // this appears to be a bug in HTMLUnit.
//        // now dismiss
//        submit(getForm(mon),"no");
//
//        // and make sure it's gone
//        assertNull(getForm(mon));
    }

    /**
     * Gets the warning form.
     */
    private HtmlForm getForm(HudsonHomeDiskUsageMonitor mon) throws IOException, SAXException {
        HtmlPage p = new WebClient().goTo("manage");
        return p.getFormByName(mon.id);
    }
}
