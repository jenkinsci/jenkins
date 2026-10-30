/*
 * The MIT License
 *
 * Copyright 2014 Jesse Glick.
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

package jenkins.widgets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.AbstractItem;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ListView;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.logging.Level;
import net.sf.json.JSONNull;
import net.sf.json.JSONObject;
import org.htmlunit.html.HtmlAnchor;
import org.htmlunit.html.HtmlButton;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.html.HtmlTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LogRecorder;
import org.jvnet.hudson.test.MockFolder;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class BuildListTableTest {

    private LogRecorder logging = new LogRecorder().record(AbstractItem.class, Level.FINER);

    private JenkinsRule r;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        r = rule;
    }

    @Issue("JENKINS-19310")
    @Test
    void linksFromFolders() throws Exception {
        MockFolder d = r.createFolder("d");
        ListView v1 = new ListView("v1", r.jenkins);
        v1.add(d);
        r.jenkins.addView(v1);
        MockFolder d2 = d.createProject(MockFolder.class, "d2");
        FreeStyleProject p = d2.createProject(FreeStyleProject.class, "p");
        r.buildAndAssertSuccess(p);
        ListView v2 = new ListView("v2", d);
        v2.setRecurse(true);
        v2.add(p);
        d.addView(v2);
        JenkinsRule.WebClient wc = r.createWebClient();
        HtmlPage page = wc.goTo("view/v1/job/d/view/v2/builds");
        assertEquals(0, wc.waitForBackgroundJavaScript(120000));
        HtmlAnchor anchor = page.getAnchorByText("d » d2 » p");
        String href = anchor.getHrefAttribute();
        URL target = URI.create(page.getUrl().toExternalForm()).resolve(href).toURL();
        wc.getPage(target);
        assertEquals(r.getURL() + "view/v1/job/d/view/v2/job/d2/job/p/", target.toString(), href);
        page = wc.goTo("job/d/view/All/builds");
        assertEquals(0, wc.waitForBackgroundJavaScript(120000));
        anchor = page.getAnchorByText("d » d2 » p");
        href = anchor.getHrefAttribute();
        target = URI.create(page.getUrl().toExternalForm()).resolve(href).toURL();
        wc.getPage(target);
        assertEquals(r.getURL() + "job/d/job/d2/job/p/", target.toString(), href);
    }

    @Test
    void showOlderBuildsLoadsTheNextPage() throws Exception {
        int pageSize = RunListData.PAGE_SIZE;
        RunListData.PAGE_SIZE = 2;
        try {
            FreeStyleProject p = r.createFreeStyleProject("p");
            for (int i = 0; i < 3; i++) {
                r.buildAndAssertSuccess(p);
            }
            JenkinsRule.WebClient wc = r.createWebClient();
            HtmlPage page = wc.goTo("view/all/builds");
            assertEquals(0, wc.waitForBackgroundJavaScript(120000));
            HtmlTable table = page.getHtmlElementById("projectStatus");
            // the header row plus one row per build
            assertEquals(2, table.getRowCount() - 1);
            HtmlButton more = page.querySelector(".jenkins-runs__more");
            assertFalse(more.getAttribute("class").contains("jenkins-hidden"));

            more.click();
            assertEquals(0, wc.waitForBackgroundJavaScript(120000));
            assertEquals(3, table.getRowCount() - 1);
            assertTrue(more.getAttribute("class").contains("jenkins-hidden"));
        } finally {
            RunListData.PAGE_SIZE = pageSize;
        }
    }

    @Test
    void buildListEndpoints() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject("p");
        FreeStyleBuild b = r.buildAndAssertSuccess(p);
        JenkinsRule.WebClient wc = r.createWebClient();
        for (String url : List.of("view/all/buildList", "computer/(built-in)/buildList", "job/p/buildTimeTrendData")) {
            JSONObject data = wc.getJSON(url).getJSONObject().getJSONObject("data");
            assertEquals(1, data.getJSONArray("builds").size(), url);
            assertEquals(JSONNull.getInstance(), data.get("next"), url);
            assertEquals(JSONNull.getInstance(), wc.getJSON(url + "?olderThan=" + b.getTimeInMillis()).getJSONObject().getJSONObject("data").get("next"), url);
            assertEquals(0, wc.getJSON(url + "?olderThan=" + b.getTimeInMillis()).getJSONObject().getJSONObject("data").getJSONArray("builds").size(), url);
        }
        wc.setThrowExceptionOnFailingStatusCode(false);
        assertEquals(400, wc.goTo("view/all/buildList?olderThan=nope", null).getWebResponse().getStatusCode());
    }

}
