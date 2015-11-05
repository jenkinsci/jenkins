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

import static org.junit.Assert.*;

import java.io.IOException;
import java.net.URL;

import javax.annotation.Nonnull;

import org.hamcrest.Matchers;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.jvnet.hudson.test.MockFolder;

import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebRequestSettings;
import com.gargoylesoftware.htmlunit.WebResponse;

public class DirectlyModifiableViewTest {

    @Rule public JenkinsRule j = new JenkinsRule();

    @Test
    public void manipulateViewContent() throws IOException {
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
    public void doAddJobToView() throws Exception {
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
    public void doAddNestedJobToRecursiveView() throws Exception {
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
    public void doRemoveJobFromView() throws Exception {
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
    public void failWebMethodForIllegalRequest() throws Exception {
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

        assertBadStatus( // Item is scoped to different ItemGroup
                doPost(folderView, "addJobToView?name=top_project"),
                "Query parameter 'name' does not correspond to a known item"
        );
    }

    private Page doPost(View view, String path) throws Exception {
        WebClient wc = j.createWebClient();
        wc.setThrowExceptionOnFailingStatusCode(false);
        WebRequestSettings req = new WebRequestSettings(
                new URL(j.jenkins.getRootUrl() + view.getUrl() + path),
                HttpMethod.POST
        );

        return wc.getPage(wc.addCrumb(req));
    }

    private void assertBadStatus(Page page, String message) {
        WebResponse rsp = page.getWebResponse();
        assertFalse("Status: " + rsp.getStatusCode(), j.isGoodHttpStatus(rsp.getStatusCode()));
        assertThat(rsp.getContentAsString(), Matchers.containsString(message));
    }
}
