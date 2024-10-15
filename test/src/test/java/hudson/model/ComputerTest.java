/*
 * The MIT License
 *
 * Copyright (c) 2015 Red Hat, Inc.; Christopher Simons
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

import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.any;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isA;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeThat;

import hudson.ExtensionList;
import hudson.Functions;
import hudson.diagnosis.OldDataMonitor;
import hudson.remoting.Channel;
import hudson.slaves.DumbSlave;
import hudson.slaves.OfflineCause;
import java.io.File;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import jenkins.model.Jenkins;
import jenkins.widgets.ExecutorsWidget;
import jenkins.widgets.HasWidgetHelper;
import org.htmlunit.FailingHttpStatusCodeException;
import org.htmlunit.HttpMethod;
import org.htmlunit.Page;
import org.htmlunit.WebRequest;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.xml.XmlPage;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.MemoryAssert;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.MockFolder;
import org.jvnet.hudson.test.SmokeTest;
import org.jvnet.hudson.test.recipes.LocalData;

@Category(SmokeTest.class)
public class ComputerTest {

    @Rule public JenkinsRule j = new JenkinsRule();
    @Rule public LoggerRule logging = new LoggerRule();

    @Test
    public void discardLogsAfterDeletion() throws Exception {
        DumbSlave delete = j.createOnlineSlave(Jenkins.get().getLabelAtom("delete"));
        DumbSlave keep = j.createOnlineSlave(Jenkins.get().getLabelAtom("keep"));
        File logFile = delete.toComputer().getLogFile();
        assertTrue(logFile.exists());

        Jenkins.get().removeNode(delete);

        assertFalse("Slave log should be deleted", logFile.exists());
        assertFalse("Slave log directory should be deleted", logFile.getParentFile().exists());

        assertTrue("Slave log should be kept", keep.toComputer().getLogFile().exists());
    }

    /**
     * Verify we can't rename a node over an existing node.
     */
    @Issue("JENKINS-31321")
    @Test
    public void testProhibitRenameOverExistingNode() throws Exception {
        final String NOTE = "Rename node to name of another node should fail.";

        Node nodeA = j.createSlave("nodeA", null, null);
        Node nodeB = j.createSlave("nodeB", null, null);

        WebClient wc = j.createWebClient()
                .withThrowExceptionOnFailingStatusCode(false);
        HtmlForm form = wc.getPage(nodeB, "configure").getFormByName("config");
        form.getInputByName("_.name").setValue("nodeA");

        Page page = j.submit(form);
        assertEquals(NOTE, HttpURLConnection.HTTP_BAD_REQUEST, page.getWebResponse().getStatusCode());
        assertThat(NOTE, page.getWebResponse().getContentAsString(),
                containsString("Agent called ‘nodeA’ already exists"));
    }

    @Test
    public void doNotShowUserDetailsInOfflineCause() throws Exception {
        DumbSlave slave = j.createOnlineSlave();
        final Computer computer = slave.toComputer();
        computer.setTemporarilyOffline(true, new OfflineCause.UserCause(User.getOrCreateByIdOrFullName("username"), "msg"));
        verifyOfflineCause(computer);
    }

    @Test @LocalData
    public void removeUserDetailsFromOfflineCause() throws Exception {
        Computer computer = j.jenkins.getComputer("deserialized");
        verifyOfflineCause(computer);
    }

    private void verifyOfflineCause(Computer computer) throws Exception {
        XmlPage page = j.createWebClient().goToXml("computer/" + computer.getName() + "/config.xml");
        String content = page.getWebResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(content, containsString("temporaryOfflineCause"));
        assertThat(content, containsString("<userId>username</userId>"));
        assertThat(content, not(containsString("ApiTokenProperty")));
        assertThat(content, not(containsString("apiToken")));
    }

    @Issue("JENKINS-42969")
    @Test
    public void addAction() throws Exception {
        Computer c = j.createSlave().toComputer();
        class A extends InvisibleAction {}

        assertEquals(0, c.getActions(A.class).size());
        c.addAction(new A());
        assertEquals(1, c.getActions(A.class).size());
        c.addAction(new A());
        assertEquals(2, c.getActions(A.class).size());
    }

    @Test
    public void tiedJobs() throws Exception {
        DumbSlave s = j.createOnlineSlave();
        Label l = s.getSelfLabel();
        Computer c = s.toComputer();
        FreeStyleProject p = j.createFreeStyleProject();
        p.setAssignedLabel(l);
        FreeStyleProject p2 = j.createFreeStyleProject();
        MockFolder f = j.createFolder("test");
        FreeStyleProject p3 = f.createProject(FreeStyleProject.class, "project");
        p3.setAssignedLabel(l);
        assertThat(c.getTiedJobs(), containsInAnyOrder(p, p3));
    }

    @Test
    public void exceptions() throws Exception {
        logging.record("", Level.WARNING).capture(10);
        boolean ok = false;
        Computer.threadPoolForRemoting.submit(() -> {
            if (!ok) {
                throw new IllegalStateException("oops");
            }
        });
        await().atMost(15, TimeUnit.SECONDS).until(() -> logging, LoggerRule.recorded(Level.WARNING, anyOf(nullValue(), any(String.class)), isA(IllegalStateException.class)));
    }

    @Issue("SECURITY-1923")
    @Test
    public void configDotXmlWithValidXmlAndBadField() throws Exception {
        final String CONFIGURATOR = "configure_user";

        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        MockAuthorizationStrategy mas = new MockAuthorizationStrategy();
        mas.grant(Computer.CONFIGURE, Computer.EXTENDED_READ, Jenkins.READ)
                .everywhere()
                .to(CONFIGURATOR);
        j.jenkins.setAuthorizationStrategy(mas);

        Computer computer = j.createSlave().toComputer();

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.login(CONFIGURATOR);
        WebRequest req = new WebRequest(wc.createCrumbedUrl(String.format("%s/config.xml", computer.getUrl())), HttpMethod.POST);
        req.setAdditionalHeader("Content-Type", "application/xml");
        req.setRequestBody(VALID_XML_BAD_FIELD_USER_XML);

        FailingHttpStatusCodeException e = assertThrows(FailingHttpStatusCodeException.class, () -> wc.getPage(req));
        // This really shouldn't return 500, but that's what it does now.
        assertThat(e.getStatusCode(), equalTo(500));

        OldDataMonitor odm = ExtensionList.lookupSingleton(OldDataMonitor.class);
        Map<Saveable, OldDataMonitor.VersionRange> data = odm.getData();

        assertThat(data.size(), equalTo(0));

        odm.doDiscard(null, null);

        User.AllUsers.scanAll();
        boolean createUser = false;
        User badUser = User.getById("foo", createUser);

        assertNull("Should not have created user.", badUser);
    }

    private static final String VALID_XML_BAD_FIELD_USER_XML =
            "<hudson.model.User>\n" +
                    "  <id>foo</id>\n" +
                    "  <fullName>Foo User</fullName>\n" +
                    "  <badField/>\n" +
                    "</hudson.model.User>\n";

    @Test
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
    public void testTerminatedNodeAjaxExecutorsDoesNotShowTrace() throws Exception {
        DumbSlave agent = j.createOnlineSlave();
        FreeStyleProject p = j.createFreeStyleProject();
        p.setAssignedNode(agent);

        FreeStyleBuild b = ExecutorTest.startBlockingBuild(p);

        String message = "It went away";
        b.getBuiltOn().toComputer().disconnect(
                new OfflineCause.ChannelTermination(new RuntimeException(message))
        );

        WebClient wc = j.createWebClient();
        Page page = wc.getPage(wc.createCrumbedUrl(HasWidgetHelper.getWidget(agent.toComputer(), ExecutorsWidget.class).orElseThrow().getUrl() + "ajax"));
        String content = page.getWebResponse().getContentAsString();
        assertThat(content, not(containsString(message)));

        j.assertBuildStatus(Result.FAILURE, j.waitForCompletion(b));
    }

    @Test
    public void computersCollected() throws Exception {
        assumeThat("Seems to crash the test JVM at least in CI", Functions.isWindows(), is(false));
        DumbSlave agent = j.createOnlineSlave();
        FreeStyleProject p = j.createFreeStyleProject();
        p.setAssignedNode(agent);
        j.buildAndAssertSuccess(p);
        Computer computer = agent.toComputer();
        WeakReference<Computer> computerRef = new WeakReference<>(computer);
        WeakReference<Channel> channelRef = new WeakReference<>((Channel) computer.getChannel());
        computer.disconnect(null);
        computer = null;
        j.jenkins.removeNode(agent);
        agent = null;
        MemoryAssert.assertGC(computerRef, false);
        MemoryAssert.assertGC(channelRef, false);
    }

}
