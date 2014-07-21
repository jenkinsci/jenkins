package hudson.security;

import com.gargoylesoftware.htmlunit.html.HtmlButton;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.model.Item;
import java.net.HttpURLConnection;
import org.junit.AfterClass;
import static org.junit.Assert.*;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;

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

    /**
     * alice: Job/Configure+Read
     * bob: Job/Read
     * charlie: Job/ExtendedRead+Read
     */

    private void setPermissionEnabled(boolean enabled) throws Exception {
        Item.EXTENDED_READ.setEnabled(enabled);
    }


    @LocalData
    @Test public void readOnlyConfigAccessWithPermissionEnabled() throws Exception {
        setPermissionEnabled(true);

        AuthorizationStrategy as = r.jenkins.getAuthorizationStrategy();
        assertTrue("Expecting GlobalMatrixAuthorizationStrategy", (as instanceof GlobalMatrixAuthorizationStrategy));
        GlobalMatrixAuthorizationStrategy gas = (GlobalMatrixAuthorizationStrategy)as;
        assertTrue("Charlie should have extended read for this test", gas.hasExplicitPermission("charlie",Item.EXTENDED_READ));

        JenkinsRule.WebClient wc = r.createWebClient().login("charlie","charlie");
        HtmlPage page = wc.goTo("job/a/configure");
        HtmlForm form = page.getFormByName("config");
        HtmlButton saveButton = r.getButtonByCaption(form,"Save");
        assertNull(saveButton);
    }

    @LocalData
    @Test public void readOnlyConfigAccessWithPermissionDisabled() throws Exception {
        setPermissionEnabled(false);
        
        AuthorizationStrategy as = r.jenkins.getAuthorizationStrategy();
        assertTrue("Expecting GlobalMatrixAuthorizationStrategy", (as instanceof GlobalMatrixAuthorizationStrategy));
        GlobalMatrixAuthorizationStrategy gas = (GlobalMatrixAuthorizationStrategy)as;
        assertFalse("Charlie should not have extended read for this test", gas.hasExplicitPermission("charlie",Item.EXTENDED_READ));

        JenkinsRule.WebClient wc = r.createWebClient().login("charlie","charlie");
        wc.assertFails("job/a/configure", HttpURLConnection.HTTP_FORBIDDEN);
    }

    @LocalData
    @Test public void noConfigAccessWithPermissionEnabled() throws Exception {
        setPermissionEnabled(true);

        AuthorizationStrategy as = r.jenkins.getAuthorizationStrategy();
        assertTrue("Expecting GlobalMatrixAuthorizationStrategy", (as instanceof GlobalMatrixAuthorizationStrategy));
        GlobalMatrixAuthorizationStrategy gas = (GlobalMatrixAuthorizationStrategy)as;
        assertFalse("Bob should not have extended read for this test", gas.hasExplicitPermission("bob",Item.EXTENDED_READ));

        JenkinsRule.WebClient wc = r.createWebClient().login("bob","bob");
        wc.assertFails("job/a/configure", HttpURLConnection.HTTP_FORBIDDEN);
    }

    // TODO configureLink; viewConfigurationLink; matrixWithPermissionEnabled; matrixWithPermissionDisabled

}
