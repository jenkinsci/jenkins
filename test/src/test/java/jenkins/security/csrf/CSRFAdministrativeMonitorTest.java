/*
 * The MIT License
 *
 * Copyright (c) 2017, CloudBees, Inc.
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
package jenkins.security.csrf;

import hudson.model.AdministrativeMonitor;
import hudson.security.csrf.DefaultCrumbIssuer;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CSRFAdministrativeMonitorTest {
    
    @Rule
    public JenkinsRule j = new JenkinsRule();
    
    @Test
    @Issue("JENKINS-47372")
    public void testWithoutIssuer() {
        j.jenkins.setCrumbIssuer(null);
        
        CSRFAdministrativeMonitor monitor = j.jenkins.getExtensionList(AdministrativeMonitor.class).get(CSRFAdministrativeMonitor.class);
        assertTrue("Monitor must not be activated", monitor.isActivated());
    }

    @Test
    @Issue("JENKINS-47372")
    public void testWithIssuer() {
        j.jenkins.setCrumbIssuer(new DefaultCrumbIssuer(false));
        
        CSRFAdministrativeMonitor monitor = j.jenkins.getExtensionList(AdministrativeMonitor.class).get(CSRFAdministrativeMonitor.class);
        assertFalse("Monitor must be activated", monitor.isActivated());
    }
}
