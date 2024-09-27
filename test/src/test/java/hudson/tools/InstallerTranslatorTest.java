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

import static org.junit.Assert.assertEquals;

import hudson.Functions;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.JDK;
import hudson.model.Node;
import hudson.slaves.DumbSlave;
import hudson.slaves.JNLPLauncher;
import hudson.slaves.RetentionStrategy;
import hudson.tasks.BatchFile;
import hudson.tasks.Shell;
import hudson.util.StreamTaskListener;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

public class InstallerTranslatorTest {

    @Rule public JenkinsRule r = new JenkinsRule();

    @Issue("JENKINS-23517")
    @Test public void offlineNodeForJDK() throws Exception {
        Node slave = new DumbSlave("disconnected-slave", null, "/wherever", "1", Node.Mode.NORMAL, null, new JNLPLauncher(), RetentionStrategy.NOOP, Collections.emptyList());
        String globalDefaultLocation = "/usr/lib/jdk";
        JDK jdk = new JDK("my-jdk", globalDefaultLocation, List.of(new InstallSourceProperty(List.of(new CommandInstaller(null, "irrelevant", "/opt/jdk")))));
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
        String jdk1Path = Functions.isWindows() ? "C:\\jdk1" : "/opt/jdk1";
        String jdk2Path = Functions.isWindows() ? "C:\\jdk2" : "/opt/jdk2";
        JDK jdk1 = new JDK(
                "jdk1",
                null,
                List.of(new InstallSourceProperty(List.of(
                        Functions.isWindows()
                                ? new BatchCommandInstaller(null, "echo installed jdk1", jdk1Path)
                                : new CommandInstaller(null, "echo installed jdk1", jdk1Path)))));
        JDK jdk2 = new JDK(
                "jdk2",
                null,
                List.of(new InstallSourceProperty(List.of(
                        Functions.isWindows()
                                ? new BatchCommandInstaller(null, "echo installed jdk2", jdk2Path)
                                : new CommandInstaller(null, "echo installed jdk2", jdk2Path)))));
        r.jenkins.getJDKs().add(jdk1);
        r.jenkins.getJDKs().add(jdk2);
        FreeStyleProject p = r.createFreeStyleProject();
        p.setJDK(jdk1);
        p.getBuildersList().add(Functions.isWindows() ? new BatchFile("echo %JAVA_HOME%") : new Shell("echo $JAVA_HOME"));
        p.setAssignedNode(r.createSlave());
        FreeStyleBuild b1 = r.buildAndAssertSuccess(p);
        r.assertLogContains("installed jdk1", b1);
        r.assertLogContains(jdk1Path, b1);
        p.setJDK(jdk2);
        FreeStyleBuild b2 = r.buildAndAssertSuccess(p);
        r.assertLogContains("installed jdk2", b2);
        r.assertLogContains(jdk2Path, b2);
        FreeStyleBuild b3 = r.buildAndAssertSuccess(p);
        // An installer is run for every build, and it is up to a CommandInstaller configuration to do any up-to-date check.
        r.assertLogContains("installed jdk2", b3);
        r.assertLogContains(jdk2Path, b3);
        p.setAssignedNode(r.createSlave());
        FreeStyleBuild b4 = r.buildAndAssertSuccess(p);
        r.assertLogContains("installed jdk2", b4);
        r.assertLogContains(jdk2Path, b4);
        p.setJDK(jdk1);
        FreeStyleBuild b5 = r.buildAndAssertSuccess(p);
        r.assertLogContains("installed jdk1", b5);
        r.assertLogContains(jdk1Path, b5);
        FreeStyleBuild b6 = r.buildAndAssertSuccess(p);
        r.assertLogContains("installed jdk1", b6);
        r.assertLogContains(jdk1Path, b6);
    }

    @Issue("JENKINS-26940")
    @Test
    public void testMessageLoggedWhenNoInstallerFound() throws Exception {
        final CommandInstaller ci = new CommandInstaller("wrong1", "echo hello", "/opt/jdk");
        final BatchCommandInstaller bci = new BatchCommandInstaller("wrong2", "echo hello", "/opt/jdk2");
        InstallSourceProperty isp = new InstallSourceProperty(Arrays.asList(ci, bci));

        JDK jdk = new JDK("jdk", null, List.of(isp));
        r.jenkins.getJDKs().add(jdk);


        FreeStyleProject p = r.createFreeStyleProject();
        p.setJDK(jdk);
        p.getBuildersList().add(Functions.isWindows() ? new BatchFile("echo %JAVA_HOME%") : new Shell("echo $JAVA_HOME"));
        FreeStyleBuild b1 = r.buildAndAssertSuccess(p);
        r.assertLogContains(hudson.tools.Messages.CannotBeInstalled(ci.getDescriptor().getDisplayName(), jdk.getName(), r.jenkins.getDisplayName()), b1);
        r.assertLogContains(hudson.tools.Messages.CannotBeInstalled(bci.getDescriptor().getDisplayName(), jdk.getName(), r.jenkins.getDisplayName()), b1);
    }

    @Issue("JENKINS-26940")
    @Test
    public void testNoMessageLoggedWhenAnyInstallerFound() throws Exception {
        final AbstractCommandInstaller ci = Functions.isWindows()
                ? new BatchCommandInstaller("wrong1", "echo hello", "C:\\jdk")
                : new CommandInstaller("wrong1", "echo hello", "/opt/jdk");
        final AbstractCommandInstaller ci2 = Functions.isWindows()
                ? new BatchCommandInstaller("built-in", "echo hello", "C:\\jdk2")
                : new CommandInstaller("built-in", "echo hello", "/opt/jdk2");
        InstallSourceProperty isp = new InstallSourceProperty(Arrays.asList(ci, ci2));

        JDK jdk = new JDK("jdk", null, List.of(isp));
        r.jenkins.getJDKs().add(jdk);


        FreeStyleProject p = r.createFreeStyleProject();
        p.setJDK(jdk);
        p.getBuildersList().add(Functions.isWindows() ? new BatchFile("echo %JAVA_HOME%") : new Shell("echo $JAVA_HOME"));
        FreeStyleBuild b1 = r.buildAndAssertSuccess(p);
        r.assertLogNotContains(ci.getDescriptor().getDisplayName(), b1);
    }

}
