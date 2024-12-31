/*
 * The MIT License
 *
 * Copyright (c) 2018, CloudBees, Inc.
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import hudson.EnvVars;
import hudson.Launcher;
import hudson.MarkupText;
import hudson.console.ConsoleAnnotationDescriptor;
import hudson.console.ConsoleAnnotator;
import hudson.console.ConsoleNote;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Computer;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Label;
import hudson.model.Agent;
import hudson.model.TaskListener;
import hudson.model.labels.LabelAtom;
import hudson.node_monitors.AbstractAsyncNodeMonitorDescriptor;
import hudson.node_monitors.AbstractNodeMonitorDescriptor;
import hudson.node_monitors.NodeMonitor;
import hudson.agents.ComputerLauncher;
import hudson.tasks.BatchFile;
import hudson.tasks.Shell;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.util.Collection;
import jenkins.security.MasterToAgentCallable;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.SimpleCommandLauncher;
import org.jvnet.hudson.test.TestBuilder;
import org.jvnet.hudson.test.TestExtension;

/**
 * Tests for old Remoting agent versions
 */
public class OldRemotingAgentTest {

    @Rule
    public JenkinsRule j = new JenkinsRuleWithOldAgent();

    @Rule
    public TemporaryFolder tmpDir = new TemporaryFolder();

    private File agentJar;

    @Before
    public void extractAgent() throws Exception {
        agentJar = new File(tmpDir.getRoot(), "old-agent.jar");
        FileUtils.copyURLToFile(OldRemotingAgentTest.class.getResource("/old-remoting/remoting-minimum-supported.jar"), agentJar);
    }

    @Test
    @Issue("JENKINS-48761")
    public void shouldBeAbleToConnectAgentWithMinimumSupportedVersion() throws Exception {
        Label agentLabel = new LabelAtom("old-agent");
        Agent agent = j.createOnlineAgent(agentLabel);
        boolean isUnix = agent.getComputer().isUnix();
        assertThat("Received wrong agent version. A minimum supported version is expected",
                agent.getComputer().getAgentVersion(),
                equalTo(RemotingVersionInfo.getMinimumSupportedVersion().toString()));

        // Just ensure we are able to run something on the agent
        FreeStyleProject project = j.createFreeStyleProject("foo");
        project.setAssignedLabel(agentLabel);
        project.getBuildersList().add(isUnix ? new Shell("echo Hello") : new BatchFile("echo 'hello'"));
        j.buildAndAssertSuccess(project);

        // Run agent monitors
        NodeMonitorAssert.assertMonitors(NodeMonitor.getAll(), agent.getComputer());
    }

    @Issue("JENKINS-55257")
    @Test
    public void remoteConsoleNote() throws Exception {
        Agent agent = j.createOnlineAgent();
        FreeStyleProject project = j.createFreeStyleProject();
        project.setAssignedLabel(agent.getSelfLabel());
        project.getBuildersList().add(new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                build.getWorkspace().act(new RemoteConsoleNotePrinter(listener));
                return true;
            }
        });
        FreeStyleBuild b = j.buildAndAssertSuccess(project);
        StringWriter sw = new StringWriter();
        // The note will not actually work by default; we just want to ensure that the attempt is ignored without breaking the build.
        // But for purposes of testing, check that the note really made it into the log.
        boolean insecureOriginal = ConsoleNote.INSECURE;
        ConsoleNote.INSECURE = true;
        try {
            b.getLogText().writeHtmlTo(0, sw);
        } finally {
            ConsoleNote.INSECURE = insecureOriginal;
        }
        assertThat(sw.toString(), containsString("@@@ANNOTATED@@@"));
    }

    private static final class RemoteConsoleNotePrinter extends MasterToAgentCallable<Void, IOException> {
        private final TaskListener listener;

        RemoteConsoleNotePrinter(TaskListener listener) {
            this.listener = listener;
        }

        @Override
        public Void call() throws IOException {
            listener.annotate(new RemoteConsoleNote());
            listener.getLogger().println();
            return null;
        }
    }

    public static final class RemoteConsoleNote extends ConsoleNote<Object> {
        @Override
        public ConsoleAnnotator<Object> annotate(Object context, MarkupText text, int charPos) {
            text.addMarkup(charPos, "@@@ANNOTATED@@@");
            return null;
        }

        @TestExtension("remoteConsoleNote")
        public static final class DescriptorImpl extends ConsoleAnnotationDescriptor {}
    }

    //TODO: move the logic to JTH
    private class JenkinsRuleWithOldAgent extends JenkinsRule {

        @Override
        public ComputerLauncher createComputerLauncher(EnvVars env) throws URISyntaxException, IOException {

            // EnvVars are ignored, simple Command Launcher does not offer this API in public
            int sz = this.jenkins.getNodes().size();
            return new SimpleCommandLauncher(String.format("\"%s/bin/java\" %s -jar \"%s\"",
                    System.getProperty("java.home"),
                    SLAVE_DEBUG_PORT > 0 ? " -Xdebug -Xrunjdwp:transport=dt_socket,server=y,address=" + (SLAVE_DEBUG_PORT + sz) : "",
                    agentJar.getAbsolutePath()));
        }
    }

    private static class NodeMonitorAssert extends NodeMonitor {

        static void assertMonitors(Collection<NodeMonitor> toCheck, Computer c) {
            for (NodeMonitor monitor : toCheck) {
                assertMonitor(monitor, c);
            }
        }

        static void assertMonitor(NodeMonitor monitor, Computer c) {
            AbstractNodeMonitorDescriptor<?> descriptor = monitor.getDescriptor();
            final Method monitorMethod;
            try {
                monitorMethod = AbstractAsyncNodeMonitorDescriptor.class.getDeclaredMethod("monitor", Computer.class);
            } catch (NoSuchMethodException ex) {
                //TODO: make the API visible for testing?
                throw new AssertionError("Cannot invoke monitor " + monitor + ", no monitor(Computer.class) method in the Descriptor. It will be ignored. ", ex);
            }
            try {
                monitorMethod.setAccessible(true);
                Object res = monitorMethod.invoke(descriptor, c);
                System.out.println("Successfully executed monitor " + monitor);
            } catch (Exception ex) {
                throw new AssertionError("Failed to run monitor " + monitor + " for computer " + c, ex);
            } finally {
                monitorMethod.setAccessible(false);
            }
        }
    }

}
