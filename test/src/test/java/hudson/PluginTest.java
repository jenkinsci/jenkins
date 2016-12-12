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

package hudson;

import javax.servlet.http.HttpServletResponse;
import org.junit.Test;
import org.junit.Rule;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

public class PluginTest {

    @Rule public JenkinsRule r = new JenkinsRule();

    @Issue({"SECURITY-131", "SECURITY-155"})
    @Test public void doDynamic() throws Exception {
        r.createWebClient().goTo("plugin/credentials/images/24x24/credentials.png", "image/png");
        /* Collapsed somewhere before it winds up in restOfPath:
        r.createWebClient().assertFails("plugin/credentials/images/../images/24x24/credentials.png", HttpServletResponse.SC_BAD_REQUEST);
        */
        r.createWebClient().assertFails("plugin/credentials/images/%2E%2E/images/24x24/credentials.png", HttpServletResponse.SC_INTERNAL_SERVER_ERROR); // IAE from TokenList.<init>
        r.createWebClient().assertFails("plugin/credentials/images/%252E%252E/images/24x24/credentials.png", HttpServletResponse.SC_NOT_FOUND); // SECURITY-131
        r.createWebClient().assertFails("plugin/credentials/images/%25252E%25252E/images/24x24/credentials.png", HttpServletResponse.SC_NOT_FOUND); // just checking
        // SECURITY-155:
        r.createWebClient().assertFails("plugin/credentials/WEB-INF/licenses.xml", HttpServletResponse.SC_NOT_FOUND);
        r.createWebClient().assertFails("plugin/credentials/META-INF/MANIFEST.MF", HttpServletResponse.SC_NOT_FOUND);
    }

}
