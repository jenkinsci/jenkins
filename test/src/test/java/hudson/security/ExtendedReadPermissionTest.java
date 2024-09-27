package hudson.security;

import static org.junit.Assert.assertNull;

import hudson.model.Item;
import java.net.HttpURLConnection;
import jenkins.model.Jenkins;
import org.htmlunit.html.HtmlButton;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlPage;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;

/**
 *
 * @author dty
 */
public class ExtendedReadPermissionTest {

    @Rule public JenkinsRule r = new JenkinsRule();

    private static boolean enabled;

    @BeforeClass public static void saveEnabled() {
        // TODO potential race condition since other test suites might be running concurrently
        enabled = Item.EXTENDED_READ.getEnabled();
    }

    @AfterClass public static void restoreEnabled() {
        Item.EXTENDED_READ.setEnabled(enabled);
    }

    @Before public void security() throws Exception {
        r.createFreeStyleProject("a");
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        r.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().
            grant(Jenkins.ADMINISTER).everywhere().to("admin").
            grant(Jenkins.READ, Item.READ, Item.CONFIGURE).everywhere().to("alice").
            grant(Jenkins.READ, Item.READ).everywhere().to("bob").
            grant(Jenkins.READ, Item.READ, Item.EXTENDED_READ).everywhere().to("charlie"));
    }

    private void setPermissionEnabled(boolean enabled) {
        Item.EXTENDED_READ.setEnabled(enabled);
    }

    @Test public void readOnlyConfigAccessWithPermissionEnabled() throws Exception {
        setPermissionEnabled(true);

        JenkinsRule.WebClient wc = r.createWebClient();
        wc.withBasicCredentials("charlie");

        HtmlPage page = wc.goTo("job/a/configure");
        HtmlForm form = page.getFormByName("config");
        HtmlButton saveButton = r.getButtonByCaption(form, "Save");
        assertNull(saveButton);
    }

    @Ignore(
            "This was actually testing a design of matrix-auth rather than core: that permissions, though formerly granted, are ignored if currently disabled."
                + " Permission.enabled Javadoc only discusses visibility."
                + " MockAuthorizationStrategy does not implement this check.")
    @Test public void readOnlyConfigAccessWithPermissionDisabled() throws Exception {
        setPermissionEnabled(false);

        JenkinsRule.WebClient wc = r.createWebClient();
        wc.withBasicCredentials("charlie");

        wc.assertFails("job/a/configure", HttpURLConnection.HTTP_FORBIDDEN);
    }

    @Test public void noConfigAccessWithPermissionEnabled() throws Exception {
        setPermissionEnabled(true);

        JenkinsRule.WebClient wc = r.createWebClient();
        wc.withBasicCredentials("bob");

        wc.assertFails("job/a/configure", HttpURLConnection.HTTP_FORBIDDEN);
    }

    // TODO configureLink; viewConfigurationLink; matrixWithPermissionEnabled; matrixWithPermissionDisabled

}
