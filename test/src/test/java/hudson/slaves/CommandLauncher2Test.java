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

import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlTextInput;
import hudson.cli.CLICommand;
import hudson.cli.CLICommandInvoker;
import hudson.cli.UpdateNodeCommand;
import hudson.model.Computer;
import hudson.model.User;
import java.net.HttpURLConnection;
import jenkins.model.Jenkins;
import org.apache.tools.ant.filters.StringInputStream;
import static org.hamcrest.Matchers.containsString;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.RestartableJenkinsRule;

public class CommandLauncher2Test {

    @Rule
    public RestartableJenkinsRule rr = new RestartableJenkinsRule();

    @Issue("SECURITY-478")
    @Test
    public void requireRunScripts() throws Exception {
        rr.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                rr.j.jenkins.setSecurityRealm(rr.j.createDummySecurityRealm());
                rr.j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().
                    grant(Jenkins.ADMINISTER).everywhere().to("admin").
                    grant(Jenkins.READ, Computer.CONFIGURE).everywhere().to("dev"));
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
                // Then by CLI.
                CLICommand cmd = new UpdateNodeCommand();
                cmd.setTransportAuth(User.get("admin").impersonate());
                assertThat(new CLICommandInvoker(rr.j, cmd).withStdin(new StringInputStream(xml.replace("echo configured by GUI", "echo configured by CLI"))).invokeWithArgs("s"), CLICommandInvoker.Matcher.succeededSilently());
                s = (DumbSlave) rr.j.jenkins.getNode("s");
                assertEquals("echo configured by CLI", ((CommandLauncher) s.getLauncher()).getCommand());
                // Now verify that all modes failed as dev. First as GUI.
                s.setLauncher(new CommandLauncher("echo configured by admin"));
                wc = rr.j.createWebClient().login("dev");
                form = wc.getPage(s, "configure").getFormByName("config");
                input = form.getInputByName("_.command");
                assertEquals("echo configured by admin", input.getText());
                input.setText("echo ATTACK");
                try {
                    rr.j.submit(form);
                    fail();
                } catch (FailingHttpStatusCodeException x) {
                    assertEquals("403 would be more natural but Descriptor.newInstance wraps AccessDeniedException2 in Error", 500, x.getStatusCode());
                }
                s = (DumbSlave) rr.j.jenkins.getNode("s");
                assertEquals("echo configured by admin", ((CommandLauncher) s.getLauncher()).getCommand());
                // Then by REST.
                req = new WebRequest(wc.createCrumbedUrl(configDotXml), HttpMethod.POST);
                req.setEncodingType(null);
                req.setRequestBody(xml.replace("echo configured by GUI", "echo ATTACK"));
                try {
                    wc.getPage(req);
                } catch (FailingHttpStatusCodeException x) {
                    assertEquals(HttpURLConnection.HTTP_FORBIDDEN, x.getStatusCode());
                }
                s = (DumbSlave) rr.j.jenkins.getNode("s");
                assertNotEquals(CommandLauncher.class, s.getLauncher().getClass()); // currently seems to reset it to JNLPLauncher, whatever
                s.setLauncher(new CommandLauncher("echo configured by admin"));
                // Then by CLI.
                cmd = new UpdateNodeCommand();
                cmd.setTransportAuth(User.get("dev").impersonate());
                assertThat(new CLICommandInvoker(rr.j, cmd).withStdin(new StringInputStream(xml.replace("echo configured by GUI", "echo ATTACK"))).invokeWithArgs("s"),
                    CLICommandInvoker.Matcher./* gets swallowed by RobustReflectionConverter, hmm*/succeededSilently());
                s = (DumbSlave) rr.j.jenkins.getNode("s");
                assertNotEquals(CommandLauncher.class, s.getLauncher().getClass());
                // Now also check that SYSTEM deserialization works after a restart.
                s.setLauncher(new CommandLauncher("echo configured by admin"));
                s.save();
            }
        });
        rr.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                DumbSlave s = (DumbSlave) rr.j.jenkins.getNode("s");
                assertEquals("echo configured by admin", ((CommandLauncher) s.getLauncher()).getCommand());
            }
        });
    }

}
