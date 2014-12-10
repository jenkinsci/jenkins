/*
 * The MIT License
 *
 * Copyright 2014 Jesse Glick.
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

package hudson.tools;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.JDK;
import hudson.model.Node;
import hudson.slaves.DumbSlave;
import hudson.slaves.JNLPLauncher;
import hudson.slaves.NodeProperty;
import hudson.slaves.RetentionStrategy;
import hudson.tasks.Shell;
import hudson.util.StreamTaskListener;
import java.nio.charset.Charset;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

public class InstallerTranslatorTest {

    @Rule public JenkinsRule r = new JenkinsRule();

    @Issue("JENKINS-23517")
    @Test public void offlineNodeForJDK() throws Exception {
        Node slave = new DumbSlave("disconnected-slave", null, "/wherever", "1", Node.Mode.NORMAL, null, new JNLPLauncher(), RetentionStrategy.NOOP, Collections.<NodeProperty<?>>emptyList());
        String globalDefaultLocation = "/usr/lib/jdk";
        JDK jdk = new JDK("my-jdk", globalDefaultLocation, Collections.singletonList(new InstallSourceProperty(Collections.singletonList(new CommandInstaller(null, "irrelevant", "/opt/jdk")))));
        r.jenkins.getJDKs().add(jdk);
        FreeStyleProject p = r.createFreeStyleProject();
        p.setJDK(jdk);
        StreamTaskListener listener = new StreamTaskListener(System.out, Charset.defaultCharset());
        String javaHomeProp = "JAVA_HOME"; // cf. JDK.buildEnvVars
        assertEquals(globalDefaultLocation, p.getEnvironment(slave, listener).get(javaHomeProp));
        String slaveDefaultLocation = "/System/JDK";
        slave.getNodeProperties().add(new ToolLocationNodeProperty(new ToolLocationNodeProperty.ToolLocation((ToolDescriptor) jdk.getDescriptor(), jdk.getName(), slaveDefaultLocation)));
        assertEquals(slaveDefaultLocation, p.getEnvironment(slave, listener).get(javaHomeProp));
    }

    @Issue("JENKINS-17667")
    @Test public void multipleSlavesAndTools() throws Exception {
        Node slave1 = r.createSlave();
        Node slave2 = r.createSlave();
        JDK jdk1 = new JDK("jdk1", null, Collections.singletonList(new InstallSourceProperty(Collections.singletonList(new CommandInstaller(null, "echo installed jdk1", "/opt/jdk1")))));
        JDK jdk2 = new JDK("jdk2", null, Collections.singletonList(new InstallSourceProperty(Collections.singletonList(new CommandInstaller(null, "echo installed jdk2", "/opt/jdk2")))));
        r.jenkins.getJDKs().add(jdk1);
        r.jenkins.getJDKs().add(jdk2);
        FreeStyleProject p = r.createFreeStyleProject();
        p.setJDK(jdk1);
        p.getBuildersList().add(new Shell("echo $JAVA_HOME"));
        p.setAssignedNode(slave1);
        FreeStyleBuild b1 = r.buildAndAssertSuccess(p);
        r.assertLogContains("installed jdk1", b1);
        r.assertLogContains("/opt/jdk1", b1);
        p.setJDK(jdk2);
        FreeStyleBuild b2 = r.buildAndAssertSuccess(p);
        r.assertLogContains("installed jdk2", b2);
        r.assertLogContains("/opt/jdk2", b2);
        FreeStyleBuild b3 = r.buildAndAssertSuccess(p);
        // An installer is run for every build, and it is up to a CommandInstaller configuration to do any up-to-date check.
        r.assertLogContains("installed jdk2", b3);
        r.assertLogContains("/opt/jdk2", b3);
        p.setAssignedNode(slave2);
        FreeStyleBuild b4 = r.buildAndAssertSuccess(p);
        r.assertLogContains("installed jdk2", b4);
        r.assertLogContains("/opt/jdk2", b4);
        p.setJDK(jdk1);
        FreeStyleBuild b5 = r.buildAndAssertSuccess(p);
        r.assertLogContains("installed jdk1", b5);
        r.assertLogContains("/opt/jdk1", b5);
        FreeStyleBuild b6 = r.buildAndAssertSuccess(p);
        r.assertLogContains("installed jdk1", b6);
        r.assertLogContains("/opt/jdk1", b6);
    }

}
