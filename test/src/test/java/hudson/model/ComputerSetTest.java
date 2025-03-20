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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;

import hudson.cli.CLICommandInvoker;
import hudson.slaves.DumbSlave;
import hudson.slaves.OfflineCause;
import java.net.HttpURLConnection;
import jenkins.model.Jenkins;
import jenkins.widgets.ExecutorsWidget;
import jenkins.widgets.HasWidgetHelper;
import org.htmlunit.Page;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlPage;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.jvnet.hudson.test.MockAuthorizationStrategy;

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
        j.createSlave("anAnotherNode", "", null);
        assertThat(ComputerSet.getComputerNames(), contains("anAnotherNode"));
        j.createSlave("aNode", "", null);
        assertThat(ComputerSet.getComputerNames(), contains("aNode", "anAnotherNode"));
    }

    @Test
    public void managePermissionCanConfigure() throws Exception {
        final String USER = "user";
        final String MANAGER = "manager";
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                // Read access
                .grant(Jenkins.READ).everywhere().to(USER)

                // Read and Manage
                .grant(Jenkins.READ).everywhere().to(MANAGER)
                .grant(Jenkins.MANAGE).everywhere().to(MANAGER)
        );

        JenkinsRule.WebClient wc = j.createWebClient()
                .withThrowExceptionOnFailingStatusCode(false);

        // Jenkins.READ can access /computer but not /computer/configure
        wc.login(USER);
        HtmlPage page = wc.goTo("computer/");
        assertEquals(HttpURLConnection.HTTP_OK, page.getWebResponse().getStatusCode());
        String responseContent = page.getWebResponse().getContentAsString();
        // the "Node Monitoring" link in the app bar is not visible
        assertThat(responseContent, not(containsString("Node Monitoring")));
        page = wc.goTo("computer/configure");
        assertEquals(HttpURLConnection.HTTP_FORBIDDEN, page.getWebResponse().getStatusCode());

        // Jenkins.MANAGER can access /computer and /computer/configure
        wc.login(MANAGER);
        page = wc.goTo("computer/");
        assertEquals(HttpURLConnection.HTTP_OK, page.getWebResponse().getStatusCode());
        responseContent = page.getWebResponse().getContentAsString();
        // the "Node Monitoring" link in the app bar is visible
        assertThat(responseContent, containsString("Configure Monitors"));
        page = wc.goTo("computer/configure");
        assertEquals(HttpURLConnection.HTTP_OK, page.getWebResponse().getStatusCode());
        // and the save button is visible
        responseContent = page.getWebResponse().getContentAsString();
        assertThat(responseContent, containsString("Save"));
    }

    @Test
    @Issue("SECURITY-2120")
    public void testTerminatedNodeStatusPageDoesNotShowTrace() throws Exception {
        DumbSlave agent = j.createOnlineSlave();
        FreeStyleProject p = j.createFreeStyleProject();
        p.setAssignedNode(agent);

        FreeStyleBuild b = ExecutorTest.startBlockingBuild(p);

        String message = "It went away";
        b.getBuiltOn().toComputer().disconnect(
                new OfflineCause.ChannelTermination(new RuntimeException(message))
        );

        WebClient wc = j.createWebClient();
        Page page = wc.getPage(wc.createCrumbedUrl(agent.toComputer().getUrl()));
        String content = page.getWebResponse().getContentAsString();
        assertThat(content, not(containsString(message)));

        j.assertBuildStatus(Result.FAILURE, j.waitForCompletion(b));
    }

    @Test
    @Issue("SECURITY-2120")
    public void testTerminatedNodeAjaxExecutorsDoesNotShowTrace() throws Exception {
        DumbSlave agent = j.createOnlineSlave();
        FreeStyleProject p = j.createFreeStyleProject();
        p.setAssignedNode(agent);

        FreeStyleBuild b = ExecutorTest.startBlockingBuild(p);

        String message = "It went away";
        b.getBuiltOn().toComputer().disconnect(
                new OfflineCause.ChannelTermination(new RuntimeException(message))
        );

        WebClient wc = j.createWebClient().withJavaScriptEnabled(false);
        Page page = wc.getPage(wc.createCrumbedUrl(HasWidgetHelper.getWidget(j.jenkins.getComputer(), ExecutorsWidget.class).orElseThrow().getUrl() + "ajax"));
        String content = page.getWebResponse().getContentAsString();
        assertThat(content, not(containsString(message)));

        j.assertBuildStatus(Result.FAILURE, j.waitForCompletion(b));
    }
}
