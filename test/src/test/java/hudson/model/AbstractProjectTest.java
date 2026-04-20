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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.FilePath;
import hudson.Functions;
import hudson.Launcher;
import hudson.Util;
import hudson.matrix.MatrixProject;
import hudson.scm.NullSCM;
import hudson.scm.SCMDescriptor;
import hudson.security.GlobalMatrixAuthorizationStrategy;
import hudson.security.Permission;
import hudson.tasks.BatchFile;
import hudson.tasks.BuildTrigger;
import hudson.tasks.Shell;
import hudson.triggers.SCMTrigger;
import hudson.triggers.TimerTrigger;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import hudson.util.OneShotEvent;
import hudson.util.StreamTaskListener;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.Future;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.io.IOUtils;
import org.htmlunit.ElementNotFoundException;
import org.htmlunit.HttpMethod;
import org.htmlunit.Page;
import org.htmlunit.WebRequest;
import org.htmlunit.WebResponse;
import org.htmlunit.html.DomNode;
import org.htmlunit.html.DomNodeList;
import org.htmlunit.html.HtmlAnchor;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlInput;
import org.htmlunit.html.HtmlPage;
import org.jenkinsci.plugins.matrixauth.AuthorizationType;
import org.jenkinsci.plugins.matrixauth.PermissionEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.MockFolder;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.args4j.CmdLineException;

@WithJenkins
class AbstractProjectTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void configRoundtrip() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        Label l = j.jenkins.getLabel("foo && bar");
        project.setAssignedLabel(l);
        j.configRoundtrip(project);

        assertEquals(l, project.getAssignedLabel());
    }

    /**
     * Tests the workspace deletion.
     */
    @Test
    void wipeWorkspace() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        project.getBuildersList().add(Functions.isWindows() ? new BatchFile("echo hello") : new Shell("echo hello"));

        FreeStyleBuild b = j.buildAndAssertSuccess(project);

        assertTrue(b.getWorkspace().exists(), "Workspace should exist by now");

        project.doDoWipeOutWorkspace();

        assertFalse(b.getWorkspace().exists(), "Workspace should be gone by now");
    }

    /**
     * Makes sure that the workspace deletion is protected.
     */
    @Test
    void wipeWorkspaceProtected() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        j.createDummySecurityRealm();
        project.getBuildersList().add(Functions.isWindows() ? new BatchFile("echo hello") : new Shell("echo hello"));

        FreeStyleBuild b = j.buildAndAssertSuccess(project);

        assertTrue(b.getWorkspace().exists(), "Workspace should exist by now");

        // make sure that the action link is protected
        JenkinsRule.WebClient wc = j.createWebClient()
                .withThrowExceptionOnFailingStatusCode(false);
        Page page = wc.getPage(new WebRequest(new URI(wc.getContextPath() + project.getUrl() + "doWipeOutWorkspace").toURL(), HttpMethod.POST));
        assertEquals(HttpURLConnection.HTTP_FORBIDDEN, page.getWebResponse().getStatusCode());
    }

    /**
     * Makes sure that the workspace deletion link is not provided when the user
     * doesn't have an access.
     */
    @Test
    void wipeWorkspaceProtected2() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        GlobalMatrixAuthorizationStrategy strategy = new GlobalMatrixAuthorizationStrategy();
        strategy.add(Permission.READ, new PermissionEntry(AuthorizationType.EITHER, "anonymous"));
        strategy.add(Item.WORKSPACE, new PermissionEntry(AuthorizationType.EITHER, "anonymous"));
        j.jenkins.setAuthorizationStrategy(strategy);

        // make sure that the deletion is protected in the same way
        wipeWorkspaceProtected();

        // there shouldn't be any "wipe out workspace" link for anonymous user
        JenkinsRule.WebClient webClient = j.createWebClient();
        HtmlPage page = webClient.getPage(j.jenkins.getItem("test0"));

        HtmlPage workspace = page.getAnchorByText("Workspace").click();
        String wipeOutLabel = ResourceBundle.getBundle("hudson/model/AbstractProject/sidepanel").getString("Wipe Out Workspace");
        assertThrows(ElementNotFoundException.class, () -> workspace.getAnchorByText(wipeOutLabel), "shouldn't find a link");
    }

    /**
     * Tests the &lt;optionalBlock @field> round trip behavior by using
     * {@link AbstractProject#concurrentBuild}
     */
    @Test
    void optionalBlockDataBindingRoundtrip() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        for (boolean b : new boolean[] {true, false}) {
            p.setConcurrentBuild(b);
            j.submit(j.createWebClient().getPage(p, "configure").getFormByName("config"));
            assertEquals(b, p.isConcurrentBuild());
        }
    }

    /**
     * Tests round trip configuration of the blockBuildWhenUpstreamBuilding
     * field
     */
    @Test
    @Issue("JENKINS-4423")
    void configuringBlockBuildWhenUpstreamBuildingRoundtrip() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.setBlockBuildWhenUpstreamBuilding(false);

        HtmlForm form = j.createWebClient().getPage(p, "configure").getFormByName("config");
        HtmlInput input = form.getInputByName("blockBuildWhenUpstreamBuilding");
        assertFalse(input.isChecked(), "blockBuildWhenUpstreamBuilding check box is checked.");

        input.setChecked(true);
        j.submit(form);
        assertTrue(p.blockBuildWhenUpstreamBuilding(), "blockBuildWhenUpstreamBuilding was not updated from configuration form");

        form = j.createWebClient().getPage(p, "configure").getFormByName("config");
        input = form.getInputByName("blockBuildWhenUpstreamBuilding");
        assertTrue(input.isChecked(), "blockBuildWhenUpstreamBuilding check box is not checked.");
    }

    /**
     * Unless the concurrent build option is enabled, polling and build should
     * be mutually exclusive to avoid allocating unnecessary workspaces.
     */
    @Test
    @Issue("JENKINS-4202")
    void pollingAndBuildExclusion() throws Exception {
        final OneShotEvent sync = new OneShotEvent();

        final FreeStyleProject p = j.createFreeStyleProject();
        FreeStyleBuild b1 = j.buildAndAssertSuccess(p);

        p.setScm(new NullSCM() {
            @Override
            public boolean pollChanges(AbstractProject project, Launcher launcher, FilePath workspace, TaskListener listener) {
                try {
                    sync.block();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                return true;
            }

            /**
             * Don't write 'this', so that subtypes can be implemented as
             * anonymous class.
             */
            private Object writeReplace() {
                return new Object();
            }

            @Override
            public boolean requiresWorkspaceForPolling() {
                return true;
            }

            @Override
            public SCMDescriptor<?> getDescriptor() {
                return new SCMDescriptor<>(null) {
                };
            }
        });
        Thread t = new Thread(() -> p.poll(StreamTaskListener.fromStdout()));
        try {
            t.start();
            Future<FreeStyleBuild> f = p.scheduleBuild2(0);

            // add a bit of delay to make sure that the blockage is happening
            Thread.sleep(3000);

            // release the polling
            sync.signal();

            FreeStyleBuild b2 = j.assertBuildStatusSuccess(f);

            // they should have used the same workspace.
            assertEquals(b1.getWorkspace(), b2.getWorkspace());
        } finally {
            t.interrupt();
        }
    }

    /* TODO too slow, seems capable of causing testWorkspaceLock to time out:
    @Test
    @Issue("JENKINS-15156")
    public void testGetBuildAfterGC() {
        FreeStyleProject job = j.createFreeStyleProject();
        job.scheduleBuild2(0, new Cause.UserIdCause()).get();
        j.jenkins.queue.clearLeftItems();
        MemoryAssert.assertGC(new WeakReference(job.getLastBuild()));
        assert job.lastBuild != null;
    }
     */

    @Test
    @Issue("JENKINS-18678")
    void renameJobLostBuilds() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject("initial");
        j.buildAndAssertSuccess(p);
        assertEquals(1, (long) p.getBuilds().size());
        p.renameTo("edited");
        p._getRuns().purgeCache();
        assertEquals(1, (long) p.getBuilds().size());
        MockFolder d = j.jenkins.createProject(MockFolder.class, "d");
        Items.move(p, d);
        assertEquals(p, j.jenkins.getItemByFullName("d/edited"));
        p._getRuns().purgeCache();
        assertEquals(1, (long) p.getBuilds().size());
        d.renameTo("d2");
        p = j.jenkins.getItemByFullName("d2/edited", FreeStyleProject.class);
        p._getRuns().purgeCache();
        assertEquals(1, (long) p.getBuilds().size());
    }

    @Test
    @Issue("JENKINS-17575")
    void deleteRedirect() throws Exception {
        j.createFreeStyleProject("j1");
        assertEquals("", deleteRedirectTarget("job/j1"));
        j.createFreeStyleProject("j2");
        Jenkins.get().addView(new AllView("v1"));
        assertEquals("view/v1/", deleteRedirectTarget("view/v1/job/j2"));
        MockFolder d = Jenkins.get().createProject(MockFolder.class, "d");
        d.addView(new AllView("v2"));
        for (String n : new String[] {"j3", "j4", "j5"}) {
            d.createProject(FreeStyleProject.class, n);
        }
        assertEquals("job/d/", deleteRedirectTarget("job/d/job/j3"));
        assertEquals("job/d/view/v2/", deleteRedirectTarget("job/d/view/v2/job/j4"));
        assertEquals("view/v1/job/d/", deleteRedirectTarget("view/v1/job/d/job/j5"));
        assertEquals("view/v1/", deleteRedirectTarget("view/v1/job/d")); // JENKINS-23375
    }

    private String deleteRedirectTarget(String job) throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient();
        String base = wc.getContextPath();
        String loc = wc.getPage(wc.addCrumb(new WebRequest(new URI(base + job + "/doDelete").toURL(), HttpMethod.POST))).getUrl().toString();
        assert loc.startsWith(base) : loc;
        return loc.substring(base.length());
    }

    @Test
    @Issue("JENKINS-18407")
    void queueSuccessBehavior() throws Exception {
        // prevent any builds to test the behaviour
        j.jenkins.setNumExecutors(0);

        FreeStyleProject p = j.createFreeStyleProject();
        Future<FreeStyleBuild> f = p.scheduleBuild2(0);
        assertNotNull(f);
        Future<FreeStyleBuild> g = p.scheduleBuild2(0);
        assertEquals(f, g);

        p.makeDisabled(true);
        assertNull(p.scheduleBuild2(0));
    }

    /**
     * Do the same as {@link #queueSuccessBehavior()} but over HTTP
     */
    @Test
    @Issue("JENKINS-18407")
    void queueSuccessBehaviorOverHTTP() throws Exception {
        // prevent any builds to test the behaviour
        j.jenkins.setNumExecutors(0);

        FreeStyleProject p = j.createFreeStyleProject();
        JenkinsRule.WebClient wc = j.createWebClient()
                .withThrowExceptionOnFailingStatusCode(false);

        WebResponse rsp = wc.getPage(wc.addCrumb(new WebRequest(new URL(j.getURL(), p.getUrl() +
                "build?delay=0"),
                HttpMethod.POST))).getWebResponse();
        assertEquals(HttpURLConnection.HTTP_CREATED, rsp.getStatusCode());
        assertNotNull(rsp.getResponseHeaderValue("Location"));

        WebResponse rsp2 = wc.getPage(wc.addCrumb(new WebRequest(new URL(j.getURL(), p.getUrl() +
                "build?delay=0"),
                HttpMethod.POST))).getWebResponse();
        assertEquals(HttpURLConnection.HTTP_CREATED, rsp2.getStatusCode());
        assertEquals(rsp.getResponseHeaderValue("Location"), rsp2.getResponseHeaderValue("Location"));

        p.makeDisabled(true);

        WebResponse rsp3 = wc.getPage(wc.addCrumb(new WebRequest(new URL(j.getURL(), p.getUrl() +
                "build?delay=0"),
                HttpMethod.POST))).getWebResponse();
        assertEquals(HttpURLConnection.HTTP_CONFLICT, rsp3.getStatusCode());
    }

    /**
     * We used to store {@link AbstractProject#triggers} as {@link Vector}, so
     * make sure we can still read back the configuration from that.
     */
    @Test
    void vectorTriggers() throws Exception {
        AbstractProject<?, ?> p = (AbstractProject<?, ?>) j.jenkins.createProjectFromXML("foo", getClass().getResourceAsStream("AbstractProjectTest/vectorTriggers.xml"));
        assertEquals(1, p.triggers().size());
        Trigger<?> t = p.triggers().getFirst();
        assertEquals(SCMTrigger.class, t.getClass());
        assertEquals("*/10 * * * *", t.getSpec());
    }

    @Test
    @Issue("JENKINS-18813")
    void removeTrigger() throws Exception {
        AbstractProject<?, ?> p = (AbstractProject<?, ?>) j.jenkins.createProjectFromXML("foo", getClass().getResourceAsStream("AbstractProjectTest/vectorTriggers.xml"));

        TriggerDescriptor SCM_TRIGGER_DESCRIPTOR = (TriggerDescriptor) j.jenkins.getDescriptorOrDie(SCMTrigger.class);
        p.removeTrigger(SCM_TRIGGER_DESCRIPTOR);
        assertEquals(0, p.triggers().size());
    }

    @Test
    @Issue("JENKINS-18813")
    void addTriggerSameType() throws Exception {
        AbstractProject<?, ?> p = (AbstractProject<?, ?>) j.jenkins.createProjectFromXML("foo", getClass().getResourceAsStream("AbstractProjectTest/vectorTriggers.xml"));

        SCMTrigger newTrigger = new SCMTrigger("H/5 * * * *");
        p.addTrigger(newTrigger);

        assertEquals(1, p.triggers().size());
        Trigger<?> t = p.triggers().getFirst();
        assertEquals(SCMTrigger.class, t.getClass());
        assertEquals("H/5 * * * *", t.getSpec());
    }

    @Test
    @Issue("JENKINS-18813")
    void addTriggerDifferentType() throws Exception {
        AbstractProject<?, ?> p = (AbstractProject<?, ?>) j.jenkins.createProjectFromXML("foo", getClass().getResourceAsStream("AbstractProjectTest/vectorTriggers.xml"));

        TimerTrigger newTrigger = new TimerTrigger("20 * * * *");
        p.addTrigger(newTrigger);

        assertEquals(2, p.triggers().size());
        Trigger<?> t = p.triggers().get(1);
        assertEquals(newTrigger, t);
    }

    /**
     * Trying to POST to config.xml by a different job type should fail.
     */
    @Test
    void configDotXmlSubmissionToDifferentType() throws Exception {
        j.jenkins.setCrumbIssuer(null);
        FreeStyleProject p = j.createFreeStyleProject();

        HttpURLConnection con = postConfigDotXml(p, "<matrix-project />");

        // this should fail with a type mismatch error
        // the error message should report both what was submitted and what was expected
        assertEquals(500, con.getResponseCode());
        String msg = IOUtils.toString(con.getErrorStream(), StandardCharsets.UTF_8);
        System.out.println(msg);
        assertThat(msg, allOf(containsString(FreeStyleProject.class.getName()), containsString(MatrixProject.class.getName())));

        // control. this should work
        con = postConfigDotXml(p, "<project />");
        assertEquals(200, con.getResponseCode());
    }

    private HttpURLConnection postConfigDotXml(FreeStyleProject p, String xml) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(j.getURL(), "job/" + p.getName() + "/config.xml").openConnection();
        con.setRequestMethod("POST");
        con.setRequestProperty("Content-Type", "application/xml; charset=utf-8");
        con.setDoOutput(true);
        try (OutputStream s = con.getOutputStream()) {
            s.write(xml.getBytes(StandardCharsets.UTF_8));
        }
        return con;
    }

    @Issue("JENKINS-21017")
    @Test
    void doConfigDotXmlReset() throws Exception {
        j.jenkins.setCrumbIssuer(null);
        FreeStyleProject p = j.createFreeStyleProject();
        p.setAssignedLabel(Label.get("whatever"));
        assertEquals("whatever", p.getAssignedLabelString());
        assertThat(p.getConfigFile().asString(), containsString("<assignedNode>whatever</assignedNode>"));
        assertEquals(200, postConfigDotXml(p, "<project/>").getResponseCode());
        assertNull(p.getAssignedLabelString()); // did not work
        assertThat(p.getConfigFile().asString(), not(containsString("<assignedNode>"))); // actually did work anyway
    }

    @Test
    @Issue("JENKINS-27549")
    void loadingWithNPEOnTriggerStart() throws Exception {
        AbstractProject<?, ?> project = (AbstractProject<?, ?>) j.jenkins.createProjectFromXML("foo", getClass().getResourceAsStream("AbstractProjectTest/npeTrigger.xml"));

        assertEquals(1, project.triggers().size());
    }

    @Test
    @Issue("JENKINS-30742")
    void resolveForCLI() throws Exception {
        CmdLineException e = assertThrows(CmdLineException.class, () -> AbstractProject.resolveForCLI("never_created"));
        assertEquals("No such job ‘never_created’ exists.", e.getMessage());

        AbstractProject<?, ?> project = j.jenkins.createProject(FreeStyleProject.class, "never_created");
        e = assertThrows(CmdLineException.class, () -> AbstractProject.resolveForCLI("never_created1"));
        assertEquals("No such job ‘never_created1’ exists. Perhaps you meant ‘never_created’?", e.getMessage());
    }

    public static class MockBuildTriggerThrowsNPEOnStart extends Trigger<Item> {
        @Override
        public void start(hudson.model.Item project, boolean newInstance) {
            throw new NullPointerException();
        }

        @TestExtension("loadingWithNPEOnTriggerStart")
        public static class DescriptorImpl extends TriggerDescriptor {

            @Override
            public boolean isApplicable(hudson.model.Item item) {
                return false;
            }
        }

    }

    @Issue("SECURITY-617")
    @Test
    void upstreamDownstreamExportApi() throws Exception {
        FreeStyleProject us = j.createFreeStyleProject("upstream-project");
        FreeStyleProject ds = j.createFreeStyleProject("downstream-project");
        us.getPublishersList().add(new BuildTrigger(Set.of(ds), Result.SUCCESS));
        j.jenkins.rebuildDependencyGraph();
        assertEquals(List.of(ds), us.getDownstreamProjects());
        assertEquals(List.of(us), ds.getUpstreamProjects());
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().
                grant(Jenkins.READ).everywhere().toEveryone().
                grant(Item.READ).everywhere().to("alice").
                grant(Item.READ).onItems(us).to("bob").
                grant(Item.READ).onItems(ds).to("charlie"));
        String api = j.createWebClient().withBasicCredentials("alice").goTo(us.getUrl() + "api/json?pretty", null).getWebResponse().getContentAsString();
        System.out.println(api);
        assertThat(api, containsString("downstream-project"));
        api = j.createWebClient().withBasicCredentials("alice").goTo(ds.getUrl() + "api/json?pretty", null).getWebResponse().getContentAsString();
        System.out.println(api);
        assertThat(api, containsString("upstream-project"));
        api = j.createWebClient().withBasicCredentials("bob").goTo(us.getUrl() + "api/json?pretty", null).getWebResponse().getContentAsString();
        System.out.println(api);
        assertThat(api, not(containsString("downstream-project")));
        api = j.createWebClient().withBasicCredentials("charlie").goTo(ds.getUrl() + "api/json?pretty", null).getWebResponse().getContentAsString();
        System.out.println(api);
        assertThat(api, not(containsString("upstream-project")));
    }

    @Test
    void ensureWhenNonExistingLabelsProposalsAreMade() throws Exception {
        j.jenkins.setCrumbIssuer(null);
        FreeStyleProject p = j.createFreeStyleProject();

        String label = "whatever";
        HtmlPage htmlPage = this.requestCheckAssignedLabelString(p, label);
        String responseContent = htmlPage.getWebResponse().getContentAsString();
        /* Sample:
         *
         * <div class=warning><img src='/jenkins/static/03a3de4a/images/none.gif' height=16 width=1>There’s no agent/cloud that
         *     matches this assignment. Did you mean ‘master’ instead of ‘whatever’?
         * </div>
         */
        assertThat(responseContent, allOf(
                containsString("warning"),
                // as there is only the built-in node that is currently used, it's de facto the nearest to whatever
                containsString("built-in"),
                containsString("whatever")
        ));
    }

    @Test
    void ensureLegitLabelsAreRetrievedCorrectly() throws Exception {
        j.jenkins.setCrumbIssuer(null);
        j.jenkins.setLabelString("existing");
        FreeStyleProject p = j.createFreeStyleProject();

        String label = "existing";
        HtmlPage htmlPage = this.requestCheckAssignedLabelString(p, label);
        String responseContent = htmlPage.getWebResponse().getContentAsString();
        /* Sample:
         *
         * <div class=ok><img src='/jenkins/static/32591acf/images/none.gif' height=16 width=1>
         *   <a href="http://localhost:5595/jenkins/label/existing/">Label existing</a>
         *   is serviced by 1 node. Permissions or other restrictions provided by plugins may prevent
         *   this job from running on those nodes.
         * </div>
         */
        assertThat(responseContent, allOf(
                containsString("ok"),
                containsString("label/existing/\">")
        ));
    }

    @Test
    @Issue("SECURITY-1781")
    void dangerousLabelsAreEscaped() throws Exception {
        j.jenkins.setCrumbIssuer(null);

        // unescaped: "\"><img src=x onerror=alert(123)>"
        String label = "\"\\\"><img src=x onerror=alert(123)>\"";
        j.jenkins.setLabelString(label);
        FreeStyleProject p = j.createFreeStyleProject();

        HtmlPage htmlPage = this.requestCheckAssignedLabelString(p, label);
        String responseContent = htmlPage.getWebResponse().getContentAsString();
        /* Sample (before correction)
         *
         * <div class=ok><img src='/jenkins/static/793045c3/images/none.gif' height=16 width=1>
         *   <a href="http://localhost:5718/jenkins/label/"><img src=x onerror=alert(123)>/">Label &quot;&gt;&lt;img src=x
         *      onerror=alert(123)&gt;</a>
         *   is serviced by 1 node. Permissions or other restrictions provided by plugins may prevent
         *   this job from running on those nodes.
         * </div>
         */
        /* Sample (after correction)
         * <div class=ok><img src='/jenkins/static/e16858e2/images/none.gif' height=16 width=1>
         *   <a href="http://localhost:6151/jenkins/label/%22%3E%3Cimg%20src=x%20onerror=alert(123)%3E/">
         *     Label &quot;&gt;&lt;img src=x onerror=alert(123)&gt;</a>
         *   is serviced by 1 node.
         *   Permissions or other restrictions provided by plugins may prevent this job from running on those nodes.
         * </div>
         */
        DomNodeList<DomNode> domNodes = htmlPage.getDocumentElement().querySelectorAll("*");
        assertThat(domNodes, hasSize(4));
        assertEquals("head", domNodes.get(0).getNodeName());
        assertEquals("body", domNodes.get(1).getNodeName());
        assertEquals("div", domNodes.get(2).getNodeName());
        assertEquals("a", domNodes.get(3).getNodeName());

        // only: "><img src=x onerror=alert(123)>
        // the first double quote was escaped during creation (with the backslash)
        String unquotedLabel = Label.parseExpression(label).getName();
        HtmlAnchor anchor = (HtmlAnchor) domNodes.get(3);
        assertThat(anchor.getHrefAttribute(), containsString(Util.rawEncode(unquotedLabel)));

        assertThat(responseContent, containsString("ok"));
    }

    @Test
    void autoCompleteUpstreamProjects() throws Exception {
        j.jenkins.setCrumbIssuer(null);
        FreeStyleProject p1 = j.createFreeStyleProject("p1");
        this.testAutoCompleteResponse(this.requestAutoCompleteUpstreamProjects(p1, "").getJSONObject(), "p1");
        this.testAutoCompleteResponse(this.requestAutoCompleteUpstreamProjects(p1, "z").getJSONObject());
        j.createFreeStyleProject("z1");
        this.testAutoCompleteResponse(this.requestAutoCompleteUpstreamProjects(p1, "").getJSONObject(), "p1", "z1");
        this.testAutoCompleteResponse(this.requestAutoCompleteUpstreamProjects(p1, "z").getJSONObject(), "z1");
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().
                grant(Jenkins.READ).everywhere().toEveryone().
                grant(Item.READ).everywhere().to("alice").
                grant(Item.READ).onItems(p1).to("bob"));
        this.testAutoCompleteResponse(this.requestAutoCompleteUpstreamProjectsWithUser(p1, "", "alice").getJSONObject(), "p1", "z1");
        this.testAutoCompleteResponse(this.requestAutoCompleteUpstreamProjectsWithUser(p1, "", "bob").getJSONObject(), "p1");
    }

    private HtmlPage requestCheckAssignedLabelString(FreeStyleProject p, String label) throws Exception {
        return j.createWebClient().goTo(p.getUrl() + p.getDescriptor().getDescriptorUrl() + "/checkAssignedLabelString?value=" + Util.rawEncode(label));
    }

    private JenkinsRule.JSONWebResponse requestAutoCompleteUpstreamProjects(FreeStyleProject p, String value) throws Exception {
        return j.getJSON(p.getUrl() + p.getDescriptor().getDescriptorUrl() + "/autoCompleteUpstreamProjects?value=" + Util.rawEncode(value));
    }

    private JenkinsRule.JSONWebResponse requestAutoCompleteUpstreamProjectsWithUser(FreeStyleProject p, String value, String user) throws Exception {
        String relativeUrl = p.getUrl() + p.getDescriptor().getDescriptorUrl() + "/autoCompleteUpstreamProjects?value=" + Util.rawEncode(value);
        Page page = j.createWebClient().withBasicCredentials(user).goTo(relativeUrl, "application/json");
        return new JenkinsRule.JSONWebResponse(page.getWebResponse());
    }

    private void testAutoCompleteResponse(JSONObject responseBody, String... projects) {
        assertThat(responseBody.containsKey("suggestions"), is(true));
        JSONArray suggestions = responseBody.getJSONArray("suggestions");
        assertThat(suggestions.size(), is(projects.length));
        List<JSONObject> expected = new ArrayList<>();
        for (String p : projects) {
            JSONObject o = new JSONObject();
            o.put("name", p);
            o.put("url", JSONObject.fromObject(null));
            o.put("icon", JSONObject.fromObject(null));
            o.put("type", "symbol");
            o.put("group", JSONObject.fromObject(null));
            expected.add(o);
        }
        assertThat(suggestions.containsAll(expected), is(true));
    }
}
