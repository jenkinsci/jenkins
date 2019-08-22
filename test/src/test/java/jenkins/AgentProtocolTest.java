/*
 * The MIT License
 *
 * Copyright (c) 2017 CloudBees, Inc.
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
package jenkins;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.annotation.CheckForNull;
import jenkins.install.SetupWizardTest;
import jenkins.model.Jenkins;
import jenkins.slaves.DeprecatedAgentProtocolMonitor;
import org.apache.commons.lang.StringUtils;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;

/**
 * Tests for {@link AgentProtocol}.
 * 
 * @author Oleg Nenashev
 */
public class AgentProtocolTest {
    
    @Rule
    public JenkinsRule j = new JenkinsRule();

    //TODO: Test is unstable on CI due to the race condition, needs to be reworked
    /**
     * Checks that Jenkins does not disable agent protocols by default after the upgrade.
     * 
     * @throws Exception Test failure
     * @see SetupWizardTest#shouldDisableUnencryptedProtocolsByDefault() 
     */
    @Test
    @Ignore
    @LocalData
    @Issue("JENKINS-45841")
    public void testShouldNotDisableProtocolsForMigratedInstances() throws Exception {
        assertProtocols(true, "Legacy Non-encrypted JNLP protocols should be enabled", 
                "JNLP-connect", "JNLP2-connect", "JNLP4-connect");
        assertProtocols(true, "Default encrypted protocols should be enabled", "JNLP4-connect");
        assertProtocols(false, "JNLP3-connect protocol should be disabled by default", "JNLP3-connect");
        assertMonitorTriggered("JNLP-connect", "JNLP2-connect");
    }
    
    @Test
    @LocalData
    @Issue("JENKINS-45841")
    public void testShouldNotOverrideUserConfiguration() throws Exception {
        assertEnabled("JNLP-connect", "JNLP3-connect");
        assertDisabled("JNLP2-connect", "JNLP4-connect");
        assertProtocols(true, "System protocols should be always enabled", "Ping");
        assertMonitorTriggered("JNLP-connect", "JNLP3-connect");
    }
    
    private void assertEnabled(String ... protocolNames) throws AssertionError {
        assertProtocols(true, null, protocolNames);    
    }
    
    private void assertDisabled(String ... protocolNames) throws AssertionError {
        assertProtocols(false, null, protocolNames);    
    }
    
    private void assertProtocols(boolean shouldBeEnabled, @CheckForNull String why, String ... protocolNames) {
        assertProtocols(j.jenkins, shouldBeEnabled, why, protocolNames);
    }
    
    public static void assertProtocols(Jenkins jenkins, boolean shouldBeEnabled, @CheckForNull String why, String ... protocolNames) 
            throws AssertionError {
        Set<String> agentProtocols = jenkins.getAgentProtocols();
        List<String> failedChecks = new ArrayList<>();
        for (String protocol : protocolNames) {
            if (shouldBeEnabled && !(agentProtocols.contains(protocol))) {
                failedChecks.add(protocol);
            }
            if (!shouldBeEnabled && agentProtocols.contains(protocol)) {
                failedChecks.add(protocol);
            }
        }
        
        if (!failedChecks.isEmpty()) {
            String message = String.format("Protocol(s) are not %s: %s. %sEnabled protocols: %s",
                    shouldBeEnabled ? "enabled" : "disabled",
                    StringUtils.join(failedChecks, ','),
                    why != null ? "Reason: " + why + ". " : "",
                    StringUtils.join(agentProtocols, ','));
            fail(message);
        }
    }
    
    public static void assertMonitorNotActive(JenkinsRule j) {
        DeprecatedAgentProtocolMonitor monitor = new DeprecatedAgentProtocolMonitor();
        assertFalse("Deprecated Agent Protocol Monitor should not be activated. Current protocols: "
                + StringUtils.join(j.jenkins.getAgentProtocols(), ","), monitor.isActivated());
    }
    
    public static void assertMonitorTriggered(String ... expectedProtocols) {
        DeprecatedAgentProtocolMonitor monitor = new DeprecatedAgentProtocolMonitor();
        assertTrue("Deprecated Agent Protocol Monitor should be activated", monitor.isActivated());
        String protocolList = monitor.getDeprecatedProtocols();
        assertThat("List of the protocols should not be null", protocolList, not(nullValue()));
        
        List<String> failedChecks = new ArrayList<>();
        for(String protocol : expectedProtocols) {
            if (!protocolList.contains(protocol)) {
                failedChecks.add(protocol);
            }
        }
        
        if (!failedChecks.isEmpty()) {
            String message = String.format(
                    "Protocol(s) should in the deprecated protocol list: %s. Current list: %s",
                    StringUtils.join(expectedProtocols, ','), protocolList);
            fail(message);
        }
    }
}
