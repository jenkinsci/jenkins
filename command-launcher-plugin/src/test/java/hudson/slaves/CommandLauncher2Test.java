/*
 * The MIT License
 *
 * Copyright 2017 CloudBees, Inc.
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

import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlTextInput;
import hudson.XmlFile;
import hudson.cli.CLICommand;
import hudson.cli.CLICommandInvoker;
import hudson.cli.UpdateNodeCommand;
import hudson.model.Computer;
import hudson.model.User;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import javax.annotation.CheckForNull;
import jenkins.model.Jenkins;
import org.apache.tools.ant.filters.StringInputStream;
import static org.hamcrest.Matchers.*;
import org.jenkinsci.plugins.command_launcher.CommandLanguage;
import org.jenkinsci.plugins.scriptsecurity.scripts.ScriptApproval;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.RestartableJenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;

public class CommandLauncher2Test {

    @Rule
    public RestartableJenkinsRule rr = new RestartableJenkinsRule();

    @Issue("SECURITY-478")
    @Test
    public void requireApproval() throws Exception {
        rr.addStep(new Statement() { // TODO .then, when using sufficiently new jenkins-test-harness
            @Override
            public void evaluate() throws Throwable {
                rr.j.jenkins.setSecurityRealm(rr.j.createDummySecurityRealm());
                rr.j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().
                    grant(Jenkins.ADMINISTER).everywhere().to("admin").
                    grant(Jenkins.READ, Computer.CONFIGURE).everywhere().to("dev"));
                ScriptApproval.get().preapprove("echo unconfigured", CommandLanguage.get());
                DumbSlave s = new DumbSlave("s", "/", new CommandLauncher("echo unconfigured"));
                rr.j.jenkins.addNode(s);
                // First, reconfigure using GUI.
                JenkinsRule.WebClient wc = rr.j.createWebClient().login("admin");
                HtmlForm form = wc.getPage(s, "configure").getFormByName("config");
                HtmlTextInput input = form.getInputByName("_.command");
                assertEquals("echo unconfigured", input.getText());
                input.setText("echo configured by GUI");
                rr.j.submit(form);
                s = (DumbSlave) rr.j.jenkins.getNode("s");
                assertEquals("echo configured by GUI", ((CommandLauncher) s.getLauncher()).getCommand());
                assertSerialForm(s, "echo configured by GUI");
                // Then by REST.
                String configDotXml = s.toComputer().getUrl() + "config.xml";
                String xml = wc.goTo(configDotXml, "application/xml").getWebResponse().getContentAsString();
                assertThat(xml, containsString("echo configured by GUI"));
                WebRequest req = new WebRequest(wc.createCrumbedUrl(configDotXml), HttpMethod.POST);
                req.setEncodingType(null);
                req.setRequestBody(xml.replace("echo configured by GUI", "echo configured by REST"));
                wc.getPage(req);
                s = (DumbSlave) rr.j.jenkins.getNode("s");
                assertEquals("echo configured by REST", ((CommandLauncher) s.getLauncher()).getCommand());
                assertSerialForm(s, "echo configured by REST");
                // Then by CLI.
                CLICommand cmd = new UpdateNodeCommand();
                cmd.setTransportAuth(User.get("admin").impersonate());
                assertThat(new CLICommandInvoker(rr.j, cmd).withStdin(new StringInputStream(xml.replace("echo configured by GUI", "echo configured by CLI"))).invokeWithArgs("s"), CLICommandInvoker.Matcher.succeededSilently());
                s = (DumbSlave) rr.j.jenkins.getNode("s");
                assertEquals("echo configured by CLI", ((CommandLauncher) s.getLauncher()).getCommand());
                assertEquals(Collections.emptySet(), ScriptApproval.get().getPendingScripts());
                assertSerialForm(s, "echo configured by CLI");
                // Now verify that all modes failed as dev. First as GUI.
                ScriptApproval.get().preapprove("echo configured by admin", CommandLanguage.get());
                s.setLauncher(new CommandLauncher("echo configured by admin"));
                s.save();
                wc = rr.j.createWebClient().login("dev");
                form = wc.getPage(s, "configure").getFormByName("config");
                input = form.getInputByName("_.command");
                assertEquals("echo configured by admin", input.getText());
                input.setText("echo GUI ATTACK");
                rr.j.submit(form);
                s = (DumbSlave) rr.j.jenkins.getNode("s");
                assertEquals("echo GUI ATTACK", ((CommandLauncher) s.getLauncher()).getCommand());
                Set<ScriptApproval.PendingScript> pendingScripts = ScriptApproval.get().getPendingScripts();
                assertEquals(1, pendingScripts.size());
                ScriptApproval.PendingScript pendingScript = pendingScripts.iterator().next();
                assertEquals(CommandLanguage.get(), pendingScript.getLanguage());
                assertEquals("echo GUI ATTACK", pendingScript.script);
                assertEquals("dev", pendingScript.getContext().getUser());
                ScriptApproval.get().denyScript(pendingScript.getHash());
                assertSerialForm(s, "echo GUI ATTACK");
                // Then by REST.
                req = new WebRequest(wc.createCrumbedUrl(configDotXml), HttpMethod.POST);
                req.setEncodingType(null);
                req.setRequestBody(xml.replace("echo configured by GUI", "echo REST ATTACK"));
                wc.getPage(req);
                s = (DumbSlave) rr.j.jenkins.getNode("s");
                assertEquals("echo REST ATTACK", ((CommandLauncher) s.getLauncher()).getCommand());
                pendingScripts = ScriptApproval.get().getPendingScripts();
                assertEquals(1, pendingScripts.size());
                pendingScript = pendingScripts.iterator().next();
                assertEquals(CommandLanguage.get(), pendingScript.getLanguage());
                assertEquals("echo REST ATTACK", pendingScript.script);
                assertEquals(/* deserialization, not recording user */ null, pendingScript.getContext().getUser());
                ScriptApproval.get().denyScript(pendingScript.getHash());
                assertSerialForm(s, "echo REST ATTACK");
                // Then by CLI.
                cmd = new UpdateNodeCommand();
                cmd.setTransportAuth(User.get("dev").impersonate());
                assertThat(new CLICommandInvoker(rr.j, cmd).withStdin(new StringInputStream(xml.replace("echo configured by GUI", "echo CLI ATTACK"))).invokeWithArgs("s"), CLICommandInvoker.Matcher.succeededSilently());
                s = (DumbSlave) rr.j.jenkins.getNode("s");
                assertEquals("echo CLI ATTACK", ((CommandLauncher) s.getLauncher()).getCommand());
                pendingScripts = ScriptApproval.get().getPendingScripts();
                assertEquals(1, pendingScripts.size());
                pendingScript = pendingScripts.iterator().next();
                assertEquals(CommandLanguage.get(), pendingScript.getLanguage());
                assertEquals("echo CLI ATTACK", pendingScript.script);
                assertEquals(/* ditto */null, pendingScript.getContext().getUser());
                ScriptApproval.get().denyScript(pendingScript.getHash());
                assertSerialForm(s, "echo CLI ATTACK");
                // Now also check that SYSTEM deserialization works after a restart.
            }
            private void assertSerialForm(DumbSlave s, @CheckForNull String expectedCommand) throws IOException {
                // cf. private methods in Nodes
                File nodesDir = new File(rr.j.jenkins.getRootDir(), "nodes");
                XmlFile configXml = new XmlFile(Jenkins.XSTREAM, new File(new File(nodesDir, s.getNodeName()), "config.xml"));
                assertThat(configXml.asString(), expectedCommand != null ? containsString("<agentCommand>" + expectedCommand + "</agentCommand>") : not(containsString("<agentCommand>")));
            }
        });
        rr.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                DumbSlave s = (DumbSlave) rr.j.jenkins.getNode("s");
                assertEquals("echo CLI ATTACK", ((CommandLauncher) s.getLauncher()).getCommand());
                Set<ScriptApproval.PendingScript> pendingScripts = ScriptApproval.get().getPendingScripts();
                assertEquals(1, pendingScripts.size());
                ScriptApproval.PendingScript pendingScript = pendingScripts.iterator().next();
                assertEquals(CommandLanguage.get(), pendingScript.getLanguage());
                assertEquals("echo CLI ATTACK", pendingScript.script);
                assertEquals(/* ditto */null, pendingScript.getContext().getUser());
            }
        });
    }

    @LocalData // saved by Hudson 1.215
    @Test
    public void ancientSerialForm() {
        rr.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                ComputerLauncher launcher = ((DumbSlave) rr.j.jenkins.getNode("test")).getLauncher();
                assertThat(launcher, instanceOf(CommandLauncher.class));
                assertEquals("echo from CLI", ((CommandLauncher) launcher).getCommand());
            }
        });
    }

}
