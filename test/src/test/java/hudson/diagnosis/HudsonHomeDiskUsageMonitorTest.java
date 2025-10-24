package hudson.diagnosis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.ExtensionList;
import hudson.model.User;
import hudson.security.GlobalMatrixAuthorizationStrategy;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import jenkins.model.Jenkins;
import org.htmlunit.ElementNotFoundException;
import org.htmlunit.HttpMethod;
import org.htmlunit.Page;
import org.htmlunit.WebRequest;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.xml.sax.SAXException;

/**
 * @author Kohsuke Kawaguchi
 */
@WithJenkins
class HudsonHomeDiskUsageMonitorTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void flow() throws Exception {
        // manually activate this
        HudsonHomeDiskUsageMonitor mon = HudsonHomeDiskUsageMonitor.get();
        mon.activated = true;

        // clicking yes should take us to somewhere
        j.getButtonByCaption(getForm(mon), "Tell me more").click();
        assertTrue(mon.isEnabled());

        // now dismiss
        j.getButtonByCaption(getForm(mon), "Dismiss").click();
        assertFalse(mon.isEnabled());

        // and make sure it's gone
        assertThrows(ElementNotFoundException.class, () -> getForm(mon));
    }

    @Issue("SECURITY-371")
    @Test
    void noAccessForNonAdmin() throws Exception {
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
        var monitorUrl = wc.getContextPath() + ExtensionList.lookupSingleton(HudsonHomeDiskUsageMonitor.class).getUrl();
        WebRequest request = new WebRequest(new URI(monitorUrl + "/disable").toURL(), HttpMethod.POST);
        HudsonHomeDiskUsageMonitor mon = HudsonHomeDiskUsageMonitor.get();

        wc.withBasicApiToken(bob);
        Page p = wc.getPage(request);
        assertEquals(HttpURLConnection.HTTP_FORBIDDEN, p.getWebResponse().getStatusCode());

        assertTrue(mon.isEnabled());

        WebRequest requestReadOnly = new WebRequest(new URI(monitorUrl).toURL(), HttpMethod.GET);
        p = wc.getPage(requestReadOnly);
        assertEquals(HttpURLConnection.HTTP_FORBIDDEN, p.getWebResponse().getStatusCode());

        wc.withBasicApiToken(administrator);
        request = new WebRequest(new URI(monitorUrl + "/disable").toURL(), HttpMethod.POST);
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
