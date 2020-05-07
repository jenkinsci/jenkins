package hudson.diagnosis;

import com.gargoylesoftware.htmlunit.ElementNotFoundException;
import com.gargoylesoftware.htmlunit.html.DomElement;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.model.AdministrativeMonitor;
import hudson.model.Item;
import hudson.model.ListView;
import hudson.model.View;
import java.io.IOException;
import java.net.URL;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import jenkins.model.Jenkins;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
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
        URL url = r.submit(f,"yes").getUrl();
        assertTrue(url.toExternalForm(),url.toExternalForm().endsWith("/newView"));

        // since we didn't create a view, if we go back, we should see the warning again
        p = r.createWebClient().goTo("manage");
        assertNotNull(p.getFormByName(mon.id));

        // once we create a view, the message should disappear
        r.jenkins.addView(new ListView("test"));

        verifyNoForm();
    }
    
    @Test
    public void systemReadNoViewAccessVerifyNoForm() throws Exception {
        final String READONLY = "readonly";

        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        r.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ).everywhere().to(READONLY)
                .grant(Jenkins.SYSTEM_READ).everywhere().to(READONLY)
        );

        for (int i = 0; i <= TooManyJobsButNoView.THRESHOLD; i++)
            r.createFreeStyleProject();

        JenkinsRule.WebClient wc = r.createWebClient();
        wc.login(READONLY);

        verifyNoMonitor(wc);
    }

    private void verifyNoMonitor(JenkinsRule.WebClient wc) throws IOException, SAXException {
        HtmlPage p = wc.goTo("manage");
        DomElement adminMonitorDiv = p.getElementById("tooManyJobsButNoView");
        assertThat(adminMonitorDiv, is(nullValue()));
    }
    
    @Test
    public void systemReadVerifyForm() throws Exception {
        final String READONLY = "readonly";

        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        r.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ).everywhere().to(READONLY)
                .grant(Jenkins.SYSTEM_READ).everywhere().to(READONLY)
                .grant(Item.READ).everywhere().to(READONLY)
                .grant(View.READ).everywhere().to(READONLY)
        );

        for (int i = 0; i <= TooManyJobsButNoView.THRESHOLD; i++)
            r.createFreeStyleProject();

        JenkinsRule.WebClient wc = r.createWebClient();
        wc.login(READONLY);

        verifyMonitor(wc);
    }

    private void verifyMonitor(JenkinsRule.WebClient wc) throws IOException, SAXException {
        HtmlPage p = wc.goTo("manage");
        DomElement adminMonitorDiv = p.getElementById("tooManyJobsButNoView");
        assertThat(adminMonitorDiv, is(notNullValue()));
        assertThat(adminMonitorDiv.getTextContent(), is(notNullValue()));
    }
    
}
