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
import static org.hamcrest.Matchers.is;

import hudson.ExtensionList;
import hudson.PluginWrapper;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Slave;
import hudson.util.FormValidation;
import jenkins.slaves.JnlpSlaveAgentProtocol4;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.For;
import org.jvnet.hudson.test.InboundAgentRule;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.RealJenkinsRule;

@For({JNLPLauncher.class, JnlpSlaveAgentProtocol4.class})
public class JNLPLauncherRealTest {

    @Rule public RealJenkinsRule rr = new RealJenkinsRule().includeTestClasspathPlugins(false);

    @Issue("JEP-230")
    @Test public void smokes() throws Throwable {
        /* Since RealJenkinsRuleInit.jpi will load detached plugins, to reproduce a failure use:
        FileUtils.touch(new File(rr.getHome(), "plugins/instance-identity.jpi.disabled"));
        */
        rr.then(JNLPLauncherRealTest::_smokes);
    }

    private static void _smokes(JenkinsRule r) throws Throwable {
        InboundAgentRule inboundAgents = new InboundAgentRule(); // cannot use @Rule since it would not be accessible from the controller JVM
        inboundAgents.apply(new Statement() {
            @Override public void evaluate() throws Throwable {
                for (PluginWrapper plugin : r.jenkins.pluginManager.getPlugins()) {
                    System.err.println(plugin + " active=" + plugin.isActive() + " enabled=" + plugin.isEnabled());
                }
                assertThat(ExtensionList.lookupSingleton(JNLPLauncher.DescriptorImpl.class).doCheckWebSocket(false, null).kind, is(FormValidation.Kind.OK));
                Slave agent = inboundAgents.createAgent(r, "static");
                FreeStyleProject p = r.createFreeStyleProject();
                p.setAssignedNode(agent);
                FreeStyleBuild b = r.buildAndAssertSuccess(p);
                System.err.println(JenkinsRule.getLog(b));
            }
        }, Description.EMPTY).evaluate();

    }

}
