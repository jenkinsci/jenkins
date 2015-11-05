package hudson.diagnosis;

import com.gargoylesoftware.htmlunit.ElementNotFoundException;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.model.AdministrativeMonitor;
import hudson.model.ListView;
import java.io.IOException;
import java.net.URL;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.xml.sax.SAXException;

/**
 * @author Kohsuke Kawaguchi
 */
public class TooManyJobsButNoViewTest {

    @Rule public JenkinsRule r = new JenkinsRule();
    private TooManyJobsButNoView mon;

    @Before public void setUp() throws Exception {
        mon = AdministrativeMonitor.all().get(TooManyJobsButNoView.class);
    }

    /**
     * Shouldn't be active at the beginning
     */
    @Test public void initialState() throws Exception {
        verifyNoForm();
    }

    private void verifyNoForm() throws IOException, SAXException {
        HtmlPage p = r.createWebClient().goTo("manage");
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
    @Test public void activated() throws Exception {
        for( int i=0; i<=TooManyJobsButNoView.THRESHOLD; i++ )
            r.createFreeStyleProject();

        HtmlPage p = r.createWebClient().goTo("manage");
        HtmlForm f = p.getFormByName(mon.id);
        assertNotNull(f);

        // this should take us to the new view page
        URL url = r.submit(f,"yes").getWebResponse().getUrl();
        assertTrue(url.toExternalForm(),url.toExternalForm().endsWith("/newView"));

        // since we didn't create a view, if we go back, we should see the warning again
        p = r.createWebClient().goTo("manage");
        assertNotNull(p.getFormByName(mon.id));

        // once we create a view, the message should disappear
        r.jenkins.addView(new ListView("test"));

        verifyNoForm();
    }
}
