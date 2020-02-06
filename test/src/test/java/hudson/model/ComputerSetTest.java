/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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

import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.cli.CLICommandInvoker;
import hudson.slaves.DumbSlave;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import jenkins.model.Jenkins;

/**
 * @author Kohsuke Kawaguchi
 */
public class ComputerSetTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    @Issue("JENKINS-2821")
    public void pageRendering() throws Exception {
        WebClient client = j.createWebClient();
        j.createSlave();
        client.goTo("computer");
    }

    /**
     * Tests the basic UI behavior of the node monitoring
     */
    @Test
    public void configuration() throws Exception {
        WebClient client = j.createWebClient();
        HtmlForm form = client.goTo("computer/configure").getFormByName("config");
        j.submit(form);
    }

    @Test
    public void nodeOfflineCli() throws Exception {
        DumbSlave s = j.createSlave();

        assertThat(new CLICommandInvoker(j, "wait-node-offline").invokeWithArgs("xxx"), CLICommandInvoker.Matcher.failedWith(/* IllegalArgumentException from NodeOptionHandler */ 3));
        assertThat(new CLICommandInvoker(j, "wait-node-online").invokeWithArgs(s.getNodeName()), CLICommandInvoker.Matcher.succeededSilently());

        s.toComputer().disconnect(null).get();

        assertThat(new CLICommandInvoker(j, "wait-node-offline").invokeWithArgs(s.getNodeName()), CLICommandInvoker.Matcher.succeededSilently());
    }

    @Test
    public void getComputerNames() throws Exception {
        assertThat(ComputerSet.getComputerNames(), is(empty()));
        j.createSlave("aNode", "", null);
        assertThat(ComputerSet.getComputerNames(), contains("aNode"));
        j.createSlave("anAnotherNode", "", null);
        assertThat(ComputerSet.getComputerNames(), containsInAnyOrder("aNode", "anAnotherNode"));
    }

    @Issue("JENKINS-60266")
    @Test
    public void monitorDisplayedWithManagePermission() throws Exception {
        //GIVEN a user with MANAGE permission
        final String MANAGER = "manager";

        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                                                   .grant(Jenkins.MANAGE, Jenkins.READ).everywhere().to(MANAGER));
        //WHEN the user go to Monitors
        WebClient client = j.createWebClient();
        HtmlPage page = client.withBasicCredentials(MANAGER).goTo("computer");
        //THEN the user can see monitor information
        assertCanSeeMonitor(page, "Free Disk Space");
        assertCanSeeMonitor(page, "Free Swap Space");
        assertCanSeeMonitor(page, "Free Temp Space");
    }

    @Issue("JENKINS-60266")
    @Test
    public void monitorNotDisplayedWithoutConfigurePermission() throws Exception {
        //GIVEN a user with only READ permission
        final String USER = "user";
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(Jenkins.READ).everywhere().to(USER));

        {
            //WHEN the user goes to monitor
            WebClient client = j.createWebClient();
            HtmlPage page = client.withBasicCredentials(USER).goTo("computer");
            //THEN the user is not able to see information.
            assertCanNotSeeMonitor(page, "Free Disk Space");
            assertCanNotSeeMonitor(page, "Free Swap Space");
            assertCanNotSeeMonitor(page, "Free Temp Space");
        }
    }

    /**
     * Tests that user with {@link Jenkins#MANAGE} can submit configuration form
     */
    @Issue("JENKINS-60266")
    @Test
    public void configurationSuccessWithManagePermission() throws Exception {
        final String MANAGER = "manager";

        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                                                   .grant(Jenkins.READ, Jenkins.MANAGE).everywhere().to(MANAGER));

        WebClient client = j.createWebClient();
        HtmlForm form = client.login(MANAGER).goTo("computer/configure").getFormByName("config");
        j.submit(form);
    }

    private void assertCanSeeMonitor(HtmlPage page, String label) {
        HtmlAnchor result = page.getFirstByXPath("//*[@id=\"computers\"]//*[contains(text(),'" + label + "')]");
        assertNotNull("Monitor '"+label+"' should be displayed", result);
    }

    private void assertCanNotSeeMonitor(HtmlPage page, String label) {
        HtmlAnchor result = page.getFirstByXPath("//*[@id=\"computers\"]//*[contains(text(),'" + label + "')]");
        assertNull("Monitor '"+label+"' shouldn't be displayed", result);
    }
}
