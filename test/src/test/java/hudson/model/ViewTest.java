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

import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.html.DomNodeUtil;
import jenkins.model.Jenkins;
import org.jvnet.hudson.test.Issue;
import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlLabel;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlRadioButtonInput;
import hudson.XmlFile;
import hudson.matrix.AxisList;
import hudson.matrix.LabelAxis;
import hudson.matrix.MatrixProject;
import hudson.model.Queue.Task;
import hudson.model.Node.Mode;

import org.jvnet.hudson.test.Email;
import org.w3c.dom.Text;

import static hudson.model.Messages.Hudson_ViewName;
import hudson.security.ACL;
import hudson.security.AccessDeniedException2;
import hudson.slaves.DumbSlave;
import hudson.util.FormValidation;
import hudson.util.HudsonIsLoading;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import jenkins.model.ProjectNamingStrategy;
import jenkins.security.NotReallyRoleSensitiveCallable;
import static org.junit.Assert.*;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.MockFolder;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.recipes.LocalData;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * @author Kohsuke Kawaguchi
 */
public class ViewTest {

    @Rule public JenkinsRule j = new JenkinsRule();

    @Issue("JENKINS-7100")
    @Test public void xHudsonHeader() throws Exception {
        assertNotNull(j.createWebClient().goTo("").getWebResponse().getResponseHeaderValue("X-Hudson"));
    }

    /**
     * Creating two views with the same name.
     */
    @Email("http://d.hatena.ne.jp/ssogabe/20090101/1230744150")
    @Test public void conflictingName() throws Exception {
        assertNull(j.jenkins.getView("foo"));

        HtmlForm form = j.createWebClient().goTo("newView").getFormByName("createItem");
        form.getInputByName("name").setValueAttribute("foo");
        form.getRadioButtonsByName("mode").get(0).setChecked(true);
        j.submit(form);
        assertNotNull(j.jenkins.getView("foo"));

        // do it again and verify an error
        try {
            j.submit(form);
            fail("shouldn't be allowed to create two views of the same name.");
        } catch (FailingHttpStatusCodeException e) {
            assertEquals(400, e.getStatusCode());
        }
    }

    @Test public void privateView() throws Exception {
        j.createFreeStyleProject("project1");
        User user = User.get("me", true); // create user

        WebClient wc = j.createWebClient();
        HtmlPage userPage = wc.goTo("user/me");
        HtmlAnchor privateViewsLink = userPage.getAnchorByText("My Views");
        assertNotNull("My Views link not available", privateViewsLink);

        HtmlPage privateViewsPage = (HtmlPage) privateViewsLink.click();

        Text viewLabel = (Text) privateViewsPage.getFirstByXPath("//div[@class='tabBar']//div[@class='tab active']/a/text()");
        assertTrue("'All' view should be selected", viewLabel.getTextContent().contains(Hudson_ViewName()));

        View listView = listView("listView");

        HtmlPage newViewPage = wc.goTo("user/me/my-views/newView");
        HtmlForm form = newViewPage.getFormByName("createItem");
        form.getInputByName("name").setValueAttribute("proxy-view");
        ((HtmlRadioButtonInput) form.getInputByValue("hudson.model.ProxyView")).setChecked(true);
        HtmlPage proxyViewConfigurePage = j.submit(form);
        View proxyView = user.getProperty(MyViewsProperty.class).getView("proxy-view");
        assertNotNull(proxyView);
        form = proxyViewConfigurePage.getFormByName("viewConfig");
        form.getSelectByName("proxiedViewName").setSelectedAttribute("listView", true);
        j.submit(form);

        assertTrue(proxyView instanceof ProxyView);
        assertEquals(((ProxyView) proxyView).getProxiedViewName(), "listView");
        assertEquals(((ProxyView) proxyView).getProxiedView(), listView);
    }

    @Test public void deleteView() throws Exception {
        WebClient wc = j.createWebClient();

        ListView v = listView("list");
        HtmlPage delete = wc.getPage(v, "delete");
        j.submit(delete.getFormByName("delete"));
        assertNull(j.jenkins.getView("list"));

        User user = User.get("user", true);
        MyViewsProperty p = user.getProperty(MyViewsProperty.class);
        v = new ListView("list", p);
        p.addView(v);
        delete = wc.getPage(v, "delete");
        j.submit(delete.getFormByName("delete"));
        assertNull(p.getView("list"));

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
        WebClient webClient = j.createWebClient();
        webClient.getOptions().setJavaScriptEnabled(false);
        j.assertAllImageLoadSuccessfully(webClient.goTo("asynchPeople"));
    }

    @Issue("JENKINS-16608")
    @Test public void notAllowedName() throws Exception {
        HtmlForm form = j.createWebClient().goTo("newView").getFormByName("createItem");
        form.getInputByName("name").setValueAttribute("..");
        form.getRadioButtonsByName("mode").get(0).setChecked(true);

        try {
            j.submit(form);
            fail("\"..\" should not be allowed.");
        } catch (FailingHttpStatusCodeException e) {
            assertEquals(400, e.getStatusCode());
        }
    }

    @Ignore("verified manually in Winstone but org.mortbay.JettyResponse.sendRedirect (6.1.26) seems to mangle the location")
    @Issue("JENKINS-18373")
    @Test public void unicodeName() throws Exception {
        HtmlForm form = j.createWebClient().goTo("newView").getFormByName("createItem");
        String name = "I ♥ NY";
        form.getInputByName("name").setValueAttribute(name);
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
        String xml = wc.goToXml("view/v/config.xml").getContent();
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
    
    @Test
    public void testGetQueueItems() throws IOException, Exception{
        ListView view1 = listView("view1");
        view1.filterQueue = true;
        ListView view2 = listView("view2");
        view2.filterQueue = true;

        FreeStyleProject inView1 = j.createFreeStyleProject("in-view1");
        inView1.setAssignedLabel(j.jenkins.getLabelAtom("without-any-slave"));
        view1.add(inView1);

        MatrixProject inView2 = j.jenkins.createProject(MatrixProject.class, "in-view2");
        inView2.setAssignedLabel(j.jenkins.getLabelAtom("without-any-slave"));
        view2.add(inView2);

        FreeStyleProject notInView = j.createFreeStyleProject("not-in-view");
        notInView.setAssignedLabel(j.jenkins.getLabelAtom("without-any-slave"));

        FreeStyleProject inBothViews = j.createFreeStyleProject("in-both-views");
        inBothViews.setAssignedLabel(j.jenkins.getLabelAtom("without-any-slave"));
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
    }

    private void assertContainsItems(View view, Task... items) {
        for (Task job: items) {
            assertTrue(
                    "Queued items for " + view.getDisplayName() + " should contain " + job.getDisplayName(),
                    view.getQueueItems().contains(Queue.getInstance().getItem(job))
            );
        }
    }

    private void assertNotContainsItems(View view, Task... items) {
        for (Task job: items) {
            assertFalse(
                    "Queued items for " + view.getDisplayName() + " should not contain " + job.getDisplayName(),
                    view.getQueueItems().contains(Queue.getInstance().getItem(job))
            );
        }
    }
    
    @Test
    public void testGetComputers() throws IOException, Exception{
        ListView view1 = listView("view1");
        ListView view2 = listView("view2");
        ListView view3 = listView("view3");
        view1.filterExecutors=true;
        view2.filterExecutors=true;
        view3.filterExecutors=true;

        Slave slave0 = j.createOnlineSlave(j.jenkins.getLabel("label0"));
        Slave slave1 = j.createOnlineSlave(j.jenkins.getLabel("label1"));
        Slave slave2 = j.createOnlineSlave(j.jenkins.getLabel("label2"));
        Slave slave3 = j.createOnlineSlave(j.jenkins.getLabel("label0"));
        Slave slave4 = j.createOnlineSlave(j.jenkins.getLabel("label4"));

        FreeStyleProject freestyleJob = j.createFreeStyleProject("free");
        view1.add(freestyleJob);
        freestyleJob.setAssignedLabel(j.jenkins.getLabel("label0||label2"));

        MatrixProject matrixJob = j.jenkins.createProject(MatrixProject.class, "matrix");
        view1.add(matrixJob);
        matrixJob.setAxes(new AxisList(
                new LabelAxis("label", Arrays.asList("label1"))
        ));

        FreeStyleProject noLabelJob = j.createFreeStyleProject("not-assigned-label");
        view3.add(noLabelJob);
        noLabelJob.setAssignedLabel(null);

        FreeStyleProject foreignJob = j.createFreeStyleProject("in-other-view");
        view2.add(foreignJob);
        foreignJob.setAssignedLabel(j.jenkins.getLabel("label0||label1"));

        // contains all slaves having labels associated with freestyleJob or matrixJob
        assertContainsNodes(view1, slave0, slave1, slave2, slave3);
        assertNotContainsNodes(view1, slave4);

        // contains all slaves having labels associated with foreignJob
        assertContainsNodes(view2, slave0, slave1, slave3);
        assertNotContainsNodes(view2, slave2, slave4);

        // contains all slaves as it contains noLabelJob that can run everywhere
        assertContainsNodes(view3, slave0, slave1, slave2, slave3, slave4);
    }

    @Test
    @Issue("JENKINS-21474")
    public void testGetComputersNPE() throws Exception {
        ListView view = listView("aView");
        view.filterExecutors = true;

        DumbSlave dedicatedSlave = j.createOnlineSlave();
        dedicatedSlave.setMode(Mode.EXCLUSIVE);
        view.add(j.createFreeStyleProject());

        FreeStyleProject tiedJob = j.createFreeStyleProject();
        tiedJob.setAssignedNode(dedicatedSlave);
        view.add(tiedJob);

        DumbSlave notIncludedSlave = j.createOnlineSlave();
        notIncludedSlave.setMode(Mode.EXCLUSIVE);

        assertContainsNodes(view, j.jenkins, dedicatedSlave);
        assertNotContainsNodes(view, notIncludedSlave);
    }

    private void assertContainsNodes(View view, Node... slaves) {
        for (Node slave: slaves) {
            assertTrue(
                    "Filtered executors for " + view.getDisplayName() + " should contain " + slave.getDisplayName(),
                    view.getComputers().contains(slave.toComputer())
            );
        }
    }

    private void assertNotContainsNodes(View view, Node... slaves) {
        for (Node slave: slaves) {
            assertFalse(
                    "Filtered executors for " + view.getDisplayName() + " should not contain " + slave.getDisplayName(),
                    view.getComputers().contains(slave.toComputer())
            );
        }
    }

    @Test
    public void testGetItem() throws Exception{
        ListView view = listView("foo");
        FreeStyleProject job1 = j.createFreeStyleProject("free");
        MatrixProject job2 = j.jenkins.createProject(MatrixProject.class, "matrix");
        FreeStyleProject job3 = j.createFreeStyleProject("not-included");
        view.jobNames.add(job2.getDisplayName());
        view.jobNames.add(job1.getDisplayName());
        assertEquals("View should return job " + job1.getDisplayName(),job1,  view.getItem("free"));
        assertNotNull("View should return null.", view.getItem("not-included"));
    }
    
    @Test
    public void testRename() throws Exception {
        ListView view = listView("foo");
        view.rename("renamed");
        assertEquals("View should have name foo.", "renamed", view.getDisplayName());
        ListView view2 = listView("foo");
        try{
            view2.rename("renamed");
            fail("Attempt to rename job with a name used by another view with the same owner should throw exception");
        }
        catch(Exception Exception){
        }
        assertEquals("View should not be renamed if required name has another view with the same owner", "foo", view2.getDisplayName());
    }
    
    @Test
    public void testGetOwnerItemGroup() throws Exception {
        ListView view = listView("foo");
        assertEquals("View should have owner jenkins.",j.jenkins.getItemGroup(), view.getOwnerItemGroup());
    }
    
    @Test
    public void testGetOwnerPrimaryView() throws Exception{
        ListView view = listView("foo");
        j.jenkins.setPrimaryView(view);
        assertEquals("View should have primary view " + view.getDisplayName(),view, view.getOwnerPrimaryView());
    }
    
    @Test
    public void testSave() throws Exception{
        ListView view = listView("foo");
        FreeStyleProject job = j.createFreeStyleProject("free");
        view.jobNames.add("free");
        view.save();
        j.jenkins.doReload();
        //wait until all configuration are reloaded
        if(j.jenkins.servletContext.getAttribute("app") instanceof HudsonIsLoading){
            Thread.sleep(500);
        }
        assertTrue("View does not contains job free after load.", j.jenkins.getView(view.getDisplayName()).contains(j.jenkins.getItem(job.getName())));       
    }
    
    @Test
    public void testGetProperties() throws Exception {
        View view = listView("foo");
        Thread.sleep(100000);
        HtmlForm f = j.createWebClient().getPage(view, "configure").getFormByName("viewConfig");
        ((HtmlLabel) DomNodeUtil.selectSingleNode(f, ".//LABEL[text()='Test property']")).click();
        j.submit(f);
        assertNotNull("View should contains ViewPropertyImpl property.", view.getProperties().get(PropertyImpl.class));
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
            grant(Job.READ).everywhere().toEveryone().
            grant(Item.CREATE).onFolders(d1).to("dev")); // not on root or d2
        ACL.impersonate(Jenkins.ANONYMOUS, new NotReallyRoleSensitiveCallable<Void,Exception>() {
            @Override
            public Void call() throws Exception {
                try {
                    assertCheckJobName(j.jenkins, "whatever", FormValidation.Kind.OK);
                    fail("should not have been allowed");
                } catch (AccessDeniedException2 x) {
                    // OK
                }
                return null;
            }
        });
        ACL.impersonate(User.get("dev").impersonate(), new NotReallyRoleSensitiveCallable<Void,Exception>() {
            @Override
            public Void call() throws Exception {
                try {
                    assertCheckJobName(j.jenkins, "whatever", FormValidation.Kind.OK);
                    fail("should not have been allowed");
                } catch (AccessDeniedException2 x) {
                    // OK
                }
                try {
                    assertCheckJobName(d2, "whatever", FormValidation.Kind.OK);
                    fail("should not have been allowed");
                } catch (AccessDeniedException2 x) {
                    // OK
                }
                assertCheckJobName(d1, "whatever", FormValidation.Kind.OK);
                return null;
            }
        });
        ACL.impersonate(User.get("admin").impersonate(), new NotReallyRoleSensitiveCallable<Void,Exception>() {
            @Override
            public Void call() throws Exception {
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
        JenkinsRule.WebClient wc = j.createWebClient().login("admin");
        assertEquals("original ${rootURL}/checkJobName still supported", "<div/>", wc.goTo("checkJobName?value=stuff").getWebResponse().getContentAsString());
        assertEquals("but now possible on a view in a folder", "<div/>", wc.goTo("job/d1/view/All/checkJobName?value=stuff").getWebResponse().getContentAsString());
    }

    private void assertCheckJobName(ViewGroup context, String name, FormValidation.Kind expected) {
        assertEquals(expected, context.getPrimaryView().doCheckJobName(name).kind);
    }
    
    
    @Test
    @Issue("JENKINS-36908")
    @LocalData
    public void testAllViewCreatedIfNoPrimary() throws Exception {
        assertNotNull(j.getInstance().getView("All"));
    }
    
    @Test
    @Issue("JENKINS-36908")
    @LocalData
    public void testAllViewNotCreatedIfPrimary() throws Exception {
        assertNull(j.getInstance().getView("All"));
    }

}
