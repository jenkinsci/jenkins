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
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Functions;
import hudson.cli.CLICommandInvoker;
import hudson.slaves.DumbSlave;
import hudson.slaves.OfflineCause;
import hudson.slaves.RetentionStrategy;
import hudson.util.FormValidation;
import java.net.HttpURLConnection;
import jenkins.model.Jenkins;
import jenkins.widgets.ExecutorsWidget;
import jenkins.widgets.HasWidgetHelper;
import org.htmlunit.HttpMethod;
import org.htmlunit.Page;
import org.htmlunit.WebRequest;
import org.htmlunit.WebResponse;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * @author Kohsuke Kawaguchi
 */
@WithJenkins
class ComputerSetTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    @Issue("JENKINS-2821")
    void pageRendering() throws Exception {
        WebClient client = j.createWebClient();
        j.createSlave();
        client.goTo("computer");
    }

    /**
     * Tests the basic UI behavior of the node monitoring
     */
    @Test
    void configuration() throws Exception {
        WebClient client = j.createWebClient();
        HtmlForm form = client.goTo("computer/configure").getFormByName("config");
        j.submit(form);
    }

    @Test
    void nodeOfflineCli() throws Exception {
        DumbSlave s = j.createSlave();

        assertThat(new CLICommandInvoker(j, "wait-node-offline").invokeWithArgs("xxx"), CLICommandInvoker.Matcher.failedWith(/* IllegalArgumentException from NodeOptionHandler */ 3));
        assertThat(new CLICommandInvoker(j, "wait-node-online").invokeWithArgs(s.getNodeName()), CLICommandInvoker.Matcher.succeededSilently());

        s.toComputer().disconnect(null).get();

        assertThat(new CLICommandInvoker(j, "wait-node-offline").invokeWithArgs(s.getNodeName()), CLICommandInvoker.Matcher.succeededSilently());
    }

    @Test
    void getComputerNames() throws Exception {
        assertThat(ComputerSet.getComputerNames(), is(empty()));
        j.createSlave("anAnotherNode", "", null);
        assertThat(ComputerSet.getComputerNames(), contains("anAnotherNode"));
        j.createSlave("aNode", "", null);
        assertThat(ComputerSet.getComputerNames(), contains("aNode", "anAnotherNode"));
    }

    @Test
    void managePermissionCanConfigure() throws Exception {
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
    void testTerminatedNodeStatusPageDoesNotShowTrace() throws Exception {
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
    void testTerminatedNodeAjaxExecutorsDoesNotShowTrace() throws Exception {
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

    @Test
    @Issue("https://github.com/jenkinsci/jenkins/issues/16372")
    void checkNameRejectsBuiltInLiteral() {
        ComputerSet computerSet = new ComputerSet();
        Failure f = assertThrows(Failure.class, () -> computerSet.checkName("(built-in)"));
        assertThat(f.getMessage(), allOf(containsString("(built-in)"), containsString("is not an allowed name")));
        f = assertThrows(Failure.class, () -> computerSet.checkName("(master)"));
        assertThat(f.getMessage(), allOf(containsString("(master)"), containsString("is not an allowed name")));
    }

    /* Many of the tests are skipped on Windows CI because they are platform
     * independent and would extend the already slow Windows test time on CI.
     */
    @Test
    @Issue("https://github.com/jenkinsci/jenkins/issues/16372")
    void checkNameAllowsCaseVariations() {
        assumeFalse(Functions.isWindows() && System.getenv("CI") != null, "Not valuable enough to run on Windows CI");
        ComputerSet computerSet = new ComputerSet();
        // These should be allowed (case-sensitive check)
        assertThat(computerSet.checkName("(Built-in)"), is("(Built-in)"));
        assertThat(computerSet.checkName("(Built-IN)"), is("(Built-IN)"));
        assertThat(computerSet.checkName("(BuiLT-in)"), is("(BuiLT-in)"));
        assertThat(computerSet.checkName("(Master)"), is("(Master)"));
        assertThat(computerSet.checkName("(MASTER)"), is("(MASTER)"));
        assertThat(computerSet.checkName("(mastEr)"), is("(mastEr)"));
    }

    @Test
    @Issue("https://github.com/jenkinsci/jenkins/issues/16372")
    void checkNameAllowsSubstrings() {
        assumeFalse(Functions.isWindows() && System.getenv("CI") != null, "Not valuable enough to run on Windows CI");
        ComputerSet computerSet = new ComputerSet();
        // These should be allowed (not exact matches)
        assertThat(computerSet.checkName("prefix-(built-in)"), is("prefix-(built-in)"));
        assertThat(computerSet.checkName("(built-in)-suffix"), is("(built-in)-suffix"));
        assertThat(computerSet.checkName("space (built-in) name"), is("space (built-in) name"));
        assertThat(computerSet.checkName("(built-in-suffix)"), is("(built-in-suffix)"));
        assertThat(computerSet.checkName("(prefix-built-in)"), is("(prefix-built-in)"));
        assertThat(computerSet.checkName("prefix-(master)"), is("prefix-(master)"));
        assertThat(computerSet.checkName("(master)-suffix"), is("(master)-suffix"));
        assertThat(computerSet.checkName("space (master) name"), is("space (master) name"));
        assertThat(computerSet.checkName("(master-suffix)"), is("(master-suffix)"));
        assertThat(computerSet.checkName("(prefix-master)"), is("(prefix-master)"));
    }

    @Test
    @Issue("https://github.com/jenkinsci/jenkins/issues/16372")
    void doCheckNameFormValidation() throws Exception {
        assumeFalse(Functions.isWindows() && System.getenv("CI") != null, "Not valuable enough to run on Windows CI");
        ComputerSet computerSet = new ComputerSet();
        // Should return error for "(built-in)"
        FormValidation validation = computerSet.doCheckName("(built-in)");
        assertThat(validation.kind, is(FormValidation.Kind.ERROR));
        assertThat(validation.toString(), allOf(containsString("(built-in)"), containsString("is not an allowed name")));
        validation = computerSet.doCheckName("(master)");
        assertThat(validation.kind, is(FormValidation.Kind.ERROR));
        assertThat(validation.toString(), allOf(containsString("(master)"), containsString("is not an allowed name")));
    }

    @Test
    @Issue("https://github.com/jenkinsci/jenkins/issues/16372")
    void createItemFromXmlWithNameBuiltInQueryParameter() throws Exception {
        createItemTest("(built-in)");
    }

    @Test
    @Issue("https://github.com/jenkinsci/jenkins/issues/16372")
    void createItemFromXmlWithNameBuiltInXML() throws Exception {
        assumeFalse(Functions.isWindows() && System.getenv("CI") != null, "Not valuable enough to run on Windows CI");
        createItemTestEmbedNameInXML("(built-in)");
    }

    @Test
    @Issue("https://github.com/jenkinsci/jenkins/issues/16372")
    void createItemFromXmlWithEmptyNameInQueryParameter() throws Exception {
        assumeFalse(Functions.isWindows() && System.getenv("CI") != null, "Not valuable enough to run on Windows CI");
        createItemTest("");
    }

    @Test
    @Issue("https://github.com/jenkinsci/jenkins/issues/16372")
    void createItemFromXmlWithEmptyNameInXML() throws Exception {
        assumeFalse(Functions.isWindows() && System.getenv("CI") != null, "Not valuable enough to run on Windows CI");
        createItemTestEmbedNameInXML("");
    }

    @Test
    @Issue("https://github.com/jenkinsci/jenkins/issues/16372")
    void createItemFromXmlWithNameMaster() throws Exception {
        assumeFalse(Functions.isWindows() && System.getenv("CI") != null, "Not valuable enough to run on Windows CI");
        createItemTest("(master)");
    }

    @Test
    void createItemFromXmlNoNameQueryParameter() throws Exception {
        createItemTestEmbedNameInXML("agent-from-xml");
    }

    @Test
    void createItemFromXmlWithName() throws Exception {
        createItemTest("new-name");
    }

    private void createItemTestEmbedNameInXML(@NonNull String name) throws Exception {
        createItemTest(name, true, false);
    }

    private void createItemTest(@NonNull String name) throws Exception {
        createItemTest(name, false, true);
    }

    private void createItemTest(@NonNull String name, boolean embedNameInXML, boolean provideNameAsQueryParameter) throws Exception {
        String USER = "user";
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                // Grant computer create
                .grant(Jenkins.READ).everywhere().to(USER)
                .grant(Computer.CREATE).everywhere().to(USER)
        );

        String xml = String.format("""
                <slave>
                  <name>%s</name>
                  <description></description>
                  <remoteFS>/home/jenkins</remoteFS>
                  <numExecutors>2</numExecutors>
                  <mode>NORMAL</mode>
                  <retentionStrategy class="hudson.slaves.RetentionStrategy$Always"/>
                  <launcher class="hudson.slaves.JNLPLauncher"/>
                  <label>linux</label>
                  <nodeProperties/>
                </slave>
                """, embedNameInXML ? name : "");
        try (JenkinsRule.WebClient wc = j.createWebClient().withThrowExceptionOnFailingStatusCode(false)) {
            wc.login(USER);
            String agentCreateUrl = "computer/createItem";
            if (provideNameAsQueryParameter) {
                agentCreateUrl += "?name=" + name;
            }
            WebRequest req = new WebRequest(wc.createCrumbedUrl(agentCreateUrl), HttpMethod.POST);
            req.setAdditionalHeader("Content-Type", "application/xml");
            req.setRequestBody(xml);
            WebResponse rsp = wc.getPage(req).getWebResponse();
            if (name.isEmpty()) {
                assertThat(rsp.getStatusCode(), is(400));
                assertThat(rsp.getContentAsString(), containsString("Name must not be empty"));
                return;
            }
            if ("(built-in)".equals(name) || "(master)".equals(name)) {
                assertThat(rsp.getStatusCode(), is(400));
                assertThat(rsp.getContentAsString(), allOf(containsString(name), containsString("is not an allowed name")));
                return;
            }
            assertThat(rsp.getStatusCode(), is(200));
            Node node = j.jenkins.getNode(name);
            assertThat(node, is(notNullValue()));
            DumbSlave agent = (DumbSlave) node;
            assertThat(agent.getNodeName(), is(name));
            assertThat(agent.remoteFS, is("/home/jenkins"));
            assertThat(agent.getNumExecutors(), is(2));
            assertThat(agent.getLabelString(), is("linux"));
            assertThat(agent.getRetentionStrategy(), is(instanceOf(RetentionStrategy.Always.class)));
        }
    }
}
