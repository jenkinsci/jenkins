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

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.FreeStyleProject;
import hudson.model.Run;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import java.io.IOException;
import org.jvnet.hudson.test.Issue;
import hudson.Launcher.DecoratedLauncher;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * Contains tests for {@link ProcStarter} class.
 * @author Oleg Nenashev <nenashev@synopsys.com>, Synopsys Inc.
 * @since 1.568
 */
public class ProcStarterTest {

    @Rule
    public JenkinsRule rule = new JenkinsRule();

    @Test
    @Issue("JENKINS-20559")
    public void testNonInitializedEnvsNPE() throws Exception {
        // Create nodes and other test stuff
        rule.hudson.setNumExecutors(0);
        rule.createSlave();

        // Create a job with test build wrappers
        FreeStyleProject project = rule.createFreeStyleProject();
        project.getBuildWrappersList().add(new DecoratedWrapper());
        project.getBuildWrappersList().add(new EchoWrapper());

        // Run the build. If NPE occurs, the test will fail
        rule.buildAndAssertSuccess(project);
    }

    /**
     * A stub descriptor for {@link BuildWrapper}s.
     */
    public abstract static class TestWrapperDescriptor extends BuildWrapperDescriptor {

        @Override
        public boolean isApplicable(AbstractProject<?, ?> ap) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "testStub";
        }
    }

    /**
     * A wrapper, which contains a nested launch.
     */
    public static class EchoWrapper extends BuildWrapper {

        @Override
        public Environment setUp(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
            Launcher.ProcStarter starter = launcher.launch().cmds("echo", "Hello");
            starter.start();
            starter.join();
            return new Environment() {
            };
        }

        @Extension
        public static class DescriptorImpl extends TestWrapperDescriptor {
        }
    };

    /**
     * A wrapper, which decorates launchers.
     */
    public static class DecoratedWrapper extends BuildWrapper {

        @Override
        public Launcher decorateLauncher(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException, Run.RunnerAbortedException {
            final BuildListener l = listener;
            return new DecoratedLauncher(launcher) {
                @Override
                public Proc launch(Launcher.ProcStarter starter) throws IOException {
                    String[] envs = starter.envs(); // Finally, call envs()
                    l.getLogger().println("[DecoratedWrapper]: Number of environment variables is "+envs.length); // Fail on null
                    return super.launch(starter);
                }
            };
        }

        @Override
        public Environment setUp(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
            return new Environment() {
            };
        }

        @Extension
        public static class DescriptorImpl extends TestWrapperDescriptor {
        }
    };
}
