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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.AdministrativeMonitor;
import hudson.model.ProjectTest;
import hudson.model.Slave;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class ControllerExecutorsAgentsTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void testInitial() {
        ControllerExecutorsAgents monitor = j.jenkins.getExtensionList(AdministrativeMonitor.class).get(ControllerExecutorsAgents.class);
        assertFalse(monitor.isActivated());
    }

    @Test
    void testControllerExecutorsZero() throws Exception {
        Slave agent = j.createSlave();
        j.jenkins.setNumExecutors(0);
        ControllerExecutorsAgents monitor = j.jenkins.getExtensionList(AdministrativeMonitor.class).get(ControllerExecutorsAgents.class);
        assertFalse(monitor.isActivated());
    }

    @Test
    void testHasAgent() throws Exception {
        Slave agent = j.createSlave();
        ControllerExecutorsAgents monitor = j.jenkins.getExtensionList(AdministrativeMonitor.class).get(ControllerExecutorsAgents.class);
        assertTrue(monitor.isActivated());
    }

    @Test
    void testHasCloud() {
        ProjectTest.DummyCloudImpl2 c2 = new ProjectTest.DummyCloudImpl2(j, 0);
        j.jenkins.clouds.add(c2);
        ControllerExecutorsAgents monitor = j.jenkins.getExtensionList(AdministrativeMonitor.class).get(ControllerExecutorsAgents.class);
        assertTrue(monitor.isActivated());
    }

}
