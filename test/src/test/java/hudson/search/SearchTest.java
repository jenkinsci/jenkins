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

package hudson.search;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.jvnet.hudson.test.QueryUtils.waitUntilStringIsPresent;

import hudson.model.FreeStyleProject;
import hudson.model.ListView;
import hudson.model.User;
import hudson.model.View;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.security.GlobalMatrixAuthorizationStrategy;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;
import org.htmlunit.Page;
import org.htmlunit.html.HtmlButton;
import org.htmlunit.html.HtmlInput;
import org.htmlunit.html.HtmlPage;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.MockFolder;

/**
 * @author Kohsuke Kawaguchi
 */
public class SearchTest {

    @Rule public JenkinsRule j = new JenkinsRule();

    private void searchWithoutNavigating(HtmlPage page, String query) throws IOException {
        HtmlButton button = page.querySelector("#button-open-command-palette");
        button.click();

        HtmlInput search = page.querySelector("#command-bar");
        search.setValue(query);
        page.executeJavaScript("document.querySelector('#command-bar').dispatchEvent(new Event(\"input\"))");
    }

    @Test
    public void testFailure() throws Exception {
        HtmlPage page = j.createWebClient().goTo("");

        searchWithoutNavigating(page, "no-such-thing");

        waitUntilStringIsPresent(page, "No results for no-such-thing");
    }

    /**
     * Makes sure the script doesn't execute.
     */
    @Issue("JENKINS-3415")
    @Test
    public void testXSS() throws Exception {
        WebClient wc = j.createWebClient();
        wc.setAlertHandler((page, message) -> {
            throw new AssertionError();
        });
        FreeStyleProject freeStyleProject = j.createFreeStyleProject("Project");
        freeStyleProject.setDisplayName("<script>alert('script');</script>");

        Page result = wc.search("<script>alert('script');</script>");

        assertNotNull(result);
        assertEquals(j.getInstance().getRootUrl() + freeStyleProject.getUrl(), result.getUrl().toString());
    }

    @Test
    public void testSearchByProjectName() throws Exception {
        final String projectName = "testSearchByProjectName";

        j.createFreeStyleProject(projectName);

        Page result = j.search(projectName);
        assertNotNull(result);
        j.assertGoodStatus(result);

        // make sure we've fetched the testSearchByDisplayName project page
        String contents = result.getWebResponse().getContentAsString();
        assertTrue(contents.contains(String.format("<title>%s - Jenkins</title>", projectName)));
    }

    @Issue("JENKINS-24433")
    @Test
    public void testSearchByProjectNameBehindAFolder() throws Exception {
        FreeStyleProject myFreeStyleProject = j.createFreeStyleProject("testSearchByProjectName");
        MockFolder myMockFolder = j.createFolder("my-folder-1");

        Page result = j.createWebClient().goTo(myMockFolder.getUrl() + "search?q=" + myFreeStyleProject.getName());

        assertNotNull(result);
        j.assertGoodStatus(result);

        URL resultUrl = result.getUrl();
        assertEquals(j.getInstance().getRootUrl() + myFreeStyleProject.getUrl(), resultUrl.toString());
    }

    @Issue("JENKINS-24433")
    @Test
    public void testSearchByProjectNameInAFolder() throws Exception {

        MockFolder myMockFolder = j.createFolder("my-folder-1");
        FreeStyleProject myFreeStyleProject = myMockFolder.createProject(FreeStyleProject.class, "my-job-1");

        Page result = j.search(myFreeStyleProject.getName());

        assertNotNull(result);
        j.assertGoodStatus(result);

        URL resultUrl = result.getUrl();
        assertEquals(j.getInstance().getRootUrl() + myFreeStyleProject.getUrl(), resultUrl.toString());
    }

    @Test
    public void testSearchByDisplayName() throws Exception {
        final String displayName = "displayName9999999";

        FreeStyleProject project = j.createFreeStyleProject("testSearchByDisplayName");
        project.setDisplayName(displayName);

        Page result = j.search(displayName);
        assertNotNull(result);
        j.assertGoodStatus(result);

        // make sure we've fetched the testSearchByDisplayName project page
        String contents = result.getWebResponse().getContentAsString();
        assertTrue(contents.contains(String.format("<title>%s - Jenkins</title>", displayName)));
    }

    @Test
    public void testSearch2ProjectsWithSameDisplayName() throws Exception {
        // create 2 freestyle projects with the same display name
        final String projectName1 = "projectName1";
        final String projectName2 = "projectName2";
        final String projectName3 = "projectName3";
        final String displayName = "displayNameFoo";
        final String otherDisplayName = "otherDisplayName";

        FreeStyleProject project1 = j.createFreeStyleProject(projectName1);
        project1.setDisplayName(displayName);
        FreeStyleProject project2 = j.createFreeStyleProject(projectName2);
        project2.setDisplayName(displayName);
        FreeStyleProject project3 = j.createFreeStyleProject(projectName3);
        project3.setDisplayName(otherDisplayName);

        // make sure that on search we get back one of the projects, it doesn't
        // matter which one as long as the one that is returned has displayName
        // as the display name
        Page result = j.search(displayName);
        assertNotNull(result);
        j.assertGoodStatus(result);

        // make sure we've fetched the testSearchByDisplayName project page
        String contents = result.getWebResponse().getContentAsString();
        assertTrue(contents.contains(String.format("<title>%s - Jenkins</title>", displayName)));
        assertFalse(contents.contains(otherDisplayName));
    }

    @Test
    public void testGetSuggestionsHasBothNamesAndDisplayNames() throws Exception {
        final String projectName = "project name";
        final String displayName = "display name";

        FreeStyleProject project1 = j.createFreeStyleProject(projectName);
        project1.setDisplayName(displayName);

        WebClient wc = j.createWebClient();
        Page result = wc.goTo("search/suggest?query=name", "application/json");
        assertNotNull(result);
        j.assertGoodStatus(result);

        String content = result.getWebResponse().getContentAsString();
        System.out.println(content);
        JSONObject jsonContent = (JSONObject) JSONSerializer.toJSON(content);
        assertNotNull(jsonContent);
        JSONArray jsonArray = jsonContent.getJSONArray("suggestions");
        assertNotNull(jsonArray);

        assertEquals(2, jsonArray.size());

        boolean foundProjectName = false;
        boolean foundDisplayName = false;
        for (Object suggestion : jsonArray) {
            JSONObject jsonSuggestion = (JSONObject) suggestion;

            String name = (String) jsonSuggestion.get("name");
            if (projectName.equals(name)) {
                foundProjectName = true;
            }
            else if (displayName.equals(name)) {
                foundDisplayName = true;
            }
        }

        assertTrue(foundProjectName);
        assertTrue(foundDisplayName);
    }

    @Issue("JENKINS-24433")
    @Test
    public void testProjectNameBehindAFolderDisplayName() throws Exception {
        final String projectName1 = "job-1";
        final String displayName1 = "job-1 display";

        final String projectName2 = "job-2";
        final String displayName2 = "job-2 display";

        FreeStyleProject project1 = j.createFreeStyleProject(projectName1);
        project1.setDisplayName(displayName1);

        MockFolder myMockFolder = j.createFolder("my-folder-1");

        FreeStyleProject project2 = myMockFolder.createProject(FreeStyleProject.class, projectName2);
        project2.setDisplayName(displayName2);

        WebClient wc = j.createWebClient();
        Page result = wc.goTo(myMockFolder.getUrl() + "search/suggest?query=" + projectName1, "application/json");
        assertNotNull(result);
        j.assertGoodStatus(result);

        String content = result.getWebResponse().getContentAsString();
        JSONObject jsonContent = (JSONObject) JSONSerializer.toJSON(content);
        assertNotNull(jsonContent);
        JSONArray jsonArray = jsonContent.getJSONArray("suggestions");
        assertNotNull(jsonArray);

        assertEquals(2, jsonArray.size());

        boolean foundDisplayName = false;
        for (Object suggestion : jsonArray) {
            JSONObject jsonSuggestion = (JSONObject) suggestion;

            String name = (String) jsonSuggestion.get("name");
            if (projectName1.equals(name)) {
                foundDisplayName = true;
            }
        }

        assertTrue(foundDisplayName);
    }

    @Issue("JENKINS-24433")
    @Test
    public void testProjectNameInAFolderDisplayName() throws Exception {
        final String projectName1 = "job-1";
        final String displayName1 = "job-1 display";

        final String projectName2 = "job-2";
        final String displayName2 = "my-folder-1 job-2";

        FreeStyleProject project1 = j.createFreeStyleProject(projectName1);
        project1.setDisplayName(displayName1);

        MockFolder myMockFolder = j.createFolder("my-folder-1");

        FreeStyleProject project2 = myMockFolder.createProject(FreeStyleProject.class, projectName2);
        project2.setDisplayName(displayName2);

        WebClient wc = j.createWebClient();
        Page result = wc.goTo(myMockFolder.getUrl() + "search/suggest?query=" + projectName2, "application/json");
        assertNotNull(result);
        j.assertGoodStatus(result);

        String content = result.getWebResponse().getContentAsString();
        JSONObject jsonContent = (JSONObject) JSONSerializer.toJSON(content);
        assertNotNull(jsonContent);
        JSONArray jsonArray = jsonContent.getJSONArray("suggestions");
        assertNotNull(jsonArray);

        assertEquals(1, jsonArray.size());

        boolean foundDisplayName = false;
        for (Object suggestion : jsonArray) {
            JSONObject jsonSuggestion = (JSONObject) suggestion;

            String name = (String) jsonSuggestion.get("name");

            if ("my-folder-1 » job-2".equals(name)) {
                foundDisplayName = true;
            }
        }

        assertTrue(foundDisplayName);
    }

    /**
     * Disable/enable status shouldn't affect the search
     */
    @Issue("JENKINS-13148")
    @Test
    public void testDisabledJobShouldBeSearchable() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject("foo-bar");
        assertTrue(suggest(j.jenkins.getSearchIndex(), "foo").contains(p));

        p.disable();
        assertTrue(suggest(j.jenkins.getSearchIndex(), "foo").contains(p));
    }

    /**
     * All top-level jobs should be searchable, not just jobs in the current view.
     */
    @Issue("JENKINS-13148")
    @Test
    public void testCompletionOutsideView() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject("foo-bar");
        ListView v = new ListView("empty1", j.jenkins);
        ListView w = new ListView("empty2", j.jenkins);
        j.jenkins.addView(v);
        j.jenkins.addView(w);
        j.jenkins.setPrimaryView(w);

        // new view should be empty
        assertFalse(v.contains(p));
        assertFalse(w.contains(p));
        assertFalse(j.jenkins.getPrimaryView().contains(p));

        assertTrue(suggest(j.jenkins.getSearchIndex(), "foo").contains(p));
    }

    @Issue("SECURITY-385")
    @Test
    public void testInaccessibleViews() throws IOException {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        GlobalMatrixAuthorizationStrategy strategy = new GlobalMatrixAuthorizationStrategy();
        strategy.add(Jenkins.READ, "alice");
        j.jenkins.setAuthorizationStrategy(strategy);

        j.jenkins.addView(new ListView("foo", j.jenkins));

        // SYSTEM can see all the views
        assertEquals("two views exist", 2, Jenkins.get().getViews().size());
        List<SearchItem> results = new ArrayList<>();
        j.jenkins.getSearchIndex().suggest("foo", results);
        assertEquals("nonempty results list", 1, results.size());


        // Alice can't
        assertFalse("no permission", j.jenkins.getView("foo").hasPermission2(User.get("alice").impersonate2(), View.READ));
        ACL.impersonate2(User.get("alice").impersonate2(), () -> {
            assertEquals("no visible views", 0, Jenkins.get().getViews().size());

            List<SearchItem> results1 = new ArrayList<>();
            j.jenkins.getSearchIndex().suggest("foo", results1);
            assertEquals("empty results list", Collections.emptyList(), results1);
        });
    }

    @Test
    public void testSearchWithinFolders() throws Exception {
        MockFolder folder1 = j.createFolder("folder1");
        FreeStyleProject p1 = folder1.createProject(FreeStyleProject.class, "myjob");
        MockFolder folder2 = j.createFolder("folder2");
        FreeStyleProject p2 = folder2.createProject(FreeStyleProject.class, "myjob");
        List<SearchItem> suggest = suggest(j.jenkins.getSearchIndex(), "myjob");
        assertTrue(suggest.contains(p1));
        assertTrue(suggest.contains(p2));
    }


    @Test
    @Issue("JENKINS-7874")
    public void adminOnlyLinksNotShownToRegularUser() {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        MockAuthorizationStrategy mas = new MockAuthorizationStrategy();
        mas.grant(Jenkins.READ).onRoot().toEveryone();
        j.jenkins.setAuthorizationStrategy(mas);

        try (ACLContext acl = ACL.as(User.get("alice"))) {
            List<SearchItem> results = new ArrayList<>();
            j.jenkins.getSearchIndex().find("config", results);
            j.jenkins.getSearchIndex().find("manage", results);
            j.jenkins.getSearchIndex().find("log", results);
            assertEquals("empty results list", 0, results.size());
        }
    }

    private List<SearchItem> suggest(SearchIndex index, String term) {
        List<SearchItem> result = new ArrayList<>();
        index.suggest(term, result);
        return result;
    }

    @Issue("JENKINS-35459")
    @Test
    public void testProjectNameInAListView() throws Exception {
        MockFolder myMockFolder = j.createFolder("folder");
        FreeStyleProject freeStyleProject = myMockFolder.createProject(FreeStyleProject.class, "myJob");

        ListView listView = new ListView("ListView", j.jenkins);
        listView.setRecurse(true);
        listView.add(myMockFolder);
        listView.add(freeStyleProject);

        j.jenkins.addView(listView);
        j.jenkins.setPrimaryView(listView);

        assertEquals(2, j.jenkins.getPrimaryView().getAllItems().size());

        WebClient wc = j.createWebClient();
        Page result = wc.goTo("search/suggest?query=" + freeStyleProject.getName(), "application/json");

        assertNotNull(result);
        j.assertGoodStatus(result);

        String content = result.getWebResponse().getContentAsString();
        JSONObject jsonContent = (JSONObject) JSONSerializer.toJSON(content);
        assertNotNull(jsonContent);
        JSONArray jsonArray = jsonContent.getJSONArray("suggestions");
        assertNotNull(jsonArray);

        assertEquals(2, jsonArray.size());

        Page searchResult = wc.goTo("search?q=" + myMockFolder.getName() + "%2F" + freeStyleProject.getName());

        assertNotNull(searchResult);
        j.assertGoodStatus(searchResult);

        URL resultUrl = searchResult.getUrl();
        assertEquals(j.getInstance().getRootUrl() + freeStyleProject.getUrl(), resultUrl.toString());
    }

    @Test
    @Issue("SECURITY-2399")
    public void testSearchBound() throws Exception {

        final String projectName1 = "projectName1";
        final String projectName2 = "projectName2";
        final String projectName3 = "projectName3";

        j.createFreeStyleProject(projectName1);
        j.createFreeStyleProject(projectName2);
        j.createFreeStyleProject(projectName3);

        final JenkinsRule.WebClient wc = j.createWebClient();

        Page result = wc.goTo("search/suggest?query=projectName", "application/json");
        JSONArray suggestions = getSearchJson(result);
        assertEquals(3, suggestions.size());

        Field declaredField = Search.class.getDeclaredField("MAX_SEARCH_SIZE");
        declaredField.setAccessible(true);
        declaredField.set(null, 2);

        Page maximizedResult = wc.goTo("search/suggest?query=projectName", "application/json");
        JSONArray maximizedSuggestions = getSearchJson(maximizedResult);
        assertEquals(2, maximizedSuggestions.size());
    }

    private JSONArray getSearchJson(Page page) {
        assertNotNull(page);
        j.assertGoodStatus(page);
        String content = page.getWebResponse().getContentAsString();
        JSONObject jsonContent = (JSONObject) JSONSerializer.toJSON(content);
        assertNotNull(jsonContent);
        JSONArray jsonArray = jsonContent.getJSONArray("suggestions");
        assertNotNull(jsonArray);
        return jsonArray;
    }
}
