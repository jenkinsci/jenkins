/*
 * The MIT License
 *
 * Copyright (c) 2014 Red Hat, Inc.
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import org.hamcrest.Matchers;
import org.htmlunit.HttpMethod;
import org.htmlunit.Page;
import org.htmlunit.WebRequest;
import org.htmlunit.WebResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.jvnet.hudson.test.MockFolder;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class DirectlyModifiableViewTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void manipulateViewContent() throws IOException {
        FreeStyleProject projectA = j.createFreeStyleProject("projectA");
        FreeStyleProject projectB = j.createFreeStyleProject("projectB");

        ListView view = new ListView("a_view", j.jenkins);
        j.jenkins.addView(view);

        assertFalse(view.contains(projectA));
        assertFalse(view.contains(projectB));

        view.add(projectA);
        assertTrue(view.contains(projectA));
        assertFalse(view.contains(projectB));

        view.add(projectB);
        assertTrue(view.contains(projectA));
        assertTrue(view.contains(projectB));

        assertTrue(view.remove(projectA));
        assertFalse(view.contains(projectA));
        assertTrue(view.contains(projectB));

        assertTrue(view.remove(projectB));
        assertFalse(view.contains(projectA));
        assertFalse(view.contains(projectB));

        assertFalse(view.remove(projectB));
    }

    @Test
    void doAddJobToView() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject("a_project");
        ListView view = new ListView("a_view", j.jenkins);
        j.jenkins.addView(view);

        assertFalse(view.contains(project));

        Page page = doPost(view, "addJobToView?name=a_project");
        j.assertGoodStatus(page);
        assertTrue(view.contains(project));

        page = doPost(view, "addJobToView?name=a_project");
        j.assertGoodStatus(page);
        assertTrue(view.contains(project));
    }

    @Test
    void doAddNestedJobToRecursiveView() throws Exception {
        ListView view = new ListView("a_view", j.jenkins);
        view.setRecurse(true);
        j.jenkins.addView(view);

        MockFolder folder = j.createFolder("folder");
        FreeStyleProject np = folder.createProject(FreeStyleProject.class, "nested_project");

        view.add(np);
        assertTrue(view.contains(np));
        view.remove(np);
        assertFalse(view.contains(np));

        Page page = doPost(view, "addJobToView?name=folder/nested_project");
        j.assertGoodStatus(page);
        assertTrue(view.contains(np));

        page = doPost(view, "removeJobFromView?name=folder/nested_project");
        j.assertGoodStatus(page);
        assertFalse(view.contains(np));

        MockFolder nf = folder.createProject(MockFolder.class, "nested_folder");
        FreeStyleProject nnp = nf.createProject(FreeStyleProject.class, "nested_nested_project");
        ListView nestedView = new ListView("nested_view", folder);
        nestedView.setRecurse(true);
        folder.addView(nestedView);

        page = doPost(nestedView, "addJobToView?name=nested_folder/nested_nested_project");
        j.assertGoodStatus(page);
        assertTrue(nestedView.contains(nnp));

        page = doPost(nestedView, "removeJobFromView?name=nested_folder/nested_nested_project");
        j.assertGoodStatus(page);
        assertFalse(nestedView.contains(nnp));

        page = doPost(nestedView, "addJobToView?name=/folder/nested_folder/nested_nested_project");
        j.assertGoodStatus(page);
        assertTrue(nestedView.contains(nnp));

        page = doPost(nestedView, "removeJobFromView?name=/folder/nested_folder/nested_nested_project");
        j.assertGoodStatus(page);
        assertFalse(nestedView.contains(nnp));
    }

    @Test
    void doRemoveJobFromView() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject("a_project");
        ListView view = new ListView("a_view", j.jenkins);
        j.jenkins.addView(view);

        Page page = doPost(view, "addJobToView?name=a_project");
        assertTrue(view.contains(project));

        page = doPost(view, "removeJobFromView?name=a_project");
        j.assertGoodStatus(page);
        assertFalse(view.contains(project));

        page = doPost(view, "removeJobFromView?name=a_project");
        j.assertGoodStatus(page);
        assertFalse(view.contains(project));
    }

    @Test
    void failWebMethodForIllegalRequest() throws Exception {
        ListView view = new ListView("a_view", j.jenkins);
        j.jenkins.addView(view);

        assertBadStatus(
                doPost(view, "addJobToView"),
                "Query parameter 'name' is required"
        );
        assertBadStatus(
                doPost(view, "addJobToView?name=no_project"),
                "Query parameter 'name' does not correspond to a known item"
        );
        assertBadStatus(
                doPost(view, "removeJobFromView"),
                "Query parameter 'name' is required"
        );

        MockFolder folder = j.createFolder("folder");
        ListView folderView = new ListView("folder_view", folder);
        folder.addView(folderView);

        // Item is scoped to different ItemGroup
        assertBadStatus(
                doPost(folderView, "addJobToView?name=top_project"),
                "Query parameter 'name' does not correspond to a known item"
        );
    }

    private Page doPost(View view, String path) throws Exception {
        WebClient wc = j.createWebClient()
                .withThrowExceptionOnFailingStatusCode(false);
        WebRequest req = new WebRequest(
                new URI(j.jenkins.getRootUrl() + view.getUrl() + path).toURL(),
                HttpMethod.POST
        );

        return wc.getPage(wc.addCrumb(req));
    }

    private void assertBadStatus(Page page, String message) {
        WebResponse rsp = page.getWebResponse();
        assertFalse(j.isGoodHttpStatus(rsp.getStatusCode()), "Status: " + rsp.getStatusCode());
        assertThat(rsp.getContentAsString(), Matchers.containsString(message));
    }
}
