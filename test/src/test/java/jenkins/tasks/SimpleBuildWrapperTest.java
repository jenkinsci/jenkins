/*
 * The MIT License
 *
 * Copyright 2015 Jesse Glick.
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

package jenkins.tasks;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Functions;
import hudson.Launcher;
import hudson.console.ConsoleLogFilter;
import hudson.console.LineTransformationOutputStream;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.JDK;
import hudson.model.Run;
import hudson.model.Slave;
import hudson.model.TaskListener;
import hudson.slaves.CommandLauncher;
import hudson.slaves.NodeProperty;
import hudson.slaves.RetentionStrategy;
import hudson.slaves.SlaveComputer;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.tasks.Shell;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.Collections;
import java.util.Locale;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Assume;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.CaptureEnvironmentBuilder;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestBuilder;
import org.jvnet.hudson.test.TestExtension;

public class SimpleBuildWrapperTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public JenkinsRule r = new JenkinsRule();
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test public void envOverride() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        p.getBuildWrappersList().add(new WrapperWithEnvOverride());
        CaptureEnvironmentBuilder captureEnvironment = new CaptureEnvironmentBuilder();
        p.getBuildersList().add(captureEnvironment);
        FreeStyleBuild b = r.buildAndAssertSuccess(p);
        String path = captureEnvironment.getEnvVars().get("PATH");
        assertTrue(path, path.startsWith(b.getWorkspace().child("bin").getRemote() + File.pathSeparatorChar));
    }
    public static class WrapperWithEnvOverride extends SimpleBuildWrapper {
        @Override public void setUp(Context context, Run<?,?> build, FilePath workspace, Launcher launcher, TaskListener listener, EnvVars initialEnvironment) throws IOException, InterruptedException {
            assertNotNull(initialEnvironment.get("PATH"));
            context.env("PATH+STUFF", workspace.child("bin").getRemote());
        }
        @TestExtension("envOverride") public static class DescriptorImpl extends BuildWrapperDescriptor {
            @Override public String getDisplayName() {
                return "WrapperWithEnvOverride";
            }
            @Override public boolean isApplicable(AbstractProject<?,?> item) {
                return true;
            }
        }
    }

    @Test public void envOverrideExpand() throws Exception {
        Assume.assumeFalse(Functions.isWindows());
        FreeStyleProject p = r.createFreeStyleProject();
        p.getBuildWrappersList().add(new WrapperWithEnvOverrideExpand());
        SpecialEnvSlave slave = new SpecialEnvSlave(tmp.getRoot(), r.createComputerLauncher(null));
        r.jenkins.addNode(slave);
        p.setAssignedNode(slave);
        JDK jdk = new JDK("test", "/opt/jdk");
        r.jenkins.getJDKs().add(jdk);
        p.setJDK(jdk);
        CaptureEnvironmentBuilder captureEnvironment = new CaptureEnvironmentBuilder();
        p.getBuildersList().add(captureEnvironment);
        p.getBuildersList().add(new Shell("echo effective PATH=$PATH"));
        FreeStyleBuild b = r.buildAndAssertSuccess(p);
        String expected = "/home/jenkins/extra/bin:/opt/jdk/bin:/usr/bin:/bin";
        assertEquals(expected, captureEnvironment.getEnvVars().get("PATH"));
        // TODO why is /opt/jdk/bin added twice? In CommandInterpreter.perform, envVars right before Launcher.launch is correct, but this somehow sneaks in.
        r.assertLogContains("effective PATH=/opt/jdk/bin:" + expected, b);
    }
    public static class WrapperWithEnvOverrideExpand extends SimpleBuildWrapper {
        @Override public void setUp(Context context, Run<?,?> build, FilePath workspace, Launcher launcher, TaskListener listener, EnvVars initialEnvironment) throws IOException, InterruptedException {
            assertEquals("/opt/jdk/bin:/usr/bin:/bin", initialEnvironment.get("PATH"));
            assertEquals("/home/jenkins", initialEnvironment.get("HOME"));
            context.env("EXTRA", "${HOME}/extra");
            context.env("PATH+EXTRA", "${EXTRA}/bin");
        }
        @TestExtension("envOverrideExpand") public static class DescriptorImpl extends BuildWrapperDescriptor {
            @Override public String getDisplayName() {
                return "WrapperWithEnvOverrideExpand";
            }
            @Override public boolean isApplicable(AbstractProject<?,?> item) {
                return true;
            }
        }
    }
    private static class SpecialEnvSlave extends Slave {
        SpecialEnvSlave(File remoteFS, CommandLauncher launcher) throws Descriptor.FormException, IOException {
            super("special", "SpecialEnvSlave", remoteFS.getAbsolutePath(), 1, Mode.NORMAL, "", launcher, RetentionStrategy.NOOP, Collections.<NodeProperty<?>>emptyList());
        }
        @Override public Computer createComputer() {
            return new SpecialEnvComputer(this);
        }
    }
    private static class SpecialEnvComputer extends SlaveComputer {
        SpecialEnvComputer(SpecialEnvSlave slave) {
            super(slave);
        }
        @Override public EnvVars getEnvironment() throws IOException, InterruptedException {
            EnvVars env = super.getEnvironment();
            env.put("PATH", "/usr/bin:/bin");
            env.put("HOME", "/home/jenkins");
            return env;
        }
    }

    @Test public void disposer() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        p.getBuildWrappersList().add(new WrapperWithDisposer());
        r.assertLogContains("ran DisposerImpl", r.buildAndAssertSuccess(p));
    }
    public static class WrapperWithDisposer extends SimpleBuildWrapper {
        @Override public void setUp(Context context, Run<?,?> build, FilePath workspace, Launcher launcher, TaskListener listener, EnvVars initialEnvironment) throws IOException, InterruptedException {
            context.setDisposer(new DisposerImpl());
        }
        private static final class DisposerImpl extends Disposer {
            private static final long serialVersionUID = 1;
            @Override public void tearDown(Run<?,?> build, FilePath workspace, Launcher launcher, TaskListener listener) throws IOException, InterruptedException {
                listener.getLogger().println("ran DisposerImpl");
            }
        }
        @TestExtension("disposer") public static class DescriptorImpl extends BuildWrapperDescriptor {
            @Override public String getDisplayName() {
                return "WrapperWithDisposer";
            }
            @Override public boolean isApplicable(AbstractProject<?,?> item) {
                return true;
            }
        }
    }

    @Issue("JENKINS-27392")
    @Test public void loggerDecorator() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        p.getBuildWrappersList().add(new WrapperWithLogger());
        p.getBuildersList().add(new TestBuilder() {
            @Override public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                listener.getLogger().println("sending a message");
                return true;
            }
        });
        r.assertLogContains("SENDING A MESSAGE", r.buildAndAssertSuccess(p));
    }
    public static class WrapperWithLogger extends SimpleBuildWrapper {
        @Override public void setUp(Context context, Run<?,?> build, FilePath workspace, Launcher launcher, TaskListener listener, EnvVars initialEnvironment) throws IOException, InterruptedException {}
        @Override public ConsoleLogFilter createLoggerDecorator(Run<?,?> build) {
            return new UpcaseFilter();
        }
        private static class UpcaseFilter extends ConsoleLogFilter implements Serializable {
            private static final long serialVersionUID = 1;
            @SuppressWarnings("rawtypes") // inherited
            @Override public OutputStream decorateLogger(AbstractBuild _ignore, final OutputStream logger) throws IOException, InterruptedException {
                return new LineTransformationOutputStream() {
                    @Override protected void eol(byte[] b, int len) throws IOException {
                        logger.write(new String(b, 0, len).toUpperCase(Locale.ROOT).getBytes());
                    }
                };
            }
        }
        @TestExtension("loggerDecorator") public static class DescriptorImpl extends BuildWrapperDescriptor {
            @Override public String getDisplayName() {
                return "WrapperWithLogger";
            }
            @Override public boolean isApplicable(AbstractProject<?,?> item) {
                return true;
            }
        }
    }

}
