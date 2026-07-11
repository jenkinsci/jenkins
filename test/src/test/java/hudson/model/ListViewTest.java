/*
 * The MIT License
 *
 * Copyright 2013 Jesse Glick.
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
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import hudson.Functions;
import hudson.matrix.AxisList;
import hudson.matrix.MatrixProject;
import hudson.matrix.TextAxis;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.security.AuthorizationStrategy;
import hudson.security.Permission;
import hudson.views.StatusFilter;
import hudson.views.ViewJobFilter;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import jenkins.model.Jenkins;
import org.apache.commons.io.IOUtils;
import org.htmlunit.AlertHandler;
import org.htmlunit.html.HtmlAnchor;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.jvnet.hudson.test.MockFolder;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.hudson.test.recipes.LocalData;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.springframework.security.core.Authentication;
import org.xml.sax.SAXException;

@WithJenkins
class ListViewTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Issue("JENKINS-15309")
    @LocalData
    @Test
    void nullJobNames() {
        assertTrue(j.jenkins.getView("v").getItems().isEmpty());
    }

    @Test
    void testJobLinksAreValid() throws Exception {
      /*
       * jenkins
       * + -- folder1
       *      |-- job1
       *      +-- folder2
       *          +-- job2
       */
      MockFolder folder1 = j.jenkins.createProject(MockFolder.class, "folder1");
      FreeStyleProject job1 = folder1.createProject(FreeStyleProject.class, "job1");
      MockFolder folder2 = folder1.createProject(MockFolder.class, "folder2");
      FreeStyleProject job2 = folder2.createProject(FreeStyleProject.class, "job2");

      ListView lv = new ListView("myview");
      lv.setRecurse(true);
      lv.setIncludeRegex(".*");
      j.jenkins.addView(lv);
      WebClient webClient = j.createWebClient();
      checkLinkFromViewExistsAndIsValid(folder1, j.jenkins, lv, webClient);
      checkLinkFromViewExistsAndIsValid(job1, j.jenkins, lv, webClient);
      checkLinkFromViewExistsAndIsValid(folder2, j.jenkins, lv, webClient);
      checkLinkFromViewExistsAndIsValid(job2, j.jenkins, lv, webClient);
      ListView lv2 = new ListView("myview", folder1);
      lv2.setRecurse(true);
      lv2.setIncludeRegex(".*");
      folder1.addView(lv2);
      checkLinkFromItemExistsAndIsValid(job1, folder1, folder1, webClient);
      checkLinkFromItemExistsAndIsValid(folder2, folder1, folder1, webClient);
      checkLinkFromViewExistsAndIsValid(job2, folder1, lv2, webClient);
    }

    private void checkLinkFromViewExistsAndIsValid(Item item, ItemGroup ig, View view, WebClient webClient) throws IOException, SAXException {
      HtmlPage page = webClient.goTo(view.getUrl());
      HtmlAnchor link = page.getAnchorByText(Functions.getRelativeDisplayNameFrom(item, ig));
      webClient.getPage(view, link.getHrefAttribute());
    }

    private void checkLinkFromItemExistsAndIsValid(Item item, ItemGroup ig, Item top, WebClient webClient) throws IOException, SAXException {
      HtmlPage page = webClient.goTo(top.getUrl());
      HtmlAnchor link = page.getAnchorByText(Functions.getRelativeDisplayNameFrom(item, ig));
      webClient.getPage(top, link.getHrefAttribute());
    }

    @Issue("JENKINS-20415")
    @Test
    void nonTopLevelItemGroup() throws Exception {
        MatrixProject mp = j.jenkins.createProject(MatrixProject.class, "mp");
        mp.setAxes(new AxisList(new TextAxis("axis", "one", "two")));
        assertEquals(2, mp.getItems().size());
        ListView v = new ListView("v");
        j.jenkins.addView(v);
        v.setIncludeRegex(".*");
        v.setRecurse(true);
        // Note: did not manage to reproduce CCE until I changed expand to use ‘for (TopLevelItem item : items)’ rather than ‘for (Item item : items)’; perhaps a compiler-specific issue?
        assertEquals(List.of(mp), v.getItems());
    }

    @Issue("JENKINS-18680")
    @Test
    void renamesMovesAndDeletes() throws Exception {
        MockFolder top = j.createFolder("top");
        MockFolder sub = top.createProject(MockFolder.class, "sub");
        FreeStyleProject p1 = top.createProject(FreeStyleProject.class, "p1");
        FreeStyleProject p2 = sub.createProject(FreeStyleProject.class, "p2");
        FreeStyleProject p3 = top.createProject(FreeStyleProject.class, "p3");
        ListView v = new ListView("v");
        v.setRecurse(true);
        top.addView(v);
        v.add(p1);
        v.add(p2);
        v.add(p3);
        assertEquals(new HashSet<TopLevelItem>(Arrays.asList(p1, p2, p3)), new HashSet<>(v.getItems()));
        sub.renameTo("lower");
        MockFolder stuff = top.createProject(MockFolder.class, "stuff");
        Items.move(p1, stuff);
        p3.delete();
        top.createProject(FreeStyleProject.class, "p3");
        assertEquals(new HashSet<TopLevelItem>(Arrays.asList(p1, p2)), new HashSet<>(v.getItems()));
        top.renameTo("upper");
        assertEquals(new HashSet<TopLevelItem>(Arrays.asList(p1, p2)), new HashSet<>(v.getItems()));
    }

    @Issue("JENKINS-23893")
    @Test
    void renameJobContainedInTopLevelView() throws Exception {
        ListView view = new ListView("view", j.jenkins);
        j.jenkins.addView(view);
        FreeStyleProject job = j.createFreeStyleProject("old_name");
        view.add(job);

        assertTrue(view.contains(job));
        assertTrue(view.jobNamesContains(job));

        job.renameTo("new_name");

        assertFalse(view.jobNames.contains("old_name"), "old job name is still contained: " + view.jobNames);
        assertTrue(view.contains(job));
        assertTrue(view.jobNamesContains(job));
    }

    @Test
    void renameContainedJob() throws Exception {
        MockFolder folder = j.createFolder("folder");
        ListView view = new ListView("view", folder);
        folder.addView(view);

        FreeStyleProject job = folder.createProject(FreeStyleProject.class, "old_name");
        view.add(job);

        assertTrue(view.contains(job));
        assertTrue(view.jobNamesContains(job));

        job.renameTo("new_name");

        assertFalse(view.jobNames.contains("old_name"), "old job name is still contained");
        assertTrue(view.contains(job));
        assertTrue(view.jobNamesContains(job));
    }

    @Issue("JENKINS-23893")
    @Test
    void deleteJobContainedInTopLevelView() throws Exception {
        ListView view = new ListView("view", j.jenkins);
        j.jenkins.addView(view);
        FreeStyleProject job = j.createFreeStyleProject("project");
        view.add(job);

        assertTrue(view.contains(job));
        assertTrue(view.jobNamesContains(job));

        job.delete();

        assertFalse(view.contains(job));
        assertFalse(view.jobNamesContains(job));
    }

    @Test
    void deleteContainedJob() throws Exception {
        MockFolder folder = j.createFolder("folder");
        ListView view = new ListView("view", folder);
        folder.addView(view);
        FreeStyleProject job = folder.createProject(FreeStyleProject.class, "project");
        view.add(job);

        assertTrue(view.contains(job));
        assertTrue(view.jobNamesContains(job));

        job.delete();

        assertFalse(view.contains(job));
        assertFalse(view.jobNamesContains(job));
    }

    @Issue("JENKINS-22769")
    @Test
    void renameJobInViewYouCannotSee() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new AllButViewsAuthorizationStrategy());
        final FreeStyleProject p = j.createFreeStyleProject("p1");
        ListView v = new ListView("v", j.jenkins);
        v.add(p);
        j.jenkins.addView(v);
        try (ACLContext acl = ACL.as(User.getOrCreateByIdOrFullName("alice"))) {
            p.renameTo("p2");
        }
        assertEquals(List.of(p), v.getItems());
    }

    @Issue("JENKINS-41128")
    @Test
    void addJobUsingAPI(TestInfo info) throws Exception {
        ListView v = new ListView("view", j.jenkins);
        j.jenkins.addView(v);
        StaplerRequest2 req = mock(StaplerRequest2.class);
        StaplerResponse2 rsp = mock(StaplerResponse2.class);

        String configXml = IOUtils.toString(getClass().getResourceAsStream(String.format("%s/%s/config.xml", getClass().getSimpleName(), info.getTestMethod().orElseThrow().getName())), StandardCharsets.UTF_8);

        when(req.getMethod()).thenReturn("POST");
        when(req.getParameter("name")).thenReturn("job1");
        when(req.getInputStream()).thenReturn(new Stream(IOUtils.toInputStream(configXml, StandardCharsets.UTF_8)));
        when(req.getContentType()).thenReturn("application/xml");
        v.doCreateItem(req, rsp);
        List<TopLevelItem> items = v.getItems();
        assertEquals(1, items.size());
        assertEquals("job1", items.getFirst().getName());
    }

    @Issue("JENKINS-23411")
    @Test
    void doRemoveJobFromViewNullItem() throws Exception {
        MockFolder folder = j.createFolder("folder");
        ListView view = new ListView("view", folder);
        folder.addView(view);
        FreeStyleProject job = folder.createProject(FreeStyleProject.class, "job1");
        view.add(job);

        List<TopLevelItem> items = view.getItems();
        assertEquals(1, items.size());
        assertEquals("job1", items.getFirst().getName());

        // remove a contained job
        view.doRemoveJobFromView("job1");
        List<TopLevelItem> itemsNow = view.getItems();
        assertEquals(0, itemsNow.size());

        // remove a not contained job
        Failure e = assertThrows(Failure.class, () -> view.doRemoveJobFromView("job2"));
        assertEquals("Query parameter 'name' does not correspond to a known and readable item", e.getMessage());
    }

    @Issue("JENKINS-71200")
    @Test
    void doApplyDoNotOverloadElements() throws Exception {
        MockFolder folder = j.createFolder("folder");
        FreeStyleProject job = folder.createProject(FreeStyleProject.class, "elements");
        ListView view = new ListView("view", folder);
        folder.addView(view);
        view.add(job);

        final AtomicBoolean alerts = new AtomicBoolean();
        WebClient webClient = j.createWebClient();
        webClient.setAlertHandler((AlertHandler) (page, s) -> alerts.set(true));
        HtmlPage page = webClient.goTo(view.getUrl() + "configure");
        HtmlForm form = page.getFormByName("viewConfig");
        j.assertGoodStatus(j.submit(form));
        assertFalse(alerts.get(), "No alert expected");
    }

    @Test
    void getItemsNames() throws Exception {
        MockFolder f1 = j.createFolder("f1");
        MockFolder f2 = j.createFolder("f2");
        FreeStyleProject p1 = j.createFreeStyleProject("p1");
        FreeStyleProject p2 = j.createFreeStyleProject("p2");
        FreeStyleProject p3 = f1.createProject(FreeStyleProject.class, "p3");
        FreeStyleProject p4 = f2.createProject(FreeStyleProject.class, "p4");
        ListView lv = new ListView("view", Jenkins.get());
        lv.setRecurse(false);
        Set<String> names = new TreeSet<>();
        names.add("p1");
        names.add("p2");
        names.add("f1/p3");
        names.add("f2/p4");
        lv.setJobNames(names);
        assertThat(lv.getItems(), containsInAnyOrder(p1, p2));
        lv.setRecurse(true);
        assertThat(lv.getItems(), containsInAnyOrder(p1, p2, p3, p4));
    }

    @Test
    void getItemsRegex() throws Exception {
        MockFolder f1 = j.createFolder("f1");
        MockFolder f2 = j.createFolder("f2");
        FreeStyleProject p1 = j.createFreeStyleProject("p1");
        FreeStyleProject p2 = j.createFreeStyleProject("p2");
        FreeStyleProject p3 = f1.createProject(FreeStyleProject.class, "p3");
        FreeStyleProject p4 = f2.createProject(FreeStyleProject.class, "p4");
        ListView lv = new ListView("view", Jenkins.get());
        lv.setRecurse(false);
        lv.setIncludeRegex("p.*");
        assertThat(lv.getItems(), containsInAnyOrder(p1, p2));
        lv.setRecurse(true);
        assertThat(lv.getItems(), containsInAnyOrder(p1, p2));
        lv.setIncludeRegex("f.*");
        assertThat(lv.getItems(), containsInAnyOrder(p3, p4, f1, f2));
        lv.setRecurse(false);
        assertThat(lv.getItems(), containsInAnyOrder(f1, f2));
    }

    @Test
    void withJobViewFilter() throws Exception {
        MockFolder f1 = j.createFolder("f1");
        MockFolder f2 = j.createFolder("f2");
        FreeStyleProject p1 = j.createFreeStyleProject("p1");
        FreeStyleProject p2 = j.createFreeStyleProject("p2");
        FreeStyleProject p3 = f1.createProject(FreeStyleProject.class, "p3");
        FreeStyleProject p4 = f2.createProject(FreeStyleProject.class, "p4");
        ListView lv = new ListView("view", Jenkins.get());
        lv.setJobFilters(List.of(new AllFilter()));
        lv.setRecurse(false);
        assertThat(lv.getItems(), containsInAnyOrder(f1, f2, p1, p2));
        lv.setRecurse(true);
        assertThat(lv.getItems(), containsInAnyOrder(f1, f2, p1, p2, p3, p4));
    }

    @Issue("JENKINS-62661")
    @Test
    @LocalData
    void migrateStatusFilter() {
        View v = j.jenkins.getView("testview");
        assertThat(v, notNullValue());
        assertThat(v, instanceOf(ListView.class));
        ListView lv = (ListView) v;
        StatusFilter sf = lv.getJobFilters().get(StatusFilter.class);
        assertThat(sf.getStatusFilter(), is(true));
    }

    private static final class AllFilter extends ViewJobFilter {
        @Override
        public List<TopLevelItem> filter(List<TopLevelItem> added, List<TopLevelItem> all, View filteringView) {
            return new ArrayList<>(all);
        }
    }

    private static class AllButViewsAuthorizationStrategy extends AuthorizationStrategy {
        @Override public ACL getRootACL() {
            return UNSECURED.getRootACL();
        }

        @Override public Collection<String> getGroups() {
            return Collections.emptyList();
        }

        @Override public ACL getACL(View item) {
            return new ACL() {
                @Override public boolean hasPermission2(Authentication a, Permission permission) {
                    return a.equals(SYSTEM2);
                }
            };
        }
    }

    private static class Stream extends ServletInputStream {
        private final InputStream inner;

        Stream(final InputStream inner) {
            this.inner = inner;
        }

        @Override
        public int read() throws IOException {
            return inner.read();
        }

        @Override
        public int read(byte[] b) throws IOException {
            return inner.read(b);
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            return inner.read(b, off, len);
        }

        @Override
        public boolean isFinished() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isReady() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            throw new UnsupportedOperationException();
        }
    }

}
