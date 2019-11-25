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

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.html.DomElement;
import com.gargoylesoftware.htmlunit.html.DomNodeUtil;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.model.Node.Mode;
import hudson.search.SearchTest;
import hudson.security.AuthorizationStrategy;
import hudson.security.SecurityRealm;
import hudson.tasks.Ant;
import hudson.tasks.BuildStep;
import hudson.tasks.Ant.AntInstallation;
import hudson.tasks.Shell;
import jenkins.model.Jenkins;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.jvnet.hudson.test.Email;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.SmokeTest;
import org.jvnet.hudson.test.recipes.LocalData;

import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

/**
 * @author Kohsuke Kawaguchi
 */
@Category(SmokeTest.class)
public class HudsonTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    /**
     * Tests the basic UI sanity and HtmlUnit set up.
     */
    @Test
    public void globalConfigRoundtrip() throws Exception {
        j.jenkins.setQuietPeriod(10);
        j.jenkins.setScmCheckoutRetryCount(9);
        j.jenkins.setNumExecutors(8);
        j.configRoundtrip();
        assertEquals(10, j.jenkins.getQuietPeriod());
        assertEquals(9, j.jenkins.getScmCheckoutRetryCount());
        assertEquals(8, j.jenkins.getNumExecutors());
    }

    /**
     * Performs a very basic round-trip of a non-empty system configuration screen.
     * This makes sure that the structured form submission is working (to some limited extent.)
     */
    @Test
    @LocalData
    @Email("http://www.nabble.com/Hudson.configure-calling-deprecated-Descriptor.configure-td19051815.html")
    public void simpleConfigSubmit() throws Exception {
        // just load the page and resubmit
        HtmlPage configPage = j.createWebClient().goTo("configure");
        HtmlForm form = configPage.getFormByName("config");
        j.submit(form);
        // Load tools page and resubmit too
        HtmlPage toolsConfigPage = j.createWebClient().goTo("configureTools");
        HtmlForm toolsForm = toolsConfigPage.getFormByName("config");
        j.submit(toolsForm);

        // make sure all the pieces are intact
        assertEquals(2, j.jenkins.getNumExecutors());
        assertSame(Mode.NORMAL, j.jenkins.getMode());
        assertSame(SecurityRealm.NO_AUTHENTICATION, j.jenkins.getSecurityRealm());
        assertSame(AuthorizationStrategy.UNSECURED, j.jenkins.getAuthorizationStrategy());
        assertEquals(5, j.jenkins.getQuietPeriod());

        List<JDK> jdks = j.jenkins.getJDKs();
        assertEquals(3,jdks.size()); // Hudson adds one more
        assertJDK(jdks.get(0),"jdk1","/tmp");
        assertJDK(jdks.get(1),"jdk2","/tmp");

        AntInstallation[] ants = j.jenkins.getDescriptorByType(Ant.DescriptorImpl.class).getInstallations();
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
    @Test
    public void searchIndex() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        Page jobPage = j.search(p.getName());

        URL url = jobPage.getUrl();
        System.out.println(url);
        assertTrue(url.getPath().endsWith("/job/"+p.getName()+"/"));
    }

    /**
     * Top page should only have one item in the breadcrumb.
     */
    @Test
    public void breadcrumb() throws Exception {
        HtmlPage root = j.createWebClient().goTo("");
        DomElement navbar = root.getElementById("breadcrumbs");
        assertEquals(1, DomNodeUtil.selectNodes(navbar, "LI/A").size());
    }

    /**
     * Configure link from "/computer/(master)/" should work.
     */
    @Test
    @Email("http://www.nabble.com/Master-slave-refactor-td21361880.html")
    public void computerConfigureLink() throws Exception {
        HtmlPage page = j.createWebClient().goTo("computer/(master)/configure");
        j.submit(page.getFormByName("config"));
    }

    /**
     * Configure link from "/computer/(master)/" should work.
     */
    @Test
    @Email("http://www.nabble.com/Master-slave-refactor-td21361880.html")
    public void deleteHudsonComputer() throws Exception {
        WebClient wc = j.createWebClient();
        HtmlPage page = wc.goTo("computer/(master)/");
        for (HtmlAnchor a : page.getAnchors()) {
            assertFalse(a.getHrefAttribute(), a.getHrefAttribute().endsWith("delete"));
        }

        wc.setThrowExceptionOnFailingStatusCode(false);
        // try to delete it by hitting the final URL directly
        WebRequest req = new WebRequest(new URL(wc.getContextPath()+"computer/(master)/doDelete"), HttpMethod.POST);
        page = wc.getPage(wc.addCrumb(req));
        assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, page.getWebResponse().getStatusCode());

        // the master computer object should be still here
        page = wc.goTo("computer/(master)/");
        assertEquals(HttpURLConnection.HTTP_OK, page.getWebResponse().getStatusCode());
    }

    /**
     * Legacy descriptors should be visible in the /descriptor/xyz URL.
     */
    @Test
    @Email("http://www.nabble.com/1.286-version-and-description-The-requested-resource-%28%29-is-not--available.-td22233801.html")
    public void legacyDescriptorLookup() throws Exception {
        Descriptor dummy = new Descriptor(HudsonTest.class) {};

        BuildStep.PUBLISHERS.addRecorder(dummy);
        assertSame(dummy, j.jenkins.getDescriptor(HudsonTest.class.getName()));

        BuildStep.PUBLISHERS.remove(dummy);
        assertNull(j.jenkins.getDescriptor(HudsonTest.class.getName()));
    }

    /**
     * Verify null/invalid primaryView setting doesn't result in infinite loop.
     */
    @Test
    @Issue("JENKINS-6938")
    public void invalidPrimaryView() throws Exception {
        Field pv = Jenkins.class.getDeclaredField("primaryView");
        pv.setAccessible(true);
        String value = null;
        pv.set(j.jenkins, value);
        assertNull("null primaryView", j.jenkins.getView(value));
        value = "some bogus name";
        pv.set(j.jenkins, value);
        assertNull("invalid primaryView", j.jenkins.getView(value));
    }

    @Issue("JENKINS-60266")
    @Test
    public void globalConfigAllowedWithConfigurePermission() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                                                   .grant(Jenkins.CONFIGURE, Jenkins.READ).everywhere().toEveryone());

        HtmlForm form = j.createWebClient().goTo("configure").getFormByName("config");
        HtmlPage updated = j.submit(form);
        assertEquals("User with CONFIGURE_JENKINS permission should be able to update global configuration",
                     200, updated.getWebResponse().getStatusCode());
    }

    @Issue("JENKINS-60266")
    @Test
    public void someGlobalConfigurationIsNotDisplayedWithConfigurePermission() throws Exception {
        //GIVEN a user with CONFIGURE permission
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                                                   .grant(Jenkins.CONFIGURE, Jenkins.READ).everywhere().toEveryone());

        //WHEN the user goes to /configure page
        HtmlForm form = j.createWebClient().goTo("configure").getFormByName("config");
        String formText = form.asText();
        //THEN items restricted to ADMINISTER only should not be displayed.
        assertThat("Shouldn't be able to configure # of executors", formText, not(containsString("executors")));
        assertThat("Shouldn't be able to configure Global properties", formText,
                   not(containsString("Global properties")));
        assertThat("Shouldn't be able to configure Administrative monitors", formText, not(containsString(
                "Administrative "
                + "monitors")));
        assertThat("Shouldn't be able to configure Shell", formText, not(containsString("Shell")));
    }

    @Issue("JENKINS-60266")
    @Test
    public void someGlobalConfigCanNotBeModifiedWithConfigurePermission() throws Exception {
        //GIVEN the Global Configuration Form, with some changes unsaved
        int currentNumberExecutors = j.getInstance().getNumExecutors();
        View primary = j.getInstance().getPrimaryView();
        String shell = getShell();
        j.getInstance().addView(new ListView("otherView"));

        HtmlForm form = j.createWebClient().goTo("configure").getFormByName("config");
        form.getInputByName("_.numExecutors").setValueAttribute(""+(currentNumberExecutors+1));
        form.getSelectByName("primaryView").setSelectedAttribute("otherView", true);
        form.getInputByName("_.shell").setValueAttribute("/fakeShell");

        // WHEN a user with CONFIGURE permission only try to save those changes
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                                                   .grant(Jenkins.CONFIGURE, Jenkins.READ).everywhere().toEveryone());
        j.submit(form);
        // THEN the changes on fields forbidden to a CONFIGURE_JENKINS permission are not saved
        assertEquals("shouldn't be allowed to change the number of executors", currentNumberExecutors, j.getInstance().getNumExecutors());
        assertEquals("shouldn't be allowed to change the primary view", primary, j.getInstance().getPrimaryView());
        assertEquals("shouldn't be allowed to change the shell executable", shell, getShell());
    }

    private String getShell() {
        Descriptor descriptorByName = j.getInstance().getDescriptorByName("hudson.tasks.Shell");
        return ((Shell.DescriptorImpl) descriptorByName).getShell();
    }
}
