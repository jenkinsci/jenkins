/*
 * The MIT License
 *
 * Copyright (c) 2015 Red Hat, Inc.; Christopher Simons
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import com.gargoylesoftware.htmlunit.Page;
import hudson.slaves.DumbSlave;
import hudson.slaves.OfflineCause;
import java.util.concurrent.Future;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.jvnet.hudson.test.SmokeTest;

@Category(SmokeTest.class)
public class ComputerSEC2120Test {

    @Rule public JenkinsRule j = new JenkinsRule();

    @Test
    public void testTerminatedNodeStatusPageDoesNotShowTrace() throws Exception {
        DumbSlave agent = j.createOnlineSlave();
        FreeStyleProject p = j.createFreeStyleProject();
        p.setAssignedNode(agent);

        Future<FreeStyleBuild> r = ExecutorTest.startBlockingBuild(p);

        String message = "It went away";
        p.getLastBuild().getBuiltOn().toComputer().disconnect(
                new OfflineCause.ChannelTermination(new RuntimeException(message))
        );

        WebClient wc = j.createWebClient();
        Page page = wc.getPage(wc.createCrumbedUrl(agent.toComputer().getUrl()));
        String content = page.getWebResponse().getContentAsString();
        assertThat(content, not(containsString(message)));
    }

    @Test
    public void testTerminatedNodeAjaxExecutorsDoesNotShowTrace() throws Exception {
        DumbSlave agent = j.createOnlineSlave();
        FreeStyleProject p = j.createFreeStyleProject();
        p.setAssignedNode(agent);

        Future<FreeStyleBuild> r = ExecutorTest.startBlockingBuild(p);

        String message = "It went away";
        p.getLastBuild().getBuiltOn().toComputer().disconnect(
                new OfflineCause.ChannelTermination(new RuntimeException(message))
        );

        WebClient wc = j.createWebClient();
        Page page = wc.getPage(wc.createCrumbedUrl("ajaxExecutors"));
        String content = page.getWebResponse().getContentAsString();
        assertThat(content, not(containsString(message)));
    }

}
