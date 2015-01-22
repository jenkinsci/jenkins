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
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildWrapperDescriptor;
import java.io.File;
import java.io.IOException;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.jvnet.hudson.test.CaptureEnvironmentBuilder;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;

public class SimpleBuildWrapperTest {

    @Rule public JenkinsRule r = new JenkinsRule();

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

}
