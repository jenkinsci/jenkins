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

package jenkins.diagnostics;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.AdministrativeMonitor;
import jenkins.model.JenkinsLocationConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class RootUrlNotSetMonitorTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    @Issue("JENKINS-31661")
    void testWithRootUrl_configured() {
        // test relies on the default JTH behavior
        JenkinsLocationConfiguration config = JenkinsLocationConfiguration.get();
        String url = config.getUrl();
        assertNotNull(url);
        assertFalse(url.isBlank());

        RootUrlNotSetMonitor monitor = j.jenkins.getExtensionList(AdministrativeMonitor.class).get(RootUrlNotSetMonitor.class);
        assertFalse(monitor.isActivated(), "Monitor must not be activated");

        config.setUrl(null);

        assertTrue(monitor.isActivated(), "Monitor must be activated");

        config.setUrl("ftp://localhost:8080/jenkins");

        assertTrue(monitor.isActivated(), "Monitor must be activated");

        config.setUrl("http://localhost:8080/jenkins");

        assertFalse(monitor.isActivated(), "Monitor must be activated");
    }
}
