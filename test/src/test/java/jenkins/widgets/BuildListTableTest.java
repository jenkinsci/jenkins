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

import static org.junit.Assert.assertEquals;

import hudson.model.AbstractItem;
import hudson.model.FreeStyleProject;
import hudson.model.ListView;
import java.net.URI;
import java.net.URL;
import java.util.logging.Level;
import org.htmlunit.html.HtmlAnchor;
import org.htmlunit.html.HtmlPage;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.MockFolder;

public class BuildListTableTest {

    @Rule public JenkinsRule r = new JenkinsRule();
    @Rule public LoggerRule logging = new LoggerRule().record(AbstractItem.class, Level.FINER);

    @Issue("JENKINS-19310")
    @Test public void linksFromFolders() throws Exception {
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
        assertEquals(href, r.getURL() + "view/v1/job/d/view/v2/job/d2/job/p/", target.toString());
        page = wc.goTo("job/d/view/All/builds");
        assertEquals(0, wc.waitForBackgroundJavaScript(120000));
        anchor = page.getAnchorByText("d » d2 » p");
        href = anchor.getHrefAttribute();
        target = URI.create(page.getUrl().toExternalForm()).resolve(href).toURL();
        wc.getPage(target);
        assertEquals(href, r.getURL() + "job/d/job/d2/job/p/", target.toString());
    }

}
