/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Yahoo! Inc., CloudBees, Inc.
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.model;

import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebRequestSettings;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.model.Node.Mode;
import hudson.search.SearchTest;
import hudson.security.AuthorizationStrategy;
import hudson.security.SecurityRealm;
import hudson.tasks.Ant;
import hudson.tasks.BuildStep;
import hudson.tasks.Ant.AntInstallation;
import jenkins.model.Jenkins;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.Email;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.recipes.LocalData;

import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

/**
 * @author Kohsuke Kawaguchi
 */
public class HudsonTest extends HudsonTestCase {
    /**
     * Tests the basic UI sanity and HtmlUnit set up.
     */
    public void testGlobalConfigRoundtrip() throws Exception {
        jenkins.setQuietPeriod(10);
        jenkins.setScmCheckoutRetryCount(9);
        jenkins.setNumExecutors(8);
        configRoundtrip();
        assertEquals(10,jenkins.getQuietPeriod());
        assertEquals(9,jenkins.getScmCheckoutRetryCount());
        assertEquals(8,jenkins.getNumExecutors());
    }

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
        submit(form);

        // make sure all the pieces are intact
        assertEquals(2, jenkins.getNumExecutors());
        assertSame(Mode.NORMAL, jenkins.getMode());
        assertSame(SecurityRealm.NO_AUTHENTICATION, jenkins.getSecurityRealm());
        assertSame(AuthorizationStrategy.UNSECURED, jenkins.getAuthorizationStrategy());
        assertEquals(5, jenkins.getQuietPeriod());

        List<JDK> jdks = jenkins.getJDKs();
        assertEquals(3,jdks.size()); // Hudson adds one more
        assertJDK(jdks.get(0),"jdk1","/tmp");
        assertJDK(jdks.get(1),"jdk2","/tmp");

        AntInstallation[] ants = jenkins.getDescriptorByType(Ant.DescriptorImpl.class).getInstallations();
        assertEquals(2,ants.length);
        assertAnt(ants[0],"ant1","/tmp");
        assertAnt(ants[1],"ant2","/tmp");
    }

    private void assertAnt(AntInstallation ant, String name, String home) {
        assertEquals(ant.getName(),name);
        assertEquals(ant.getHome(),home);
    }

    private void assertJDK(JDK jdk, String name, String home) {
        assertEquals(jdk.getName(),name);
        assertEquals(jdk.getHome(),home);
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

    /**
     * Top page should only have one item in the breadcrumb.
     */
    public void testBreadcrumb() throws Exception {
        HtmlPage root = new WebClient().goTo("");
        HtmlElement navbar = root.getElementById("breadcrumbs");
        assertEquals(1,navbar.selectNodes("LI/A").size());
    }

    /**
     * Configure link from "/computer/(master)/" should work.
     */
    @Email("http://www.nabble.com/Master-slave-refactor-td21361880.html")
    public void testComputerConfigureLink() throws Exception {
        HtmlPage page = new WebClient().goTo("computer/(master)/configure");
        submit(page.getFormByName("config"));
    }

    /**
     * Configure link from "/computer/(master)/" should work.
     */
    @Email("http://www.nabble.com/Master-slave-refactor-td21361880.html")
    public void testDeleteHudsonComputer() throws Exception {
        HudsonTestCase.WebClient wc = new WebClient();
        HtmlPage page = wc.goTo("computer/(master)/");
        for (HtmlAnchor a : page.getAnchors())
            assertFalse(a.getHrefAttribute(),a.getHrefAttribute().endsWith("delete"));

        // try to delete it by hitting the final URL directly
        WebRequestSettings req = new WebRequestSettings(new URL(wc.getContextPath()+"computer/(master)/doDelete"), HttpMethod.POST);
        try {
            wc.getPage(wc.addCrumb(req));
            fail("Error code expected");
        } catch (FailingHttpStatusCodeException e) {
            assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, e.getStatusCode());
        }

        // the master computer object should be still here
        wc.goTo("computer/(master)/");
    }

    /**
     * Legacy descriptors should be visible in the /descriptor/xyz URL.
     */
    @Email("http://www.nabble.com/1.286-version-and-description-The-requested-resource-%28%29-is-not--available.-td22233801.html")
    public void testLegacyDescriptorLookup() throws Exception {
        Descriptor dummy = new Descriptor(HudsonTest.class) {
            public String getDisplayName() {
                return "dummy";
            }
        };

        BuildStep.PUBLISHERS.addRecorder(dummy);
        assertSame(dummy, jenkins.getDescriptor(HudsonTest.class.getName()));

        BuildStep.PUBLISHERS.remove(dummy);
        assertNull(jenkins.getDescriptor(HudsonTest.class.getName()));
    }

    /**
     * Verify null/invalid primaryView setting doesn't result in infinite loop.
     */
    @Bug(6938)
    public void testInvalidPrimaryView() throws Exception {
        Field pv = Jenkins.class.getDeclaredField("primaryView");
        pv.setAccessible(true);
        String value = null;
        pv.set(jenkins, value);
        assertNull("null primaryView", jenkins.getView(value));
        value = "some bogus name";
        pv.set(jenkins, value);
        assertNull("invalid primaryView", jenkins.getView(value));
    }
}
