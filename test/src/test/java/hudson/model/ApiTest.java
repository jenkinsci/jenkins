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

import com.gargoylesoftware.htmlunit.Page;
import java.net.HttpURLConnection;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.Bug;

/**
 * @author Kohsuke Kawaguchi
 */
public class ApiTest extends HudsonTestCase {

    @Bug(2828)
    public void testXPath() throws Exception {
        new WebClient().goTo("api/xml?xpath=/*[1]","application/xml");
    }

    @Bug(3267)
    public void testWrappedZeroItems() throws Exception {
        Page page = new WebClient().goTo("api/xml?wrapper=root&xpath=/hudson/nonexistent", "application/xml");
        assertEquals("<root/>", page.getWebResponse().getContentAsString());
    }

    @Bug(3267)
    public void testWrappedOneItem() throws Exception {
        Page page = new WebClient().goTo("api/xml?wrapper=root&xpath=/hudson/view/name", "application/xml");
        assertEquals("<root><name>All</name></root>", page.getWebResponse().getContentAsString());
    }

    public void testWrappedMultipleItems() throws Exception {
        createFreeStyleProject();
        createFreeStyleProject();
        Page page = new WebClient().goTo("api/xml?wrapper=root&xpath=/hudson/job/name", "application/xml");
        assertEquals("<root><name>test0</name><name>test1</name></root>", page.getWebResponse().getContentAsString());
    }

    public void testUnwrappedZeroItems() throws Exception {
        new WebClient().assertFails("api/xml?xpath=/hudson/nonexistent", HttpURLConnection.HTTP_NOT_FOUND);
    }

    public void testUnwrappedOneItem() throws Exception {
        Page page = new WebClient().goTo("api/xml?xpath=/hudson/view/name", "application/xml");
        assertEquals("<name>All</name>", page.getWebResponse().getContentAsString());
    }

    public void testUnwrappedLongString() throws Exception {
        jenkins.setSystemMessage("Lorem ipsum dolor sit amet, consectetur adipisicing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.");
        Page page = new WebClient().goTo("api/xml?xpath=/hudson/description", "application/xml");
        assertEquals(
                "<description>"+jenkins.getSystemMessage()+"</description>",
                page.getWebResponse().getContentAsString());
    }

    public void testUnwrappedMultipleItems() throws Exception {
        createFreeStyleProject();
        createFreeStyleProject();
        new WebClient().assertFails("api/xml?xpath=/hudson/job/name", HttpURLConnection.HTTP_INTERNAL_ERROR);
    }
}
