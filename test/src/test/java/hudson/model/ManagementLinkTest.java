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
package hudson.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.gargoylesoftware.htmlunit.html.DomNodeUtil;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.jvnet.hudson.test.TestExtension;

import java.util.List;

/**
 * @author Kohsuke Kawaguchi
 */
public class ManagementLinkTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    /**
     * Makes sure every link works.
     */
    @Test
    public void links() throws Exception {
        WebClient wc = j.createWebClient();

        for (int i=0; ; i++) {
            HtmlPage page = wc.goTo("manage");
            List<?> anchors = DomNodeUtil.selectNodes(page, "id('management-links')//*[@class='link']/a[not(@onclick)]");
            assertTrue(anchors.size()>=8);
            if (i==anchors.size())  return; // done

            ((HtmlAnchor)anchors.get(i)).click();
        }
    }

    @Test @Issue("JENKINS-33683")
    public void invisibleLinks() throws Exception {
        assertEquals(null, j.jenkins.getDynamic("and_fail_trying"));
    }

    @TestExtension("invisibleLinks")
    public static final class InvisibleManagementLink extends ManagementLink {

        @Override
        public String getIconFileName() {
            return null;
        }

        @Override
        public String getDisplayName() {
            return null;
        }

        @Override
        public String getUrlName() {
            return null;
        }
    }
}
