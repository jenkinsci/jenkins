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

import static org.junit.Assert.*;
import hudson.Functions;

import java.io.IOException;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.jvnet.hudson.test.MockFolder;
import org.jvnet.hudson.test.recipes.LocalData;
import org.xml.sax.SAXException;

import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlPage;

public class ListViewTest {

    @Rule public JenkinsRule j = new JenkinsRule();

    @Bug(15309)
    @LocalData
    @Test public void nullJobNames() throws Exception {
        assertTrue(j.jenkins.getView("v").getItems().isEmpty());
    }
    
    @Test
    public void testJobLinksAreValid() throws Exception {
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

}
