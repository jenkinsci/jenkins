package hudson.diagnosis;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.xml.sax.SAXException;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.ElementNotFoundException;

import java.io.IOException;

/**
 * @author Kohsuke Kawaguchi
 */
public class HudsonHomeDiskUsageMonitorTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void flow() throws Exception {
        // manually activate this
        HudsonHomeDiskUsageMonitor mon = HudsonHomeDiskUsageMonitor.get();
        mon.activated = true;

        // clicking yes should take us to somewhere
        j.submit(getForm(mon), "yes");
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
        HtmlPage p = j.createWebClient().goTo("manage");
        return p.getFormByName(mon.id);
    }
}
