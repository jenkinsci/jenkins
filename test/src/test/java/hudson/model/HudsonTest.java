package hudson.model;

import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.html.HtmlButton;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.model.Node.Mode;
import hudson.search.SearchTest;
import hudson.security.AuthorizationStrategy;
import hudson.security.SecurityRealm;
import hudson.tasks.Ant;
import hudson.tasks.Ant.AntInstallation;
import org.jvnet.hudson.test.Email;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.recipes.LocalData;

import java.net.URL;
import java.util.List;

/**
 * @author Kohsuke Kawaguchi
 */
public class HudsonTest extends HudsonTestCase {
    /**
     * Performs a very basic round-trip of a non-empty system configuration screen.
     * This makes sure that the structured form submission is working (to some limited extent.)
     */
    @LocalData
    @Email("http://www.nabble.com/Hudson.configure-calling-deprecated-Descriptor.configure-td19051815.html")
    public void testSimpleConfigSubmit() throws Exception {
        // just load the page and resubmit
        HtmlPage configPage = new WebClient().goTo("configure");
        HtmlForm form = configPage.getFormByName("config");
        form.submit((HtmlButton)last(form.getHtmlElementsByTagName("button")));

        // make sure all the pieces are intact
        assertEquals(2,hudson.getNumExecutors());
        assertSame(Mode.NORMAL,hudson.getMode());
        assertSame(SecurityRealm.NO_AUTHENTICATION,hudson.getSecurityRealm());
        assertSame(AuthorizationStrategy.UNSECURED,hudson.getAuthorizationStrategy());
        assertEquals(5,hudson.getQuietPeriod());

        List<JDK> jdks = hudson.getJDKs();
        assertEquals(3,jdks.size()); // Hudson adds one more
        assertJDK(jdks.get(0),"jdk1","/tmp");
        assertJDK(jdks.get(1),"jdk2","/tmp");

        AntInstallation[] ants = Ant.DESCRIPTOR.getInstallations();
        assertEquals(2,ants.length);
        assertAnt(ants[0],"ant1","/tmp");
        assertAnt(ants[1],"ant2","/tmp");
    }

    private void assertAnt(AntInstallation ant, String name, String home) {
        assertEquals(ant.getName(),name);
        assertEquals(ant.getAntHome(),home);
    }

    private void assertJDK(JDK jdk, String name, String home) {
        assertEquals(jdk.getName(),name);
        assertEquals(jdk.getJavaHome(),home);
    }

    /**
     * Makes sure that the search index includes job names.
     *
     * @see SearchTest#testFailure
     *      This test makes sure that a failure will result in an exception
     */
    public void testSearchIndex() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        Page jobPage = search(p.getName());

        URL url = jobPage.getWebResponse().getUrl();
        System.out.println(url);
        assertTrue(url.getPath().endsWith("/job/"+p.getName()+"/"));
    }
}
