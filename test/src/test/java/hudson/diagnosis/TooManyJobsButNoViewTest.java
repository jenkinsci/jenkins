package hudson.diagnosis;

import org.jvnet.hudson.test.HudsonTestCase;
import org.xml.sax.SAXException;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.ElementNotFoundException;
import hudson.model.AdministrativeMonitor;
import hudson.model.ListView;

import java.net.URL;
import java.io.IOException;

/**
 * @author Kohsuke Kawaguchi
 */
public class TooManyJobsButNoViewTest extends HudsonTestCase {
    private TooManyJobsButNoView mon;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mon = AdministrativeMonitor.all().get(TooManyJobsButNoView.class);
    }

    /**
     * Shouldn't be active at the beginning
     */
    public void testInitialState() throws Exception {
        verifyNoForm();
    }

    private void verifyNoForm() throws IOException, SAXException {
        HtmlPage p = new WebClient().goTo("manage");
        try {
            p.getFormByName(mon.id);
            fail();
        } catch (ElementNotFoundException e) {
            // shouldn't find it
        }
    }

    /**
     * Once we have enough jobs, it should kick in
     */
    public void testActivated() throws Exception {
        for( int i=0; i<=TooManyJobsButNoView.THRESHOLD; i++ )
            createFreeStyleProject();

        HtmlPage p = new WebClient().goTo("manage");
        HtmlForm f = p.getFormByName(mon.id);
        assertNotNull(f);

        // this should take us to the new view page
        URL url = submit(f,"yes").getWebResponse().getUrl();
        assertTrue(url.toExternalForm(),url.toExternalForm().endsWith("/newView"));

        // since we didn't create a view, if we go back, we should see the warning again
        p = new WebClient().goTo("manage");
        assertNotNull(p.getFormByName(mon.id));

        // once we create a view, the message should disappear
        hudson.addView(new ListView("test"));

        verifyNoForm();
    }
}
