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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

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
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.Slave;
import hudson.model.TaskListener;
import hudson.scm.ChangeLogParser;
import hudson.scm.SCM;
import hudson.scm.SCMRevisionState;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.RetentionStrategy;
import hudson.slaves.SlaveComputer;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.tasks.Shell;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serial;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.CaptureEnvironmentBuilder;
import org.jvnet.hudson.test.FailureBuilder;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestBuilder;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class SimpleBuildWrapperTest {

    @TempDir
    private File tmp;
    private JenkinsRule r;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        r = rule;
    }

    @Test
    void envOverride() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        p.getBuildWrappersList().add(new WrapperWithEnvOverride());
        CaptureEnvironmentBuilder captureEnvironment = new CaptureEnvironmentBuilder();
        p.getBuildersList().add(captureEnvironment);
        FreeStyleBuild b = r.buildAndAssertSuccess(p);
        String path = captureEnvironment.getEnvVars().get("PATH");
        assertTrue(path.startsWith(b.getWorkspace().child("bin").getRemote() + File.pathSeparatorChar), path);
    }

    public static class WrapperWithEnvOverride extends SimpleBuildWrapper {
        @Override public void setUp(Context context, Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener, EnvVars initialEnvironment) throws IOException, InterruptedException {
            assertNotNull(initialEnvironment.get("PATH"));
            context.env("PATH+STUFF", workspace.child("bin").getRemote());
        }

        @TestExtension("envOverride") public static class DescriptorImpl extends BuildWrapperDescriptor {
            @Override public boolean isApplicable(AbstractProject<?, ?> item) {
                return true;
            }
        }
    }

    @Test
    void envOverrideExpand() throws Exception {
        assumeFalse(Functions.isWindows());
        FreeStyleProject p = r.createFreeStyleProject();
        p.getBuildWrappersList().add(new WrapperWithEnvOverrideExpand());
        SpecialEnvSlave slave = new SpecialEnvSlave(tmp, r.createComputerLauncher(null));
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
        @Override public void setUp(Context context, Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener, EnvVars initialEnvironment) throws IOException, InterruptedException {
            assertEquals("/opt/jdk/bin:/usr/bin:/bin", initialEnvironment.get("PATH"));
            assertEquals("/home/jenkins", initialEnvironment.get("HOME"));
            context.env("EXTRA", "${HOME}/extra");
            context.env("PATH+EXTRA", "${EXTRA}/bin");
        }

        @TestExtension("envOverrideExpand") public static class DescriptorImpl extends BuildWrapperDescriptor {
            @Override public boolean isApplicable(AbstractProject<?, ?> item) {
                return true;
            }
        }
    }

    private static class SpecialEnvSlave extends Slave {
        SpecialEnvSlave(File remoteFS, ComputerLauncher launcher) throws Descriptor.FormException, IOException {
            super("special", "SpecialEnvSlave", remoteFS.getAbsolutePath(), 1, Mode.NORMAL, "", launcher, RetentionStrategy.NOOP, Collections.emptyList());
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

    @Test
    void disposer() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        p.getBuildWrappersList().add(new WrapperWithDisposer());
        FreeStyleBuild b = r.buildAndAssertSuccess(p);
        r.assertLogContains("ran DisposerImpl #1", b);
        r.assertLogNotContains("ran DisposerImpl #2", b);
    }

    public static class WrapperWithDisposer extends SimpleBuildWrapper {
        @Override public void setUp(Context context, Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener, EnvVars initialEnvironment) throws IOException, InterruptedException {
            context.setDisposer(new DisposerImpl());
        }

        private static final class DisposerImpl extends Disposer {
            @Serial
            private static final long serialVersionUID = 1;
            private int tearDownCount = 0;

            @Override public void tearDown(Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener) throws IOException, InterruptedException {
                listener.getLogger().println("ran DisposerImpl #" + ++tearDownCount);
            }
        }

        @TestExtension({ "disposer", "failedJobWithInterruptedDisposer" }) public static class DescriptorImpl extends BuildWrapperDescriptor {
            @Override public boolean isApplicable(AbstractProject<?, ?> item) {
                return true;
            }
        }
    }

    @Test
    void disposerForPreCheckoutWrapper() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        p.getBuildWrappersList().add(new PreCheckoutWrapperWithDisposer());
        FreeStyleBuild b = r.buildAndAssertSuccess(p);
        r.assertLogContains("ran DisposerImpl #1", b);
        r.assertLogNotContains("ran DisposerImpl #2", b);
    }

    @Issue("JENKINS-43889")
    @Test
    void disposerForPreCheckoutWrapperWithScmError() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        p.setScm(new FailingSCM());
        p.getBuildWrappersList().add(new PreCheckoutWrapperWithDisposer());
        FreeStyleBuild b = r.buildAndAssertStatus(Result.FAILURE, p);
        r.assertLogContains("ran DisposerImpl #1", b);
        r.assertLogNotContains("ran DisposerImpl #2", b);
    }

    public static class PreCheckoutWrapperWithDisposer extends WrapperWithDisposer {
        @Override
        protected boolean runPreCheckout() {
            return true;
        }

        @TestExtension({ "disposerForPreCheckoutWrapper", "disposerForPreCheckoutWrapperWithScmError" }) public static class DescriptorImpl extends BuildWrapperDescriptor {
            @Override public boolean isApplicable(AbstractProject<?, ?> item) {
                return true;
            }
        }
    }

    public static class FailingSCM extends SCM {
        @Override
        public void checkout(Run<?, ?> build, Launcher launcher, FilePath workspace, TaskListener listener, File changelogFile, SCMRevisionState baseline) throws IOException, InterruptedException {
            throw new RuntimeException("SCM failed");
        }

        @Override
        public ChangeLogParser createChangeLogParser() {
            return null;
        }
    }

    @Test
    void failedJobWithInterruptedDisposer() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        p.getBuildersList().add(new FailureBuilder());
        p.getBuildWrappersList().add(new WrapperWithDisposer());
        p.getBuildWrappersList().add(new InterruptedDisposerWrapper());
        // build is ABORTED because of InterruptedException during tearDown (trumps the FAILURE result)
        FreeStyleBuild b = r.buildAndAssertStatus(Result.ABORTED, p);
        r.assertLogContains("tearDown InterruptedDisposerImpl", b);
        r.assertLogContains("ran DisposerImpl", b); // ran despite earlier InterruptedException
    }

    public static class InterruptedDisposerWrapper extends SimpleBuildWrapper {
        @Override public void setUp(Context context, Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener, EnvVars initialEnvironment) throws IOException, InterruptedException {
            context.setDisposer(new InterruptedDisposerImpl());
        }

        private static final class InterruptedDisposerImpl extends Disposer {
            @Serial
            private static final long serialVersionUID = 1;

            @Override public void tearDown(Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener) throws IOException, InterruptedException {
                listener.getLogger().println("tearDown InterruptedDisposerImpl");
                throw new InterruptedException("interrupted in InterruptedDisposerImpl");
            }
        }

        @TestExtension("failedJobWithInterruptedDisposer") public static class DescriptorImpl extends BuildWrapperDescriptor {
            @Override public boolean isApplicable(AbstractProject<?, ?> item) {
                return true;
            }
        }
    }

    @Issue("JENKINS-27392")
    @Test
    void loggerDecorator() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        p.getBuildWrappersList().add(new WrapperWithLogger());
        p.getBuildersList().add(new TestBuilder() {
            @Override public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {
                listener.getLogger().println("sending a message");
                return true;
            }
        });
        r.assertLogContains("SENDING A MESSAGE", r.buildAndAssertSuccess(p));
    }

    public static class WrapperWithLogger extends SimpleBuildWrapper {
        @Override public void setUp(Context context, Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener, EnvVars initialEnvironment) throws IOException, InterruptedException {}

        @Override public ConsoleLogFilter createLoggerDecorator(Run<?, ?> build) {
            return new UpcaseFilter();
        }

        private static class UpcaseFilter extends ConsoleLogFilter implements Serializable {
            @Serial
            private static final long serialVersionUID = 1;

            @SuppressWarnings("rawtypes") // inherited
            @Override public OutputStream decorateLogger(AbstractBuild _ignore, OutputStream logger) throws IOException, InterruptedException {
                return new LineTransformationOutputStream.Delegating(logger) {
                    @Override protected void eol(byte[] b, int len) throws IOException {
                        out.write(new String(b, 0, len, Charset.defaultCharset()).toUpperCase(Locale.ROOT).getBytes(Charset.defaultCharset()));
                    }
                };
            }
        }

        @TestExtension("loggerDecorator") public static class DescriptorImpl extends BuildWrapperDescriptor {
            @Override public boolean isApplicable(AbstractProject<?, ?> item) {
                return true;
            }
        }
    }

}
