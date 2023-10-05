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

import static org.junit.Assert.fail;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import jenkins.model.Jenkins;
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

    @Test
    @LocalData
    @Issue("JENKINS-45841")
    public void testShouldNotOverrideUserConfiguration() throws Exception {
        assertEnabled("JNLP4-connect");
        assertDisabled("JNLP2-connect", "JNLP3-connect");
        assertProtocols(true, "System protocols should be always enabled", "Ping");
    }

    private void assertEnabled(String ... protocolNames) {
        assertProtocols(true, null, protocolNames);
    }

    private void assertDisabled(String ... protocolNames) {
        assertProtocols(false, null, protocolNames);
    }

    private void assertProtocols(boolean shouldBeEnabled, @CheckForNull String why, String ... protocolNames) {
        assertProtocols(j.jenkins, shouldBeEnabled, why, protocolNames);
    }

    public static void assertProtocols(Jenkins jenkins, boolean shouldBeEnabled, @CheckForNull String why, String ... protocolNames) {
        Set<String> agentProtocols = jenkins.getAgentProtocols();
        List<String> failedChecks = new ArrayList<>();
        for (String protocol : protocolNames) {
            if (shouldBeEnabled && !agentProtocols.contains(protocol)) {
                failedChecks.add(protocol);
            }
            if (!shouldBeEnabled && agentProtocols.contains(protocol)) {
                failedChecks.add(protocol);
            }
        }

        if (!failedChecks.isEmpty()) {
            String message = String.format("Protocol(s) are not %s: %s. %sEnabled protocols: %s",
                    shouldBeEnabled ? "enabled" : "disabled",
                    String.join(",", failedChecks),
                    why != null ? "Reason: " + why + ". " : "",
                    String.join(",", agentProtocols));
            fail(message);
        }
    }

}
