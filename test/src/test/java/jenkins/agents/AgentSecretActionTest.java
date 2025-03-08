/*
 * The MIT License
 *
 * Copyright 2025 Kalinda Fabrice.
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

package jenkins.agents;

import static org.junit.Assert.assertEquals;

import hudson.model.Computer;
import hudson.slaves.DumbSlave;
import hudson.slaves.SlaveComputer;
import jenkins.model.Jenkins;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;


public class AgentSecretActionTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    private DumbSlave agent;
    private String agentUrl;

    @Before
    public void setUp() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        agent = new DumbSlave("test-agent", "/home/jenkins", new hudson.slaves.JNLPLauncher());
        Jenkins jenkins = Jenkins.get();
        jenkins.addNode(agent);
        agentUrl = "computer/test-agent/agent-secret/";
    }

    @Test
    public void testGetSecretWithValidPermissions() throws Exception {
        String expectedSecret = ((SlaveComputer) agent.getComputer()).getJnlpMac();

        MockAuthorizationStrategy authStrategy = new MockAuthorizationStrategy()
                .grant(Jenkins.READ).everywhere().to("test-user")
                .grant(Computer.CONNECT).everywhere().to("test-user");

        j.jenkins.setAuthorizationStrategy(authStrategy);

        JenkinsRule.WebClient webClient = j.createWebClient();
        webClient.login("test-user");

        String response = webClient.goTo(agentUrl, "text/plain")
                .getWebResponse().getContentAsString();

        assertEquals(expectedSecret, response);

    }

    @Test
    public void testGetSecretWithoutPermissions() throws Exception {
        MockAuthorizationStrategy authStrategy = new MockAuthorizationStrategy()
                .grant(Jenkins.READ).everywhere().to("test-user");

        j.jenkins.setAuthorizationStrategy(authStrategy);

        JenkinsRule.WebClient webClient = j.createWebClient();
        webClient.login("test-user");

        webClient.assertFails(agentUrl, 403);
    }

}
