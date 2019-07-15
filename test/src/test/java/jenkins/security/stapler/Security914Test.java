/*
 * The MIT License
 *
 * Copyright (c) 2018, CloudBees, Inc.
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
package jenkins.security.stapler;

import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebRequest;
import hudson.Functions;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestPluginManager;

import java.net.HttpURLConnection;
import java.net.URL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

@Issue("SECURITY-914")
public class Security914Test {
    
    @Rule
    public JenkinsRule j = new JenkinsRule();
    
    @Test
    public void cannotUseInvalidLocale_toTraverseFolder() throws Exception {
        Assume.assumeTrue(Functions.isWindows());
        
        if (j.jenkins.pluginManager.getPlugin("credentials") == null) {
            ((TestPluginManager) j.jenkins.pluginManager).installDetachedPlugin("credentials");
        }
        j.createWebClient().goTo("plugin/credentials/images/24x24/credentials.png", "image/png");
        
        JenkinsRule.WebClient wc = j.createWebClient()
                .withThrowExceptionOnFailingStatusCode(false);
        WebRequest request = new WebRequest(new URL(j.getURL() + "plugin/credentials/.xml"));
        // plugin deployed in: test\target\jenkins7375296945862059919tmp
        // rootDir is in     : test\target\jenkinsTests.tmp\jenkins1274934531848159942test
        // j.jenkins.getRootDir().getName() = jenkins1274934531848159942test
        request.setAdditionalHeader("Accept-Language", "../../../../jenkinsTests.tmp/" + j.jenkins.getRootDir().getName() + "/config");
        
        Page p = wc.getPage(request);
        assertEquals(HttpURLConnection.HTTP_NOT_FOUND, p.getWebResponse().getStatusCode());
        assertNotEquals(p.getWebResponse().getContentType(), "application/xml");
    }
    
    @Test
    public void cannotUseInvalidLocale_toAnyFileInSystem() throws Exception {
        Assume.assumeTrue(Functions.isWindows());
        
        if (j.jenkins.pluginManager.getPlugin("credentials") == null) {
            ((TestPluginManager) j.jenkins.pluginManager).installDetachedPlugin("credentials");
        }
        j.createWebClient().goTo("plugin/credentials/images/24x24/credentials.png", "image/png");
        
        JenkinsRule.WebClient wc = j.createWebClient()
                .withThrowExceptionOnFailingStatusCode(false);
        WebRequest request = new WebRequest(new URL(j.getURL() + "plugin/credentials/.ini"));
        // ../ can be multiply to infinity, no impact, we just need to have enough to reach the root
        request.setAdditionalHeader("Accept-Language", "../../../../../../../../../../../../windows/win");
        
        Page p = wc.getPage(request);
        assertEquals(HttpURLConnection.HTTP_NOT_FOUND, p.getWebResponse().getStatusCode());
        assertEquals(p.getWebResponse().getContentType(), "text/html");
    }
}
