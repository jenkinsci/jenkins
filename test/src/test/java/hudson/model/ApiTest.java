/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Yahoo!, Inc.
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

import com.gargoylesoftware.htmlunit.Page;

import java.io.File;
import java.net.HttpURLConnection;
import org.junit.Ignore;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * @author Kohsuke Kawaguchi
 */
public class ApiTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    @Issue("JENKINS-2828")
    public void xpath() throws Exception {
        j.createWebClient().goTo("api/xml?xpath=/*[1]", "application/xml");
    }

    @Issue("JENKINS-27607")
    @Test public void json() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject("p");
        JenkinsRule.WebClient wc = j.createWebClient();
        assertEquals("{\"name\":\"p\"}", wc.goTo(p.getUrl() + "api/json?tree=name", "application/json").getWebResponse().getContentAsString());
        assertEquals("wrap({\"name\":\"p\"})", wc.goTo(p.getUrl() + "api/json?tree=name&jsonp=wrap", "application/javascript").getWebResponse().getContentAsString());
    }

    @Test
    @Issue("JENKINS-3267")
    public void wrappedZeroItems() throws Exception {
        Page page = j.createWebClient().goTo("api/xml?wrapper=root&xpath=/hudson/nonexistent", "application/xml");
        assertEquals("<root/>", page.getWebResponse().getContentAsString());
    }

    /**
     * Test that calling the XML API with the XPath <code>document</code> function fails.
     *
     * @throws Exception if so
     */
    @Issue("SECURITY-165")
    @Test public void xPathDocumentFunction() throws Exception {
        File f = new File(j.jenkins.getRootDir(), "queue.xml");
        JenkinsRule.WebClient client = j.createWebClient();

        try {
            client.goTo("api/xml?xpath=document(\"" + f.getAbsolutePath() + "\")", "application/xml");
            fail("Should become 500 error");
        } catch (com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException e) {
            String contentAsString = e.getResponse().getContentAsString();
            j.assertStringContains(
                    contentAsString,
                    "Illegal function: document");
        }
    }

    @Test
    @Issue("JENKINS-3267")
    public void wrappedOneItem() throws Exception {
        Page page = j.createWebClient().goTo("api/xml?wrapper=root&xpath=/hudson/view/name", "application/xml");
        assertEquals("<root><name>All</name></root>", page.getWebResponse().getContentAsString());
    }

    @Ignore("JENKINS-26775: fails in JDK 8 builds prior to stapler 1.238")
    @Test
    public void wrappedMultipleItems() throws Exception {
        j.createFreeStyleProject();
        j.createFreeStyleProject();
        Page page = j.createWebClient().goTo("api/xml?wrapper=root&xpath=/hudson/job/name", "application/xml");
        assertEquals("<root><name>test0</name><name>test1</name></root>", page.getWebResponse().getContentAsString());
    }

    @Test
    public void unwrappedZeroItems() throws Exception {
        j.createWebClient().assertFails("api/xml?xpath=/hudson/nonexistent", HttpURLConnection.HTTP_NOT_FOUND);
    }

    @Test
    public void unwrappedOneItem() throws Exception {
        Page page = j.createWebClient().goTo("api/xml?xpath=/hudson/view/name", "application/xml");
        assertEquals("<name>All</name>", page.getWebResponse().getContentAsString());
    }

    @Test
    public void unwrappedLongString() throws Exception {
        j.jenkins.setSystemMessage("Lorem ipsum dolor sit amet, consectetur adipisicing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.");
        Page page = j.createWebClient().goTo("api/xml?xpath=/hudson/description", "application/xml");
        assertEquals(
                "<description>"+j.jenkins.getSystemMessage()+"</description>",
                page.getWebResponse().getContentAsString());
    }

    @Test
    public void unwrappedMultipleItems() throws Exception {
        j.createFreeStyleProject();
        j.createFreeStyleProject();
        j.createWebClient().assertFails("api/xml?xpath=/hudson/job/name", HttpURLConnection.HTTP_INTERNAL_ERROR);
    }
}
