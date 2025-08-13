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

package jenkins.slaves;

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
import hudson.model.Slave;
import hudson.model.TaskListener;
import hudson.model.labels.LabelAtom;
import hudson.node_monitors.AbstractAsyncNodeMonitorDescriptor;
import hudson.node_monitors.AbstractNodeMonitorDescriptor;
import hudson.node_monitors.NodeMonitor;
import hudson.slaves.ComputerLauncher;
import hudson.tasks.BatchFile;
import hudson.tasks.Shell;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.util.Collection;
import jenkins.security.MasterToSlaveCallable;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.SimpleCommandLauncher;
import org.jvnet.hudson.test.TestBuilder;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.JenkinsSessionExtension;

/**
 * Tests for old Remoting agent versions
 */
class OldRemotingAgentTest {

    @RegisterExtension
    private final JenkinsSessionExtension session = new JenkinsExtensionWithOldAgent();

    @TempDir
    private File tmpDir;

    private static File agentJar;

    @BeforeEach
    void extractAgent() throws Exception {
        agentJar = new File(tmpDir, "old-agent.jar");
        FileUtils.copyURLToFile(OldRemotingAgentTest.class.getResource("/old-remoting/remoting-minimum-supported.jar"), agentJar);
    }

    @Test
    @Issue("JENKINS-48761")
    void shouldBeAbleToConnectAgentWithMinimumSupportedVersion() throws Throwable {
        session.then(j -> {
            Label agentLabel = new LabelAtom("old-agent");
            Slave agent = j.createOnlineSlave(agentLabel);
            boolean isUnix = agent.getComputer().isUnix();
            assertThat("Received wrong agent version. A minimum supported version is expected",
                    agent.getComputer().getSlaveVersion(),
                    equalTo(RemotingVersionInfo.getMinimumSupportedVersion().toString()));

            // Just ensure we are able to run something on the agent
            FreeStyleProject project = j.createFreeStyleProject("foo");
            project.setAssignedLabel(agentLabel);
            project.getBuildersList().add(isUnix ? new Shell("echo Hello") : new BatchFile("echo 'hello'"));
            j.buildAndAssertSuccess(project);

            // Run agent monitors
            NodeMonitorAssert.assertMonitors(NodeMonitor.getAll(), agent.getComputer());
        });
    }

    @Issue("JENKINS-55257")
    @Test
    void remoteConsoleNote() throws Throwable {
        session.then(j -> {
            Slave agent = j.createOnlineSlave();
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
        });
    }

    private static final class RemoteConsoleNotePrinter extends MasterToSlaveCallable<Void, IOException> {
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
    private static final class JenkinsExtensionWithOldAgent extends JenkinsSessionExtension {

        private int port;
        private Description description;

        @Override
        public void beforeEach(ExtensionContext context) {
            super.beforeEach(context);
            description = Description.createTestDescription(
                    context.getTestClass().map(Class::getName).orElse(null),
                    context.getTestMethod().map(Method::getName).orElse(null),
                    context.getTestMethod().map(Method::getAnnotations).orElse(null));
        }

        @Override
        public void then(Step s) throws Throwable {
            CustomJenkinsRule r = new CustomJenkinsRule(getHome(), port);
            r.apply(
                    new Statement() {
                        @Override
                        public void evaluate() throws Throwable {
                            port = r.getPort();
                            s.run(r);
                        }
                    },
                    description
            ).evaluate();
        }

        private static final class CustomJenkinsRule extends JenkinsRule {

            CustomJenkinsRule(File home, int port) {
                with(() -> home);
                localPort = port;
            }

            int getPort() {
                return localPort;
            }

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
