package hudson.diagnosis;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.Item;
import hudson.model.ListView;
import hudson.model.View;
import java.io.IOException;
import java.net.URL;
import jenkins.model.Jenkins;
import org.htmlunit.html.DomElement;
import org.htmlunit.html.HtmlAnchor;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.xml.sax.SAXException;

/**
 * @author Kohsuke Kawaguchi
 */
@WithJenkins
class TooManyJobsButNoViewTest {

    private TooManyJobsButNoView mon;

    private JenkinsRule r;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        r = rule;
    }

    /**
     * Shouldn't be active at the beginning
     */
    @Test
    void initialState() throws Exception {
        try (JenkinsRule.WebClient wc = r.createWebClient()) {
            verifyNoMonitor(wc);
        }
    }

    /**
     * Once we have enough jobs, it should kick in
     */
    @Test
    void activated() throws Exception {
        for (int i = 0; i <= TooManyJobsButNoView.THRESHOLD; i++)
            r.createFreeStyleProject();

        try (JenkinsRule.WebClient wc = r.createWebClient()) {
            HtmlPage p = wc.goTo("manage");
            HtmlAnchor link = p.querySelector("div[data-monitor-id=\"hudson.diagnosis.TooManyJobsButNoView\"] a");
            assertNotNull(link);

            // this should take us to the new view page
            URL url = link.click().getUrl();
            assertTrue(url.toExternalForm().endsWith("/newView"), url.toExternalForm());

            // since we didn't create a view, if we go back, we should see the warning again
            verifyMonitor(wc);

            // once we create a view, the message should disappear
            r.jenkins.addView(new ListView("test"));

            verifyNoMonitor(wc);
        }
    }

    @Test
    void systemReadNoViewAccessVerifyNoForm() throws Exception {
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
        DomElement adminMonitorDiv = p.querySelector("div[data-monitor-id=\"hudson.diagnosis.TooManyJobsButNoView\"]");
        assertThat(adminMonitorDiv, is(nullValue()));
    }

    @Test
    void systemReadVerifyForm() throws Exception {
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
        DomElement adminMonitorDiv = p.querySelector("div[data-monitor-id=\"hudson.diagnosis.TooManyJobsButNoView\"]");
        assertThat(adminMonitorDiv, is(notNullValue()));
        assertThat(adminMonitorDiv.getTextContent(), is(notNullValue()));
    }
}
