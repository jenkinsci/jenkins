/*
 * The MIT License
 *
 * Copyright 2022 CloudBees, Inc.
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

package hudson.slaves;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;

import hudson.ExtensionList;
import hudson.PluginWrapper;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Slave;
import jenkins.agents.WebSocketAgentsTest;
import jenkins.slaves.JnlpSlaveAgentProtocol4;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.For;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.PrefixedOutputStream;
import org.jvnet.hudson.test.junit.jupiter.InboundAgentExtension;
import org.jvnet.hudson.test.junit.jupiter.RealJenkinsExtension;

@For({JNLPLauncher.class, JnlpSlaveAgentProtocol4.class})
class JNLPLauncherRealTest {

    private static final String STATIC_AGENT_NAME = "static";

    @RegisterExtension
    private final RealJenkinsExtension rr = new RealJenkinsExtension().withColor(PrefixedOutputStream.Color.BLUE);

    @RegisterExtension
    private final InboundAgentExtension iar = new InboundAgentExtension();

    @Issue("JEP-230")
    @Test
    void smokes() throws Throwable {
        /* Since RealJenkinsRuleInit.jpi will load detached and test scope plugins, to reproduce a failure use:
        rr.includeTestClasspathPlugins(false);
        FileUtils.touch(new File(rr.getHome(), "plugins/instance-identity.jpi.disabled"));
        */
        then(false);
    }

    /**
     * Simplified version of {@link WebSocketAgentsTest#smokes} just checking Jetty/Winstone.
     */
    @Issue("JENKINS-68933")
    @Test
    void webSocket() throws Throwable {
        then(true);
    }

    private void then(boolean websocket) throws Throwable {
        try {
            rr.startJenkins();
            InboundAgentExtension.Options.Builder options = InboundAgentExtension.Options.newBuilder().name(STATIC_AGENT_NAME).color(PrefixedOutputStream.Color.RED);
            if (websocket) {
                options = options.webSocket();
            }
            iar.createAgent(rr, options.build());
            rr.runRemotely(new RunJobStep(STATIC_AGENT_NAME, websocket));
        } finally {
            iar.stop(rr, STATIC_AGENT_NAME);
        }
    }

    private static class RunJobStep implements RealJenkinsExtension.Step {
        private final String agentName;
        private final boolean webSocket;

        RunJobStep(String agentName, boolean webSocket) {
            this.agentName = agentName;
            this.webSocket = webSocket;
        }

        @Override
        public void run(JenkinsRule r) throws Throwable {
            for (PluginWrapper plugin : r.jenkins.pluginManager.getPlugins()) {
                System.err.println(plugin + " active=" + plugin.isActive() + " enabled=" + plugin.isEnabled());
            }
            assertThat(ExtensionList.lookupSingleton(JNLPLauncher.DescriptorImpl.class).isWebSocketSupported(), is(true));
            Slave agent = (Slave) r.jenkins.getNode(agentName);
            FreeStyleProject p = r.createFreeStyleProject();
            p.setAssignedNode(agent);
            FreeStyleBuild b = r.buildAndAssertSuccess(p);
            if (webSocket) {
                assertThat(agent.toComputer().getSystemProperties(), hasKey("os.name"));
            }
            System.err.println(JenkinsRule.getLog(b));
        }
    }
}
