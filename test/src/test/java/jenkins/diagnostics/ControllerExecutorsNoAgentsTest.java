/*
 * The MIT License
 *
 * Copyright (c) 2021, CloudBees, Inc.
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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import hudson.model.AdministrativeMonitor;
import hudson.model.ProjectTest;
import hudson.model.Agent;
import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class ControllerExecutorsNoAgentsTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void testInitial() {
        ControllerExecutorsNoAgents monitor = j.jenkins.getExtensionList(AdministrativeMonitor.class).get(ControllerExecutorsNoAgents.class);
        assertTrue(monitor.isActivated());
    }

    @Test
    public void testControllerExecutorsZero() throws IOException {
        j.jenkins.setNumExecutors(0);
        ControllerExecutorsNoAgents monitor = j.jenkins.getExtensionList(AdministrativeMonitor.class).get(ControllerExecutorsNoAgents.class);
        assertFalse(monitor.isActivated());
    }

    @Test
    public void testHasAgent() throws Exception {
        Agent agent = j.createAgent();
        ControllerExecutorsNoAgents monitor = j.jenkins.getExtensionList(AdministrativeMonitor.class).get(ControllerExecutorsNoAgents.class);
        assertFalse(monitor.isActivated());
    }

    @Test
    public void testHasCloud() throws Exception {
        ProjectTest.DummyCloudImpl2 c2 = new ProjectTest.DummyCloudImpl2(j, 0);
        j.jenkins.clouds.add(c2);
        ControllerExecutorsNoAgents monitor = j.jenkins.getExtensionList(AdministrativeMonitor.class).get(ControllerExecutorsNoAgents.class);
        assertFalse(monitor.isActivated());
    }

}
