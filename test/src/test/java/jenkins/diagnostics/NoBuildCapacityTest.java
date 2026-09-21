/*
 * The MIT License
 *
 * Copyright (c) 2026, CloudBees, Inc.
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

package jenkins.diagnostics;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.AdministrativeMonitor;
import hudson.model.ProjectTest;
import java.net.URL;
import org.htmlunit.HttpMethod;
import org.htmlunit.Page;
import org.htmlunit.WebRequest;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlFormUtil;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class NoBuildCapacityTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    private NoBuildCapacity monitor() {
        return j.jenkins.getExtensionList(AdministrativeMonitor.class).get(NoBuildCapacity.class);
    }

    @Test
    void notActivatedWithBuiltInNodeExecutors() {
        // JenkinsRule starts with executors on the built-in node, so builds can run.
        assertTrue(j.jenkins.getNumExecutors() > 0);
        assertFalse(monitor().isActivated());
    }

    @Test
    void activatedWithoutAnyBuildCapacity() throws Exception {
        j.jenkins.setNumExecutors(0);
        assertTrue(monitor().isActivated());
    }

    @Test
    void notActivatedWithAgent() throws Exception {
        j.jenkins.setNumExecutors(0);
        j.createSlave();
        assertFalse(monitor().isActivated());
    }

    @Test
    void notActivatedWithCloud() throws Exception {
        j.jenkins.setNumExecutors(0);
        j.jenkins.clouds.add(new ProjectTest.DummyCloudImpl2(j, 0));
        assertFalse(monitor().isActivated());
    }

    @Test
    void monitorButtonEnablesBuiltInNodeAndReturnsToOriginatingPage() throws Exception {
        j.jenkins.setNumExecutors(0);
        assertTrue(monitor().isActivated());

        // Submitting the monitor's button from /manage must return there, not to the node configuration page.
        JenkinsRule.WebClient wc = j.createWebClient();
        HtmlPage manage = wc.goTo("manage");
        HtmlForm form = manage.getFormByName(monitor().id);
        Page result = HtmlFormUtil.submit(form);

        assertEquals(1, j.jenkins.getNumExecutors());
        assertFalse(monitor().isActivated());
        assertThat(result.getUrl().toString(), not(containsString("configure")));
        assertThat(result.getUrl().toString(), containsString("/manage"));
    }

    @Test
    void addExecutorEndpointEnablesBuiltInNode() throws Exception {
        j.jenkins.setNumExecutors(0);
        assertTrue(monitor().isActivated());

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.getPage(wc.addCrumb(new WebRequest(
                new URL(j.getURL(), "computer/(built-in)/addExecutor"), HttpMethod.POST)));

        assertEquals(1, j.jenkins.getNumExecutors());
        assertFalse(monitor().isActivated());
    }
}
