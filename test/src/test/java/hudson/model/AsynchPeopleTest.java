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

import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.For;
import org.jvnet.hudson.test.JenkinsRule;

@For(View.AsynchPeople.class)
public class AsynchPeopleTest {

    @Rule public JenkinsRule j = new JenkinsRule();

    @Issue("JENKINS-18641")
    @Test public void display() throws Exception {
        User.get("bob");
        JenkinsRule.WebClient wc = j.createWebClient();
        HtmlPage page;
        try {
            page = wc.goTo("asynchPeople");
        } catch (FailingHttpStatusCodeException x) {
            System.err.println(x.getResponse().getResponseHeaders());
            System.err.println(x.getResponse().getContentAsString());
            throw x;
        }
        assertEquals(0, wc.waitForBackgroundJavaScript(120000));
        boolean found = false;
        for (HtmlElement table : page.getElementsByTagName("table")) {
            if (table.getAttribute("class").contains("progress-bar")) {
                found = true;
                assertEquals("display: none;", table.getAttribute("style"));
                break;
            }
        }
        assertTrue(found);
        /* TODO this still fails occasionally, for reasons TBD (I think because User.getAll sometimes is empty):
        assertNotNull(page.getElementById("person-bob"));
        */
    }

}
