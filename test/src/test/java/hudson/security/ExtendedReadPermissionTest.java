package hudson.security;

import com.gargoylesoftware.htmlunit.html.HtmlButton;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.model.Item;
import java.net.HttpURLConnection;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.recipes.LocalData;

/**
 *
 * @author dty
 */
public class ExtendedReadPermissionTest extends HudsonTestCase {
    private boolean enabled;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        enabled = Item.EXTENDED_READ.getEnabled();
    }

    @Override
    protected void tearDown() throws Exception {
        Item.EXTENDED_READ.setEnabled(enabled);
        super.tearDown();
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
    public void testReadOnlyConfigAccessWithPermissionEnabled() throws Exception {
        setPermissionEnabled(true);

        AuthorizationStrategy as = jenkins.getAuthorizationStrategy();
        assertTrue("Expecting GlobalMatrixAuthorizationStrategy", (as instanceof GlobalMatrixAuthorizationStrategy));
        GlobalMatrixAuthorizationStrategy gas = (GlobalMatrixAuthorizationStrategy)as;
        assertTrue("Charlie should have extended read for this test", gas.hasExplicitPermission("charlie",Item.EXTENDED_READ));

        WebClient wc = new WebClient().login("charlie","charlie");
        HtmlPage page = wc.goTo("job/a/configure");
        HtmlForm form = page.getFormByName("config");
        HtmlButton saveButton = getButtonByCaption(form,"Save");
        assertNull(saveButton);
    }

    @LocalData
    public void testReadOnlyConfigAccessWithPermissionDisabled() throws Exception {
        setPermissionEnabled(false);
        
        AuthorizationStrategy as = jenkins.getAuthorizationStrategy();
        assertTrue("Expecting GlobalMatrixAuthorizationStrategy", (as instanceof GlobalMatrixAuthorizationStrategy));
        GlobalMatrixAuthorizationStrategy gas = (GlobalMatrixAuthorizationStrategy)as;
        assertFalse("Charlie should not have extended read for this test", gas.hasExplicitPermission("charlie",Item.EXTENDED_READ));

        WebClient wc = new WebClient().login("charlie","charlie");
        wc.assertFails("job/a/configure", HttpURLConnection.HTTP_FORBIDDEN);
    }

    @LocalData
    public void testNoConfigAccessWithPermissionEnabled() throws Exception {
        setPermissionEnabled(true);

        AuthorizationStrategy as = jenkins.getAuthorizationStrategy();
        assertTrue("Expecting GlobalMatrixAuthorizationStrategy", (as instanceof GlobalMatrixAuthorizationStrategy));
        GlobalMatrixAuthorizationStrategy gas = (GlobalMatrixAuthorizationStrategy)as;
        assertFalse("Bob should not have extended read for this test", gas.hasExplicitPermission("bob",Item.EXTENDED_READ));

        WebClient wc = new WebClient().login("bob","bob");
        wc.assertFails("job/a/configure", HttpURLConnection.HTTP_FORBIDDEN);
    }

/*
    @LocalData
    public void testConfigureLink() throws Exception {
        
    }

    @LocalData
    public void testViewConfigurationLink() throws Exception {

    }
    
    @LocalData
    public void testMatrixWithPermissionEnabled() throws Exception {
    }

    @LocalData
    public void testMatrixWithPermissionDisabled() throws Exception {
        
    }
 */
}
