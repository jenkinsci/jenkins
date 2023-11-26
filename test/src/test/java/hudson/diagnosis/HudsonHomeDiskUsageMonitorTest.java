package hudson.diagnosis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import hudson.model.User;
import hudson.security.GlobalMatrixAuthorizationStrategy;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.List;
import jenkins.model.Jenkins;
import org.htmlunit.ElementNotFoundException;
import org.htmlunit.HttpMethod;
import org.htmlunit.Page;
import org.htmlunit.WebRequest;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.util.NameValuePair;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.xml.sax.SAXException;

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
        assertThrows(ElementNotFoundException.class, () -> getForm(mon));
    }

    @Issue("SECURITY-371")
    @Test
    public void noAccessForNonAdmin() throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient()
                .withThrowExceptionOnFailingStatusCode(false);

        // TODO: Use MockAuthorizationStrategy in later versions
        JenkinsRule.DummySecurityRealm realm = j.createDummySecurityRealm();
        realm.addGroups("administrator", "admins");
        realm.addGroups("bob", "users");
        j.jenkins.setSecurityRealm(realm);
        GlobalMatrixAuthorizationStrategy auth = new GlobalMatrixAuthorizationStrategy();
        auth.add(Jenkins.ADMINISTER, "admins");
        auth.add(Jenkins.READ, "users");
        j.jenkins.setAuthorizationStrategy(auth);

        User bob = User.getById("bob", true);
        User administrator = User.getById("administrator", true);

        WebRequest request = new WebRequest(new URI(wc.getContextPath() + "administrativeMonitor/hudsonHomeIsFull/act").toURL(), HttpMethod.POST);
        NameValuePair param = new NameValuePair("no", "true");
        request.setRequestParameters(List.of(param));

        HudsonHomeDiskUsageMonitor mon = HudsonHomeDiskUsageMonitor.get();

        wc.withBasicApiToken(bob);
        Page p = wc.getPage(request);
        assertEquals(HttpURLConnection.HTTP_FORBIDDEN, p.getWebResponse().getStatusCode());

        assertTrue(mon.isEnabled());

        WebRequest requestReadOnly = new WebRequest(new URI(wc.getContextPath() + "administrativeMonitor/hudsonHomeIsFull").toURL(), HttpMethod.GET);
        p = wc.getPage(requestReadOnly);
        assertEquals(HttpURLConnection.HTTP_FORBIDDEN, p.getWebResponse().getStatusCode());

        wc.withBasicApiToken(administrator);
        request = new WebRequest(new URI(wc.getContextPath() + "administrativeMonitor/hudsonHomeIsFull/act").toURL(), HttpMethod.POST);
        request.setRequestParameters(List.of(param));
        p = wc.getPage(request);
        assertEquals(HttpURLConnection.HTTP_OK, p.getWebResponse().getStatusCode());
        assertFalse(mon.isEnabled());
    }

    /**
     * Gets the warning form.
     */
    private HtmlForm getForm(HudsonHomeDiskUsageMonitor mon) throws IOException, SAXException {
        HtmlPage p = j.createWebClient().goTo("manage");
        return p.getFormByName(mon.id);
    }
}
