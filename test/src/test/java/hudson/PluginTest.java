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

import hudson.model.UpdateCenter;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.concurrent.Future;
import jenkins.model.Jenkins;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

public class PluginTest {

    @Rule public JenkinsRule r = new JenkinsRule();

    @Issue({"SECURITY-131", "SECURITY-155", "SECURITY-705"})
    @Test public void doDynamic() throws Exception {
        r.createWebClient().goTo("plugin/cloudbees-folder/images/svgs/folder.svg", "image/svg+xml");
        r.createWebClient().goTo("plugin/cloudbees-folder/images/../images/svgs/folder.svg", "image/svg+xml"); // collapsed somewhere before it winds up in restOfPath
        /* TODO https://github.com/apache/httpcomponents-client/commit/8c04c6ae5e5ba1432e40684428338ce68431766b#r32873542
        r.createWebClient().assertFails("plugin/matrix-auth/images/%2E%2E/images/user-disabled.png", HttpServletResponse.SC_INTERNAL_SERVER_ERROR); // IAE from TokenList.<init>
        r.createWebClient().assertFails("plugin/matrix-auth/images/%252E%252E/images/user-disabled.png", HttpServletResponse.SC_BAD_REQUEST); // SECURITY-131
        r.createWebClient().assertFails("plugin/matrix-auth/images/%25252E%25252E/images/user-disabled.png", HttpServletResponse.SC_BAD_REQUEST); // just checking
        */
        // SECURITY-705:
        r.createWebClient().assertFails("plugin/matrix-auth/images/..%2fWEB-INF/licenses.xml", HttpServletResponse.SC_BAD_REQUEST);
        r.createWebClient().assertFails("plugin/matrix-auth/./matrix-auth.jpi", /* Path collapsed to simply `credentials.jpi` before entering */ HttpServletResponse.SC_NOT_FOUND);
        r.createWebClient().assertFails("plugin/matrix-auth/images/%2e%2e%2fWEB-INF/licenses.xml", HttpServletResponse.SC_BAD_REQUEST);
        r.createWebClient().assertFails("plugin/matrix-auth/images/%2e.%2fWEB-INF/licenses.xml", HttpServletResponse.SC_BAD_REQUEST);
        r.createWebClient().assertFails("plugin/matrix-auth/images/..%2f..%2f..%2f" + r.jenkins.getRootDir().getName() + "%2fsecrets%2fmaster.key", HttpServletResponse.SC_BAD_REQUEST);
        r.createWebClient().assertFails("plugin/matrix-auth/" + r.jenkins.getRootDir() + "/secrets/master.key", /* ./ prepended anyway */ Functions.isWindows() ? HttpServletResponse.SC_BAD_REQUEST : HttpServletResponse.SC_NOT_FOUND);
        // SECURITY-155:
        r.createWebClient().assertFails("plugin/matrix-auth/WEB-INF/licenses.xml", HttpServletResponse.SC_BAD_REQUEST);
        r.createWebClient().assertFails("plugin/matrix-auth/META-INF/MANIFEST.MF", HttpServletResponse.SC_BAD_REQUEST);
        r.createWebClient().assertFails("plugin/matrix-auth/web-inf/licenses.xml", HttpServletResponse.SC_BAD_REQUEST);
        r.createWebClient().assertFails("plugin/matrix-auth/meta-inf/manifest.mf", HttpServletResponse.SC_BAD_REQUEST);
    }

    @Ignore("TODO observed to fail in CI with 404 due to external UC issues")
    @Test
    @Issue("SECURITY-925")
    public void preventTimestamp2_toBeServed() throws Exception {
        // impossible to use installDetachedPlugin("credentials") since we want to have it exploded like with WAR
        Jenkins.get().getUpdateCenter().getSites().get(0).updateDirectlyNow(false);
        List<Future<UpdateCenter.UpdateCenterJob>> pluginInstalled = r.jenkins.pluginManager.install(List.of("credentials"), true);

        for (Future<UpdateCenter.UpdateCenterJob> job : pluginInstalled) {
            job.get();
        }
        r.createWebClient().assertFails("plugin/matrix-auth/.timestamp2", HttpServletResponse.SC_BAD_REQUEST);
    }

}
