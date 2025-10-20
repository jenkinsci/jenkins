/*
 * The MIT License
 *
 * Copyright 2013 Oleg Nenashev <nenashev@synopsys.com>, Synopsys Inc.
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

package hudson;

import hudson.Launcher.DecoratedLauncher;
import hudson.Launcher.ProcStarter;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.model.Run;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.tasks.Builder;
import java.io.File;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Contains tests for {@link ProcStarter} class.
 * @author Oleg Nenashev, Synopsys Inc.
 * @since 1.568
 */
@WithJenkins
class ProcStarterTest {

    private JenkinsRule rule;

    @BeforeEach
    void setUp(JenkinsRule j) {
        rule = j;
    }

    @Test
    @Issue("JENKINS-20559")
    void testNonInitializedEnvsNPE() throws Exception {
        // Create nodes and other test stuff
        rule.jenkins.setNumExecutors(0);
        rule.createSlave();

        // Create a job with test build wrappers
        FreeStyleProject project = rule.createFreeStyleProject();
        project.getBuildWrappersList().add(new DecoratedWrapper());
        project.getBuildWrappersList().add(new EchoWrapper());

        // Run the build. If NPE occurs, the test will fail
        rule.buildAndAssertSuccess(project);
    }

    @Test
    @Issue("JENKINS-36277")
    void testNonExistingPwd() throws Exception {
        rule.jenkins.setNumExecutors(0);
        rule.createSlave();

        FreeStyleProject project = rule.createFreeStyleProject();
        project.getBuildersList().add(new EchoBuilder());
        FreeStyleBuild run = rule.buildAndAssertStatus(Result.FAILURE, project);

        rule.assertLogContains("java.io.IOException: Process working directory", run);
    }

    /**
     * A stub descriptor for {@link BuildWrapper}s.
     */
    public abstract static class TestWrapperDescriptor extends BuildWrapperDescriptor {

        @Override
        public boolean isApplicable(AbstractProject<?, ?> ap) {
            return true;
        }
    }

    /**
     * A wrapper, which contains a nested launch.
     */
    public static class EchoWrapper extends BuildWrapper {

        @Override
        public Environment setUp(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
            String[] cmds = Functions.isWindows() ? new String[] { "cmd.exe", "/C", "echo", "Hello" } : new String[] { "echo", "Hello" };
            Launcher.ProcStarter starter = launcher.launch().cmds(cmds);
            starter.start();
            starter.join();
            return new Environment() {
            };
        }

        @Extension
        public static class DescriptorImpl extends TestWrapperDescriptor {
        }
    }

    /**
     * A wrapper, which decorates launchers.
     */
    public static class DecoratedWrapper extends BuildWrapper {

        @Override
        public Launcher decorateLauncher(AbstractBuild build, Launcher launcher, BuildListener listener) throws Run.RunnerAbortedException {
            final BuildListener l = listener;
            return new DecoratedLauncher(launcher) {
                @Override
                public Proc launch(Launcher.ProcStarter starter) throws IOException {
                    String[] envs = starter.envs(); // Finally, call envs()
                    l.getLogger().println("[DecoratedWrapper]: Number of environment variables is " + envs.length); // Fail on null
                    return super.launch(starter);
                }
            };
        }

        @Override
        public Environment setUp(AbstractBuild build, Launcher launcher, BuildListener listener) {
            return new Environment() {
            };
        }

        @Extension
        public static class DescriptorImpl extends TestWrapperDescriptor {
        }
    }

    public static class EchoBuilder extends Builder {

        @Override
        public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
            String[] cmds = Functions.isWindows() ? new String[] { "cmd.exe", "/C", "echo", "Hello" } : new String[] { "echo", "Hello" };
            String path = Functions.isWindows() ? "C:\\this\\path\\doesn't\\exist" : "/this/path/doesnt/exist";
            Launcher.ProcStarter starter = launcher.launch().cmds(cmds).pwd(new File(path));
            starter.start();
            starter.join();
            return true;
        }

        @Extension
        public static class DescriptorImpl extends BuildStepDescriptor<Builder> {
            @Override
            public boolean isApplicable(Class<? extends AbstractProject> aClass) {
                return true;
            }
        }
    }
}
