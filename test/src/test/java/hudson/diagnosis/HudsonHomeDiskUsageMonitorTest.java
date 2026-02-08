package hudson.diagnosis;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.User;
import hudson.security.ACL;
import hudson.security.ACLContext;
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
import org.jenkinsci.plugins.matrixauth.AuthorizationType;
import org.jenkinsci.plugins.matrixauth.PermissionEntry;
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

        JenkinsRule.DummySecurityRealm realm = j.createDummySecurityRealm();
        realm.addGroups("administrator", "admins");
        j.jenkins.setSecurityRealm(realm);
        GlobalMatrixAuthorizationStrategy auth = new GlobalMatrixAuthorizationStrategy();
        auth.add(Jenkins.ADMINISTER, new PermissionEntry(AuthorizationType.GROUP, "admins"));
        User administrator = User.getById("administrator", true);
        j.submit(getForm(mon, administrator), "yes");

        // clicking yes should take us to somewhere
        try (ACLContext c = ACL.as(administrator)) {
            assertTrue(mon.isEnabled());
        }

        // now dismiss
        j.submit(getForm(mon, administrator),"no");
        try (ACLContext c = ACL.as(administrator)) {
            assertFalse(mon.isEnabled());
        }

        // and make sure it's gone
        assertThrows(ElementNotFoundException.class, () -> getForm(mon, administrator));
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
        auth.add(Jenkins.ADMINISTER, new PermissionEntry(AuthorizationType.GROUP, "admins"));
        auth.add(Jenkins.READ, new PermissionEntry(AuthorizationType.GROUP, "users"));
        j.jenkins.setAuthorizationStrategy(auth);

        User bob = User.getById("bob", true);
        User administrator = User.getById("administrator", true);

        WebRequest request = new WebRequest(new URI(wc.getContextPath() + "administrativeMonitor/hudsonHomeIsFull/act").toURL(), HttpMethod.POST);
        NameValuePair param = new NameValuePair("no", "true");
        request.setRequestParameters(List.of(param));

        HudsonHomeDiskUsageMonitor mon = HudsonHomeDiskUsageMonitor.get();
        mon.activated = true;

        wc.withBasicApiToken(bob);
        Page p = wc.getPage(request);
        assertEquals(HttpURLConnection.HTTP_FORBIDDEN, p.getWebResponse().getStatusCode());

        assertTrue(mon.isEnabled());

        try (ACLContext c = ACL.as(administrator)) {
            assertTrue(mon.isEnabled());
        }

        WebRequest requestReadOnly = new WebRequest(new URI(wc.getContextPath() + "administrativeMonitor/hudsonHomeIsFull").toURL(), HttpMethod.GET);
        p = wc.getPage(requestReadOnly);
        assertEquals(HttpURLConnection.HTTP_FORBIDDEN, p.getWebResponse().getStatusCode());

        wc.withBasicApiToken(administrator);
        request = new WebRequest(new URI(wc.getContextPath() + "administrativeMonitor/hudsonHomeIsFull/act").toURL(), HttpMethod.POST);
        request.setRequestParameters(List.of(param));
        p = wc.getPage(request);
        assertEquals(HttpURLConnection.HTTP_OK, p.getWebResponse().getStatusCode());
        try (ACLContext c = ACL.as(administrator)) {
            assertFalse(mon.isEnabled());
        }
        assertThrows(ElementNotFoundException.class, () -> getForm(mon, administrator));
    }

    @Test
    void dismissIsUserSpecific() throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient()
                .withThrowExceptionOnFailingStatusCode(false);

        // TODO: Use MockAuthorizationStrategy in later versions
        JenkinsRule.DummySecurityRealm realm = j.createDummySecurityRealm();
        realm.addGroups("administrator", "admins");
        realm.addGroups("bob", "admins");
        j.jenkins.setSecurityRealm(realm);
        GlobalMatrixAuthorizationStrategy auth = new GlobalMatrixAuthorizationStrategy();
        auth.add(Jenkins.ADMINISTER, new PermissionEntry(AuthorizationType.GROUP, "admins"));
        j.jenkins.setAuthorizationStrategy(auth);

        User bob = User.getById("bob", true);
        User administrator = User.getById("administrator", true);

        WebRequest request = new WebRequest(new URI(wc.getContextPath() + "administrativeMonitor/hudsonHomeIsFull/act").toURL(), HttpMethod.POST);
        NameValuePair param = new NameValuePair("no", "true");
        request.setRequestParameters(List.of(param));

        HudsonHomeDiskUsageMonitor mon = HudsonHomeDiskUsageMonitor.get();
        mon.activated = true;

        wc.withBasicApiToken(bob);
        Page p = wc.getPage(request);
        assertEquals(HttpURLConnection.HTTP_OK, p.getWebResponse().getStatusCode());

        try (ACLContext c = ACL.as(bob)) {
            assertFalse(mon.isEnabled());
        }

        assertThrows(ElementNotFoundException.class, () -> getForm(mon, bob));

        try (ACLContext c = ACL.as(administrator)) {
            assertTrue(mon.isEnabled());
        }
        assertDoesNotThrow(() -> getForm(mon, administrator));
    }

    /**
     * Gets the warning form.
     */
    private HtmlForm getForm(HudsonHomeDiskUsageMonitor mon, User user) throws IOException, SAXException {
        HtmlPage p = j.createWebClient().withBasicApiToken(user).goTo("manage");
        return p.getFormByName(mon.id);
    }
}
