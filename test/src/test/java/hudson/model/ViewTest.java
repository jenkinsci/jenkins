/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Tom Huybrechts
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

import static hudson.model.Messages.Hudson_ViewName;
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.cloudbees.hudson.plugins.folder.Folder;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionList;
import hudson.XmlFile;
import hudson.diagnosis.OldDataMonitor;
import hudson.matrix.AxisList;
import hudson.matrix.LabelAxis;
import hudson.matrix.MatrixProject;
import hudson.model.Node.Mode;
import hudson.model.Queue.Task;
import hudson.security.ACL;
import hudson.security.AccessDeniedException3;
import hudson.agents.DumbAgent;
import hudson.util.FormValidation;
import hudson.util.HudsonIsLoading;
import hudson.views.ViewsTabBar;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import jenkins.model.Jenkins;
import jenkins.model.ProjectNamingStrategy;
import jenkins.security.NotReallyRoleSensitiveCallable;
import org.htmlunit.FailingHttpStatusCodeException;
import org.htmlunit.FormEncodingType;
import org.htmlunit.HttpMethod;
import org.htmlunit.Page;
import org.htmlunit.WebRequest;
import org.htmlunit.html.DomNodeUtil;
import org.htmlunit.html.HtmlAnchor;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlLabel;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.javascript.host.html.HTMLElement;
import org.htmlunit.javascript.host.svg.SVGElement;
import org.htmlunit.util.NameValuePair;
import org.jenkins.ui.icon.Icon;
import org.jenkins.ui.icon.IconSet;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Email;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.MockFolder;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.recipes.LocalData;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.w3c.dom.Text;

/**
 * @author Kohsuke Kawaguchi
 */
public class ViewTest {

    private static final String CREATE_VIEW = "create_view";
    private static final String CONFIGURATOR = "configure_user";

    @Rule public JenkinsRule j = new JenkinsRule();
    @Rule
    public LoggerRule logging = new LoggerRule();

    @Test
    public void roundTrip() throws Exception {
        ListView view = new ListView("foo");
        view.setDescription("Some description");
        view.setFilterExecutors(true);
        view.setFilterQueue(true);
        j.jenkins.addView(view);
        j.configRoundtrip(view);

        assertEquals("Some description", view.getDescription());
        assertTrue(view.isFilterExecutors());
        assertTrue(view.isFilterQueue());
    }

    @Issue("JENKINS-7100")
    @Test public void xHudsonHeader() throws Exception {
        assertNotNull(j.createWebClient().goTo("").getWebResponse().getResponseHeaderValue("X-Hudson"));
    }

    @Issue("JENKINS-43848")
    @Test public void testNoCacheHeadersAreSet() throws Exception {
        List<NameValuePair> responseHeaders = j.createWebClient()
                .goTo("view/all/itemCategories", "application/json")
                .getWebResponse()
                .getResponseHeaders();


        final Map<String, String> values = new HashMap<>();

        for (NameValuePair p : responseHeaders) {
            values.put(p.getName(), p.getValue());
        }

        String resp = values.get("Cache-Control");
        assertThat(resp, is("no-cache, no-store, must-revalidate"));
        assertThat(values.get("Expires"), is("0"));
        assertThat(values.get("Pragma"), is("no-cache"));
    }

    /**
     * Creating two views with the same name.
     */
    @Email("http://d.hatena.ne.jp/ssogabe/20090101/1230744150")
    @Test public void conflictingName() throws Exception {
        assertNull(j.jenkins.getView("foo"));

        WebClient wc = j.createWebClient();
        HtmlForm form = wc.goTo("newView").getFormByName("createItem");
        form.getInputByName("name").setValue("foo");
        form.getRadioButtonsByName("mode").get(0).setChecked(true);
        j.submit(form);
        assertNotNull(j.jenkins.getView("foo"));

        wc.setThrowExceptionOnFailingStatusCode(false);
        // do it again and verify an error
        Page page = j.submit(form);
        assertEquals("shouldn't be allowed to create two views of the same name.",
                HttpURLConnection.HTTP_BAD_REQUEST,
                page.getWebResponse().getStatusCode());
    }

    @Test public void privateView() throws Exception {
        j.createFreeStyleProject("project1");
        User user = User.get("me", true); // create user

        WebClient wc = j.createWebClient();
        HtmlPage userPage = wc.goTo("user/me");
        HtmlAnchor privateViewsLink = userPage.getAnchorByText("My Views");
        assertNotNull("My Views link not available", privateViewsLink);

        HtmlPage privateViewsPage = privateViewsLink.click();

        Text viewLabel = privateViewsPage.getFirstByXPath("//div[@class='tabBar']//div[@class='tab active']/a/text()");
        assertTrue("'All' view should be selected", viewLabel.getTextContent().contains(Hudson_ViewName()));

        View listView = listView("listView");

        HtmlPage newViewPage = wc.goTo("user/me/my-views/newView");
        HtmlForm form = newViewPage.getFormByName("createItem");
        form.getInputByName("name").setValue("proxy-view");
        form.getInputByValue("hudson.model.ProxyView").setChecked(true);
        HtmlPage proxyViewConfigurePage = j.submit(form);
        View proxyView = user.getProperty(MyViewsProperty.class).getView("proxy-view");
        assertNotNull(proxyView);
        form = proxyViewConfigurePage.getFormByName("viewConfig");
        form.getSelectByName("proxiedViewName").setSelectedAttribute("listView", true);
        j.submit(form);

        assertThat(proxyView, instanceOf(ProxyView.class));
        assertEquals("listView", ((ProxyView) proxyView).getProxiedViewName());
        assertEquals(((ProxyView) proxyView).getProxiedView(), listView);
    }

    @Issue("JENKINS-9367")
    @Test public void persistence() throws Exception {
        ListView view = listView("foo");

        ListView v = (ListView) Jenkins.XSTREAM.fromXML(Jenkins.XSTREAM.toXML(view));
        System.out.println(v.getProperties());
        assertNotNull(v.getProperties());
    }

    @Issue("JENKINS-9367")
    @Test public void allImagesCanBeLoaded() throws Exception {
        User.get("user", true);

        // as long as the cloudbees-folder is included as test dependency, its Folder will load icon
        boolean folderPluginActive = j.jenkins.getPlugin("cloudbees-folder") != null;
        // link to Folder class is done here to ensure if we remove the dependency, this code will fail and so will be removed
        boolean folderPluginClassesLoaded = j.jenkins.getDescriptor(Folder.class) != null;
        // this could be written like this to avoid the hard dependency:
        // boolean folderPluginClassesLoaded = (j.jenkins.getDescriptor("com.cloudbees.hudson.plugins.folder.Folder") != null);
        if (!folderPluginActive && folderPluginClassesLoaded) {
            // reset the icon added by Folder because the plugin resources are not reachable
            IconSet.icons.addIcon(new Icon("icon-folder icon-md", "svgs/folder.svg", "width: 24px; height: 24px;"));
        }

        WebClient webClient = j.createWebClient()
                .withThrowExceptionOnFailingStatusCode(false);
        webClient.getOptions().setJavaScriptEnabled(false);
        j.assertAllImageLoadSuccessfully(webClient.goTo("asynchPeople"));
    }

    @Issue("JENKINS-16608")
    @Test public void notAllowedName() throws Exception {
        WebClient wc = j.createWebClient()
                .withThrowExceptionOnFailingStatusCode(false);
        HtmlForm form = wc.goTo("newView").getFormByName("createItem");
        form.getInputByName("name").setValue("..");
        form.getRadioButtonsByName("mode").get(0).setChecked(true);

        HtmlPage page = j.submit(form);
        assertEquals("\"..\" should not be allowed.",
                HttpURLConnection.HTTP_BAD_REQUEST,
                page.getWebResponse().getStatusCode());
    }

    @Ignore("verified manually in Winstone but org.mortbay.JettyResponse.sendRedirect (6.1.26) seems to mangle the location")
    @Issue("JENKINS-18373")
    @Test public void unicodeName() throws Exception {
        HtmlForm form = j.createWebClient().goTo("newView").getFormByName("createItem");
        String name = "I ♥ NY";
        form.getInputByName("name").setValue(name);
        form.getRadioButtonsByName("mode").get(0).setChecked(true);
        j.submit(form);
        View view = j.jenkins.getView(name);
        assertNotNull(view);
        j.submit(j.createWebClient().getPage(view, "configure").getFormByName("viewConfig"));
    }

    @Issue("JENKINS-17302")
    @Test public void doConfigDotXml() throws Exception {
        ListView view = listView("v");
        view.description = "one";
        WebClient wc = j.createWebClient();
        String xml = wc.goToXml("view/v/config.xml").getWebResponse().getContentAsString();
        assertTrue(xml, xml.contains("<description>one</description>"));
        xml = xml.replace("<description>one</description>", "<description>two</description>");
        WebRequest req = new WebRequest(wc.createCrumbedUrl("view/v/config.xml"), HttpMethod.POST);
        req.setRequestBody(xml);
        req.setEncodingType(null);
        wc.getPage(req);
        assertEquals("two", view.getDescription());
        xml = new XmlFile(Jenkins.XSTREAM, new File(j.jenkins.getRootDir(), "config.xml")).asString();
        assertTrue(xml, xml.contains("<description>two</description>"));
    }

    @Issue("JENKINS-21017")
    @Test public void doConfigDotXmlReset() throws Exception {
        ListView view = listView("v");
        view.description = "one";
        WebClient wc = j.createWebClient();
        String xml = wc.goToXml("view/v/config.xml").getWebResponse().getContentAsString();
        assertThat(xml, containsString("<description>one</description>"));
        xml = xml.replace("<description>one</description>", "");
        WebRequest req = new WebRequest(wc.createCrumbedUrl("view/v/config.xml"), HttpMethod.POST);
        req.setRequestBody(xml);
        req.setEncodingType(null);
        wc.getPage(req);
        assertNull(view.getDescription()); // did not work
        xml = new XmlFile(Jenkins.XSTREAM, new File(j.jenkins.getRootDir(), "config.xml")).asString();
        assertThat(xml, not(containsString("<description>"))); // did not work
        assertEquals(j.jenkins, view.getOwner());
    }

    @Test
    public void testGetQueueItems() throws Exception {
        ListView view1 = listView("view1");
        view1.filterQueue = true;
        ListView view2 = listView("view2");
        view2.filterQueue = true;

        FreeStyleProject inView1 = j.createFreeStyleProject("in-view1");
        inView1.setAssignedLabel(j.jenkins.getLabelAtom("without-any-agent"));
        view1.add(inView1);

        MatrixProject inView2 = j.jenkins.createProject(MatrixProject.class, "in-view2");
        inView2.setAssignedLabel(j.jenkins.getLabelAtom("without-any-agent"));
        view2.add(inView2);

        FreeStyleProject notInView = j.createFreeStyleProject("not-in-view");
        notInView.setAssignedLabel(j.jenkins.getLabelAtom("without-any-agent"));

        FreeStyleProject inBothViews = j.createFreeStyleProject("in-both-views");
        inBothViews.setAssignedLabel(j.jenkins.getLabelAtom("without-any-agent"));
        view1.add(inBothViews);
        view2.add(inBothViews);

        Queue.getInstance().schedule(notInView, 0);
        Queue.getInstance().schedule(inView1, 0);
        Queue.getInstance().schedule(inView2, 0);
        Queue.getInstance().schedule(inBothViews, 0);

        Thread.sleep(1000);

        assertContainsItems(view1, inView1, inBothViews);
        assertNotContainsItems(view1, notInView, inView2);
        assertContainsItems(view2, inView2, inBothViews);
        assertNotContainsItems(view2, notInView, inView1);

        // Clear the queue
        assertTrue(j.jenkins.getQueue().cancel(notInView));
        assertTrue(j.jenkins.getQueue().cancel(inView1));
        assertTrue(j.jenkins.getQueue().cancel(inView2));
        assertTrue(j.jenkins.getQueue().cancel(inBothViews));
    }

    private void assertContainsItems(View view, Task... items) {
        for (Task job : items) {
            assertTrue(
                    "Queued items for " + view.getDisplayName() + " should contain " + job.getDisplayName(),
                    view.getQueueItems().contains(Queue.getInstance().getItem(job))
            );
        }
    }

    private void assertNotContainsItems(View view, Task... items) {
        for (Task job : items) {
            assertFalse(
                    "Queued items for " + view.getDisplayName() + " should not contain " + job.getDisplayName(),
                    view.getQueueItems().contains(Queue.getInstance().getItem(job))
            );
        }
    }

    @Test
    public void testGetComputers() throws Exception {
        ListView view1 = listView("view1");
        ListView view2 = listView("view2");
        ListView view3 = listView("view3");
        view1.filterExecutors = true;
        view2.filterExecutors = true;
        view3.filterExecutors = true;

        Agent agent0 = j.createOnlineAgent(j.jenkins.getLabel("label0"));
        Agent agent1 = j.createOnlineAgent(j.jenkins.getLabel("label1"));
        Agent agent2 = j.createOnlineAgent(j.jenkins.getLabel("label2"));
        Agent agent3 = j.createOnlineAgent(j.jenkins.getLabel("label0"));
        Agent agent4 = j.createOnlineAgent(j.jenkins.getLabel("label4"));

        FreeStyleProject freestyleJob = j.createFreeStyleProject("free");
        view1.add(freestyleJob);
        freestyleJob.setAssignedLabel(j.jenkins.getLabel("label0||label2"));

        MatrixProject matrixJob = j.jenkins.createProject(MatrixProject.class, "matrix");
        view1.add(matrixJob);
        matrixJob.setAxes(new AxisList(
                new LabelAxis("label", List.of("label1"))
        ));

        FreeStyleProject noLabelJob = j.createFreeStyleProject("not-assigned-label");
        view3.add(noLabelJob);
        noLabelJob.setAssignedLabel(null);

        FreeStyleProject foreignJob = j.createFreeStyleProject("in-other-view");
        view2.add(foreignJob);
        foreignJob.setAssignedLabel(j.jenkins.getLabel("label0||label1"));

        // contains all agents having labels associated with freestyleJob or matrixJob
        assertContainsNodes(view1, agent0, agent1, agent2, agent3);
        assertNotContainsNodes(view1, agent4);

        // contains all agents having labels associated with foreignJob
        assertContainsNodes(view2, agent0, agent1, agent3);
        assertNotContainsNodes(view2, agent2, agent4);

        // contains all agents as it contains noLabelJob that can run everywhere
        assertContainsNodes(view3, agent0, agent1, agent2, agent3, agent4);
    }

    @Test
    @Issue("JENKINS-21474")
    public void testGetComputersNPE() throws Exception {
        ListView view = listView("aView");
        view.filterExecutors = true;

        DumbAgent dedicatedAgent = j.createOnlineAgent();
        dedicatedAgent.setMode(Mode.EXCLUSIVE);
        view.add(j.createFreeStyleProject());

        FreeStyleProject tiedJob = j.createFreeStyleProject();
        tiedJob.setAssignedNode(dedicatedAgent);
        view.add(tiedJob);

        DumbAgent notIncludedAgent = j.createOnlineAgent();
        notIncludedAgent.setMode(Mode.EXCLUSIVE);

        assertContainsNodes(view, j.jenkins, dedicatedAgent);
        assertNotContainsNodes(view, notIncludedAgent);
    }

    private void assertContainsNodes(View view, Node... agents) {
        for (Node agent : agents) {
            assertTrue(
                    "Filtered executors for " + view.getDisplayName() + " should contain " + agent.getDisplayName(),
                    view.getComputers().contains(agent.toComputer())
            );
        }
    }

    private void assertNotContainsNodes(View view, Node... agents) {
        for (Node agent : agents) {
            assertFalse(
                    "Filtered executors for " + view.getDisplayName() + " should not contain " + agent.getDisplayName(),
                    view.getComputers().contains(agent.toComputer())
            );
        }
    }

    @Test
    public void testGetItem() throws Exception {
        ListView view = listView("foo");
        FreeStyleProject job1 = j.createFreeStyleProject("free");
        MatrixProject job2 = j.jenkins.createProject(MatrixProject.class, "matrix");
        FreeStyleProject job3 = j.createFreeStyleProject("not-included");
        view.jobNames.add(job2.getDisplayName());
        view.jobNames.add(job1.getDisplayName());
        assertEquals("View should return job " + job1.getDisplayName(), job1,  view.getItem("free"));
        assertNotNull("View should return null.", view.getItem("not-included"));
    }

    @Test
    public void testRename() throws Exception {
        ListView view = listView("foo");
        view.rename("renamed");
        assertEquals("View should have name foo.", "renamed", view.getDisplayName());
        ListView view2 = listView("foo");
        assertThrows("Attempt to rename job with a name used by another view with the same owner should throw exception", Descriptor.FormException.class, () -> view2.rename("renamed"));
        assertEquals("View should not be renamed if required name has another view with the same owner", "foo", view2.getDisplayName());
    }

    @Test
    public void testGetOwnerItemGroup() throws Exception {
        ListView view = listView("foo");
        assertEquals("View should have owner jenkins.", j.jenkins.getItemGroup(), view.getOwner().getItemGroup());
    }

    @Test
    public void testGetOwnerPrimaryView() throws Exception {
        ListView view = listView("foo");
        j.jenkins.setPrimaryView(view);
        assertEquals("View should have primary view " + view.getDisplayName(), view, view.getOwner().getPrimaryView());
    }

    @Test
    public void testSave() throws Exception {
        ListView view = listView("foo");
        FreeStyleProject job = j.createFreeStyleProject("free");
        view.jobNames.add("free");
        view.save();
        j.jenkins.doReload();
        //wait until all configuration are reloaded
        if (j.jenkins.getServletContext().getAttribute("app") instanceof HudsonIsLoading) {
            Thread.sleep(500);
        }
        assertTrue("View does not contains job free after load.", j.jenkins.getView(view.getDisplayName()).contains(j.jenkins.getItem(job.getName())));
    }

    @Test
    public void testGetProperties() throws Exception {
        View view = listView("foo");
        Thread.sleep(1000);
        HtmlForm f = j.createWebClient().getPage(view, "configure").getFormByName("viewConfig");
        ((HtmlLabel) DomNodeUtil.selectSingleNode(f, ".//LABEL[text()='Test property']")).click();
        j.submit(f);
        assertNotNull("View should contain ViewPropertyImpl property.", view.getProperties().get(PropertyImpl.class));
    }

    private ListView listView(String name) throws IOException {
        ListView view = new ListView(name, j.jenkins);
        j.jenkins.addView(view);
        return view;
    }

    public static class PropertyImpl extends ViewProperty {
        public String name;

        @DataBoundConstructor
        public PropertyImpl(String name) {
            this.name = name;
        }

        @TestExtension
        public static class DescriptorImpl extends ViewPropertyDescriptor {
            @NonNull
            @Override
            public String getDisplayName() {
                return "Test property";
            }
        }
    }

    @Issue("JENKINS-20509")
    @Test public void checkJobName() throws Exception {
        j.createFreeStyleProject("topprj");
        final MockFolder d1 = j.createFolder("d1");
        d1.createProject(FreeStyleProject.class, "subprj");
        final MockFolder d2 = j.createFolder("d2");
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().
            grant(Jenkins.ADMINISTER).everywhere().to("admin").
            grant(Jenkins.READ).everywhere().toEveryone().
            grant(Item.READ).everywhere().toEveryone().
            grant(Item.CREATE).onFolders(d1).to("dev")); // not on root or d2
        ACL.impersonate2(Jenkins.ANONYMOUS2, new NotReallyRoleSensitiveCallable<Void, Exception>() {
            @Override
            public Void call() {
                try {
                    assertCheckJobName(j.jenkins, "whatever", FormValidation.Kind.OK);
                    fail("should not have been allowed");
                } catch (AccessDeniedException3 x) {
                    // OK
                }
                return null;
            }
        });
        ACL.impersonate2(User.get("dev").impersonate2(), new NotReallyRoleSensitiveCallable<Void, Exception>() {
            @Override
            public Void call() {
                try {
                    assertCheckJobName(j.jenkins, "whatever", FormValidation.Kind.OK);
                    fail("should not have been allowed");
                } catch (AccessDeniedException3 x) {
                    // OK
                }
                try {
                    assertCheckJobName(d2, "whatever", FormValidation.Kind.OK);
                    fail("should not have been allowed");
                } catch (AccessDeniedException3 x) {
                    // OK
                }
                assertCheckJobName(d1, "whatever", FormValidation.Kind.OK);
                return null;
            }
        });
        ACL.impersonate2(User.get("admin").impersonate2(), new NotReallyRoleSensitiveCallable<Void, Exception>() {
            @Override
            public Void call() {
                assertCheckJobName(j.jenkins, "whatever", FormValidation.Kind.OK);
                assertCheckJobName(d1, "whatever", FormValidation.Kind.OK);
                assertCheckJobName(d2, "whatever", FormValidation.Kind.OK);
                assertCheckJobName(j.jenkins, "d1", FormValidation.Kind.ERROR);
                assertCheckJobName(j.jenkins, "topprj", FormValidation.Kind.ERROR);
                assertCheckJobName(d1, "subprj", FormValidation.Kind.ERROR);
                assertCheckJobName(j.jenkins, "", FormValidation.Kind.OK);
                assertCheckJobName(j.jenkins, "foo/bie", FormValidation.Kind.ERROR);
                assertCheckJobName(d2, "New", FormValidation.Kind.OK);
                j.jenkins.setProjectNamingStrategy(new ProjectNamingStrategy.PatternProjectNamingStrategy("[a-z]+", "", true));
                assertCheckJobName(d2, "New", FormValidation.Kind.ERROR);
                assertCheckJobName(d2, "new", FormValidation.Kind.OK);
                return null;
            }
        });
        JenkinsRule.WebClient wc = j.createWebClient().withBasicCredentials("admin");
        assertEquals("original ${rootURL}/checkJobName still supported", "<div/>", wc.goTo("checkJobName?value=stuff").getWebResponse().getContentAsString());
        assertEquals("but now possible on a view in a folder", "<div/>", wc.goTo("job/d1/view/All/checkJobName?value=stuff").getWebResponse().getContentAsString());
    }

    private void assertCheckJobName(ViewGroup context, String name, FormValidation.Kind expected) {
        assertEquals(expected, context.getPrimaryView().doCheckJobName(name).kind);
    }

    @Issue("JENKINS-41825")
    @Test
    public void brokenGetItems() throws Exception {
        logging.capture(100).record("", Level.INFO);
        j.jenkins.addView(new BrokenView());
        j.createWebClient().goTo("view/broken/");
        boolean found = false;
        LOGS: for (LogRecord record : logging.getRecords()) {
            for (Throwable t = record.getThrown(); t != null; t = t.getCause()) {
                if (t instanceof IllegalStateException && BrokenView.ERR.equals(t.getMessage())) {
                    found = true;
                    break LOGS;
                }
            }
        }
        assertTrue(found);
    }

    private static class BrokenView extends ListView {
        static final String ERR = "oops I cannot retrieve items";

        BrokenView() {
            super("broken");
        }

        @Override
        public List<TopLevelItem> getItems() {
            throw new IllegalStateException(ERR);
        }
    }

    @Test
    @Issue("JENKINS-36908")
    @LocalData
    public void testAllViewCreatedIfNoPrimary() {
        assertNotNull(j.getInstance().getView("All"));
    }

    @Test
    @Issue("JENKINS-36908")
    @LocalData
    public void testAllViewNotCreatedIfPrimary() {
        assertNull(j.getInstance().getView("All"));
    }

    @Test
    @Issue("JENKINS-43322")
    public void shouldFindNestedViewByName() throws Exception {
        //given
        String testNestedViewName = "right2ndNestedView";
        View right2ndNestedView = new ListView(testNestedViewName);
        //and
        View left2ndNestedView = new ListView("left2ndNestedView");
        DummyCompositeView rightNestedGroupView = new DummyCompositeView("rightNestedGroupView", left2ndNestedView, right2ndNestedView);
        //and
        listView("leftTopLevelView");
        j.jenkins.addView(rightNestedGroupView);
        //when
        View foundNestedView = j.jenkins.getView(testNestedViewName);
        //then
        assertEquals(right2ndNestedView, foundNestedView);
    }

    public void prepareSec1923() {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        MockAuthorizationStrategy mas = new MockAuthorizationStrategy();
        mas.grant(View.CREATE, View.READ, Jenkins.READ)
                .everywhere()
                .to(CREATE_VIEW);
        mas.grant(View.CONFIGURE, View.READ, Jenkins.READ)
                .everywhere()
                .to(CONFIGURATOR);
        j.jenkins.setAuthorizationStrategy(mas);
    }

    @Test
    @Issue("SECURITY-1923")
    public void simplifiedOriginalDescription() throws Exception {
        this.prepareSec1923();

        /*  This is a simplified version of the original report in SECURITY-1923.
            The XML is broken, because the root element doesn't have a matching end.
            The last line is almost a matching end, but it lacks the slash character.
            Instead that line gets interpreted as another contained element, one that
            doesn't actually exist on the class. This causes it to get logged by the
            old data monitor. */

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.login(CREATE_VIEW);

        /*  The view to create has to be nonexistent, otherwise a different code path is followed
            and the vulnerability doesn't manifest. */
        WebRequest req = new WebRequest(wc.createCrumbedUrl("createView?name=nonexistent"), HttpMethod.POST);
        req.setAdditionalHeader("Content-Type", "application/xml");
        req.setRequestBody(ORIGINAL_BAD_USER_XML);

        FailingHttpStatusCodeException e = assertThrows(FailingHttpStatusCodeException.class, () -> wc.getPage(req));
        // This really shouldn't return 500, but that's what it does now.
        assertThat(e.getStatusCode(), equalTo(500));

        // This should have a different message, but this is the current behavior demonstrating the problem.
        assertThat(e.getResponse().getContentAsString(), containsString("A problem occurred while processing the request"));

        OldDataMonitor odm = ExtensionList.lookupSingleton(OldDataMonitor.class);
        Map<Saveable, OldDataMonitor.VersionRange> data = odm.getData();

        assertThat(data.size(), equalTo(0));

        odm.doDiscard(null, null);

        View view = j.getInstance().getView("nonexistent");

        // The view should still be nonexistent, as we gave it a user and not a view.
        assertNull("Should not have created view.", view);

        User.AllUsers.scanAll();
        boolean createUser = false;
        User badUser = User.getById("foo", createUser);

        assertNull("Should not have created user.", badUser);
    }

    @Test
    @Issue("SECURITY-1923")
    public void simplifiedWithValidXmlAndBadField() throws Exception {
        this.prepareSec1923();

        /*  This is the same thing as the original report, except it uses valid XML.
            It just adds in additional invalid field, which gets picked up by the old data monitor.
            Way too much duplicated code here, but this is just for demonstration. */

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.login(CREATE_VIEW);

        /*  The view to create has to be nonexistent, otherwise a different code path is followed
            and the vulnerability doesn't manifest. */
        WebRequest req = new WebRequest(wc.createCrumbedUrl("createView?name=nonexistent"), HttpMethod.POST);
        req.setAdditionalHeader("Content-Type", "application/xml");
        req.setRequestBody(VALID_XML_BAD_FIELD_USER_XML);

        FailingHttpStatusCodeException e = assertThrows(FailingHttpStatusCodeException.class, () -> wc.getPage(req));
        // This really shouldn't return 500, but that's what it does now.
        assertThat(e.getStatusCode(), equalTo(500));

        // This should have a different message, but this is the current behavior demonstrating the problem.
        assertThat(e.getResponse().getContentAsString(), containsString("A problem occurred while processing the request"));

        OldDataMonitor odm = ExtensionList.lookupSingleton(OldDataMonitor.class);
        Map<Saveable, OldDataMonitor.VersionRange> data = odm.getData();

        assertThat(data.size(), equalTo(0));

        odm.doDiscard(null, null);

        View view = j.getInstance().getView("nonexistent");

        // The view should still be nonexistent, as we gave it a user and not a view.
        assertNull("Should not have created view.", view);

        User.AllUsers.scanAll();
        boolean createUser = false;
        User badUser = User.getById("foo", createUser);

        assertNull("Should not have created user.", badUser);
    }

    @Test
    @Issue("SECURITY-1923")
    public void configDotXmlWithValidXmlAndBadField() throws Exception {
        this.prepareSec1923();

        ListView view = new ListView("view1", j.jenkins);
        j.jenkins.addView(view);

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.login(CONFIGURATOR);
        WebRequest req = new WebRequest(wc.createCrumbedUrl(String.format("%s/config.xml", view.getUrl())), HttpMethod.POST);
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

    private static final String ORIGINAL_BAD_USER_XML =
            "<hudson.model.User>\n" +
                    "  <id>foo</id>\n" +
                    "  <fullName>Foo User</fullName>\n" +
                    "<hudson.model.User>\n";


    @Test
    @Issue("SECURITY-2171")
    public void newJob_xssPreventedInId() throws Exception {
        CustomizableTLID customizableTLID = j.jenkins.getExtensionList(TopLevelItemDescriptor.class).get(CustomizableTLID.class);
        customizableTLID.customId = "regularclass\" onclick=alert(123) other=\"";
        customizableTLID.customDisplayName = "DN-xss-id";

        JenkinsRule.WebClient wc = j.createWebClient();

        HtmlPage page = wc.goTo("view/all/newJob");

        Object result = page.executeJavaScript("Array.from(document.querySelectorAll('.label')).filter(el => el.innerText.indexOf('" + customizableTLID.customDisplayName + "') !== -1)[0].parentElement.parentElement").getJavaScriptResult();
        assertThat(result, instanceOf(HTMLElement.class));
        HTMLElement resultElement = (HTMLElement) result;
        assertThat(resultElement.getAttribute("onclick"), nullValue());
    }

    @Test
    @Issue("SECURITY-2171")
    public void newJob_xssPreventedInDisplayName() throws Exception {
        CustomizableTLID customizableTLID = j.jenkins.getExtensionList(TopLevelItemDescriptor.class).get(CustomizableTLID.class);
        customizableTLID.customId = "xss-dn";
        customizableTLID.customDisplayName = "DN <img src=x onerror=console.warn(123)>";

        JenkinsRule.WebClient wc = j.createWebClient();

        HtmlPage page = wc.goTo("view/all/newJob");

        Object result = page.executeJavaScript("document.querySelector('.xss-dn .label').innerHTML").getJavaScriptResult();
        assertThat(result, instanceOf(String.class));
        String resultString = (String) result;
        assertThat(resultString, not(containsString("<")));
    }

    @Test
    public void newJob_descriptionSupportsHtml() throws Exception {
        CustomizableTLID customizableTLID = j.jenkins.getExtensionList(TopLevelItemDescriptor.class).get(CustomizableTLID.class);
        customizableTLID.customId = "html-desc";
        customizableTLID.customDescription = "Super <strong>looong</strong> description";

        JenkinsRule.WebClient wc = j.createWebClient();

        HtmlPage page = wc.goTo("view/all/newJob");

        Object result = page.executeJavaScript("document.querySelector('.html-desc .desc strong')").getJavaScriptResult();
        assertThat(result, instanceOf(HTMLElement.class));
        assertThat(((HTMLElement) result).getTagName(), is("STRONG"));
    }

    @Test
    @Issue("SECURITY-2171")
    public void newJob_xssPreventedInGetIconFilePathPattern() throws Exception {
        CustomizableTLID customizableTLID = j.jenkins.getExtensionList(TopLevelItemDescriptor.class).get(CustomizableTLID.class);
        customizableTLID.customId = "xss-ifpp";
        customizableTLID.customIconClassName = null;
        customizableTLID.customIconFilePathPattern = "\"><img src=x onerror=\"alert(123)";

        JenkinsRule.WebClient wc = j.createWebClient();

        HtmlPage page = wc.goTo("view/all/newJob");

        Object resultIconChildrenCount = page.executeJavaScript("document.querySelector('." + customizableTLID.customId + " .icon').children.length").getJavaScriptResult();
        assertThat(resultIconChildrenCount, instanceOf(Integer.class));
        int resultIconChildrenCountInt = (int) resultIconChildrenCount;
        assertEquals(1, resultIconChildrenCountInt);

        Object resultImgAttributesCount = page.executeJavaScript("document.querySelector('." + customizableTLID.customId + " .icon img').attributes.length").getJavaScriptResult();
        assertThat(resultImgAttributesCount, instanceOf(Integer.class));
        int resultImgAttributesCountInt = (int) resultImgAttributesCount;
        assertEquals(1, resultImgAttributesCountInt);
    }

    @Test
    @Issue("SECURITY-1871")
    public void shouldNotAllowInconsistentViewName() throws IOException {
        assertNull(j.jenkins.getView("ViewName"));
        JenkinsRule.WebClient wc = j.createWebClient();
        WebRequest req = new WebRequest(wc.createCrumbedUrl("createView"), HttpMethod.POST);
        req.setEncodingType(FormEncodingType.URL_ENCODED);
        req.setRequestBody("name=ViewName&mode=hudson.model.ListView&json=" + URLEncoder.encode("{\"mode\":\"hudson.model.ListView\",\"name\":\"DifferentViewName\"}", StandardCharsets.UTF_8));
        wc.getPage(req);
        assertNull(j.jenkins.getView("DifferentViewName"));
        assertNotNull(j.jenkins.getView("ViewName"));
    }

    @Test
    public void newJob_iconClassName() throws Exception {

        CustomizableTLID customizableTLID = j.jenkins.getExtensionList(TopLevelItemDescriptor.class).get(CustomizableTLID.class);
        customizableTLID.customId = "with_Icon";
        customizableTLID.customIconClassName = "icon-freestyle-project";

        JenkinsRule.WebClient wc = j.createWebClient();

        HtmlPage page = wc.goTo("view/all/newJob");

        Object resultSrc = page.executeJavaScript("document.querySelector('." + customizableTLID.customId + " .icon img').src").getJavaScriptResult();

        assertThat(resultSrc, instanceOf(String.class));
        String resultSrcString = (String) resultSrc;
        assertThat(resultSrcString, containsString("48x48"));
        assertThat(resultSrcString, containsString("freestyleproject.png"));
    }

    @Test
    public void newJob_svg() throws Exception {

        JenkinsRule.WebClient wc = j.createWebClient();

        HtmlPage page = wc.goTo("view/all/newJob");

        Object result = page.executeJavaScript("document.querySelector('.hudson_model_FreeStyleProject .icon svg')").getJavaScriptResult();
        assertThat(result, instanceOf(SVGElement.class));
        SVGElement svg = (SVGElement) result;
        assertThat(svg.getClassName_js(), is("icon-xlg"));
    }

    @Test
    public void newJob_twoLetterIcon() throws Exception {
        CustomizableTLID customizableTLID = j.jenkins.getExtensionList(TopLevelItemDescriptor.class).get(CustomizableTLID.class);
        customizableTLID.customId = "two-letters-desc";
        customizableTLID.customDisplayName = "Two words";
        customizableTLID.customIconClassName = null;
        customizableTLID.customIconFilePathPattern = null;

        JenkinsRule.WebClient wc = j.createWebClient();

        HtmlPage page = wc.goTo("view/all/newJob");

        Object result = page.executeJavaScript("document.querySelector('." + customizableTLID.customId + " .default-icon')").getJavaScriptResult();
        assertThat(result, instanceOf(HTMLElement.class));
        HTMLElement resultHtml = (HTMLElement) result;
        HTMLElement spanA = (HTMLElement) resultHtml.getFirstElementChild();
        HTMLElement spanB = (HTMLElement) resultHtml.getLastElementChild();
        assertThat(spanA.getClassName_js(), is("a"));
        assertThat(spanA.getInnerText(), is("T"));
        assertThat(spanB.getClassName_js(), is("b"));
        assertThat(spanB.getInnerText(), is("w"));
    }

    @TestExtension
    public static class CustomizableTLID extends TopLevelItemDescriptor {

        public String customId = "ID-not-yet-defined";
        public String customDisplayName = "DisplayName-not-yet-defined";
        public String customDescription = "Description-not-yet-defined";
        public String customIconFilePathPattern = "IconFilePathPattern-not-yet-defined";
        public String customIconClassName = "IconClassName-not-yet-defined";

        public CustomizableTLID() {
            super(FreeStyleProject.class);
        }

        @Override
        public String getId() {
            return customId;
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return customDisplayName;
        }

        @Override
        public String getDescription() {
            return customDescription;
        }

        @Override
        public @CheckForNull String getIconFilePathPattern() {
            return customIconFilePathPattern;
        }

        @Override
        public String getIconClassName() {
            return customIconClassName;
        }

        @Override
        public TopLevelItem newInstance(ItemGroup parent, String name) {
            throw new UnsupportedOperationException();
        }

    }

    //Duplication with ViewTest.CompositeView from core unit test module - unfortunately it is inaccessible from here
    private static class DummyCompositeView extends View implements ViewGroup {

        private final List<View> views;
        private List<TopLevelItem> jobs;
        private String primaryView;

        private final transient ViewGroupMixIn viewGroupMixIn = new ViewGroupMixIn(this) {
            @Override
            protected List<View> views() { return views; }

            @Override
            protected String primaryView() { return primaryView; }

            @Override
            protected void primaryView(String name) { primaryView = name; }
        };

        DummyCompositeView(final String name, View... views) {
            super(name);
            this.primaryView = views[0].getViewName();
            this.views = asList(views);
        }

        private DummyCompositeView withJobs(TopLevelItem... jobs) {
            this.jobs = asList(jobs);
            return this;
        }

        @Override
        public Collection<TopLevelItem> getItems() {
            return this.jobs;
        }

        @Override
        public Collection<View> getViews() {
            return viewGroupMixIn.getViews();
        }

        @Override
        public boolean canDelete(View view) {
            return viewGroupMixIn.canDelete(view);
        }

        @Override
        public void deleteView(View view) throws IOException {
            viewGroupMixIn.deleteView(view);
        }

        @Override
        public View getView(String name) {
            return viewGroupMixIn.getView(name);
        }

        @Override
        public View getPrimaryView() {
            return viewGroupMixIn.getPrimaryView();
        }

        @Override
        public void onViewRenamed(View view, String oldName, String newName) {
            viewGroupMixIn.onViewRenamed(view, oldName, newName);
        }

        @Override
        public ViewsTabBar getViewsTabBar() {
            return null;
        }

        @Override
        public ItemGroup<? extends TopLevelItem> getItemGroup() {
            return null;
        }

        @Override
        public List<Action> getViewActions() {
            return null;
        }

        @Override
        public boolean contains(TopLevelItem item) {
            return false;
        }

        @Override
        protected void submit(StaplerRequest2 req) {
        }

        @Override
        public Item doCreateItem(StaplerRequest2 req, StaplerResponse2 rsp) {
            return null;
        }
    }
}
