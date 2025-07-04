/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc.
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

package hudson.cli;

import static hudson.cli.CLICommandInvoker.Matcher.failedWith;
import static hudson.cli.CLICommandInvoker.Matcher.succeeded;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.Extension;
import hudson.Functions;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.Executor;
import hudson.model.FileParameterDefinition;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Queue.QueueDecisionHandler;
import hudson.model.Queue.Task;
import hudson.model.SimpleParameterDefinition;
import hudson.model.StringParameterDefinition;
import hudson.model.StringParameterValue;
import hudson.model.TopLevelItem;
import hudson.slaves.DumbSlave;
import hudson.tasks.BatchFile;
import hudson.tasks.Shell;
import hudson.util.OneShotEvent;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.TimeUnit;
import net.sf.json.JSONObject;
import org.junit.experimental.categories.Category;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.CaptureEnvironmentBuilder;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.SmokeTest;
import org.jvnet.hudson.test.TestBuilder;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.StaplerRequest2;

/**
 * {@link BuildCommand} test.
 */
@WithJenkins
class BuildCommandTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    /**
     * Just schedules a build and return.
     */
    @Test
    void async() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        OneShotEvent started = new OneShotEvent();
        OneShotEvent completed = new OneShotEvent();
        p.getBuildersList().add(new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException {
                started.signal();
                completed.block();
                return true;
            }
        });

        assertThat(new CLICommandInvoker(j, new BuildCommand()).invokeWithArgs(p.getName()), CLICommandInvoker.Matcher.succeeded());
        started.block();
        assertTrue(p.getBuildByNumber(1).isBuilding());
        completed.signal();
        j.waitForCompletion(p.getBuildByNumber(1));
    }

    /**
     * Tests synchronous execution.
     */
    @Test
    @Category(SmokeTest.class)
    void sync() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.getBuildersList().add(Functions.isWindows() ? new BatchFile("ping 127.0.0.1") : new Shell("sleep 3"));

        assertThat(new CLICommandInvoker(j, new BuildCommand()).invokeWithArgs("-s", p.getName()), CLICommandInvoker.Matcher.succeeded());
        assertFalse(p.getBuildByNumber(1).isBuilding());
    }

    /**
     * Tests synchronous execution with retried verbose output
     */
    @Test
    void syncWOutputStreaming() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.getBuildersList().add(Functions.isWindows() ? new BatchFile("ping 127.0.0.1") : new Shell("sleep 3"));

        assertThat(new CLICommandInvoker(j, new BuildCommand()).invokeWithArgs("-s", "-v", "-r", "5", p.getName()), CLICommandInvoker.Matcher.succeeded());
        assertFalse(p.getBuildByNumber(1).isBuilding());
    }

    @Test
    void parameters() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition("key", null)));

        assertThat(new CLICommandInvoker(j, new BuildCommand()).invokeWithArgs("-s", "-p", "key=foobar", p.getName()), CLICommandInvoker.Matcher.succeeded());
        FreeStyleBuild b = j.assertBuildStatusSuccess(p.getBuildByNumber(1));
        assertEquals("foobar", b.getAction(ParametersAction.class).getParameter("key").getValue());
    }

    @Test
    void defaultParameters() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition("key", "default"), new StringParameterDefinition("key2", "default2")));

        assertThat(new CLICommandInvoker(j, new BuildCommand()).invokeWithArgs("-s", "-p", "key=foobar", p.getName()), CLICommandInvoker.Matcher.succeeded());
        FreeStyleBuild b = j.assertBuildStatusSuccess(p.getBuildByNumber(1));
        assertEquals("foobar", b.getAction(ParametersAction.class).getParameter("key").getValue());
        assertEquals("default2", b.getAction(ParametersAction.class).getParameter("key2").getValue());
    }

    // TODO randomly fails: Started test0 #1
    @Test
    void consoleOutput() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        CLICommandInvoker.Result r = new CLICommandInvoker(j, new BuildCommand()).invokeWithArgs("-s", "-v", p.getName());
        assertThat(r, CLICommandInvoker.Matcher.succeeded());
        j.assertBuildStatusSuccess(p.getBuildByNumber(1));
        assertThat(r.stdout(), allOf(containsString("Started from command line by anonymous"), containsString("Finished: SUCCESS")));
    }

    // TODO randomly fails: Started test0 #1
    @Test
    void consoleOutputWhenBuildSchedulingRefused() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        CLICommandInvoker.Result r = new CLICommandInvoker(j, new BuildCommand()).invokeWithArgs("-s", "-v", p.getName());
        assertThat(r, CLICommandInvoker.Matcher.failedWith(4));
        assertThat(r.stderr(), containsString(BuildCommand.BUILD_SCHEDULING_REFUSED));
    }
    // <=>

    @TestExtension("consoleOutputWhenBuildSchedulingRefused")
    public static class UnschedulingVetoer extends QueueDecisionHandler {
        @Override
        public boolean shouldSchedule(Task task, List<Action> actions) {
            return false;
        }
    }

    @Test
    void refuseToBuildDisabledProject() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject("the-project");
        project.disable();
        CLICommandInvoker invoker = new CLICommandInvoker(j, new BuildCommand());
        CLICommandInvoker.Result result = invoker.invokeWithArgs("the-project");

        assertThat(result, failedWith(4));
        assertThat(result.stderr(), containsString("ERROR: Cannot build the-project because it is disabled."));
        assertNull(project.getBuildByNumber(1), "Project should not be built");
    }

    @Test
    void refuseToBuildNewlyCopiedProject() throws Exception {
        FreeStyleProject original = j.createFreeStyleProject("original");
        FreeStyleProject newOne = (FreeStyleProject) j.jenkins.<TopLevelItem>copy(original, "new-one");
        CLICommandInvoker invoker = new CLICommandInvoker(j, new BuildCommand());
        CLICommandInvoker.Result result = invoker.invokeWithArgs("new-one");

        assertThat(result, failedWith(4));
        assertThat(result.stderr(), containsString("ERROR: Cannot build new-one because its configuration has not been saved."));
        assertNull(newOne.getBuildByNumber(1), "Project should not be built");
    }

    @Test
    void correctlyParseMapValuesContainingEqualsSign() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject("the-project");
        project.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition("expr", null)));

        CLICommandInvoker invoker = new CLICommandInvoker(j, new BuildCommand());
        CLICommandInvoker.Result result = invoker.invokeWithArgs("the-project", "-p", "expr=a=b", "-s");

        assertThat(result, succeeded());
        assertEquals("a=b", project.getBuildByNumber(1).getBuildVariables().get("expr"));
    }

    @Issue("JENKINS-15094")
    @Test
    void executorsAliveOnParameterWithNullDefaultValue() throws Exception {
        DumbSlave slave = j.createSlave();
        FreeStyleProject project = j.createFreeStyleProject("foo");
        project.setAssignedNode(slave);

        // Create test parameter with Null default value
        NullDefaultValueParameterDefinition nullDefaultDefinition = new NullDefaultValueParameterDefinition();
        ParametersDefinitionProperty pdp = new ParametersDefinitionProperty(
                new StringParameterDefinition("string", "defaultValue", "description"),
                nullDefaultDefinition);
        project.addProperty(pdp);
        CaptureEnvironmentBuilder builder = new CaptureEnvironmentBuilder();
        project.getBuildersList().add(builder);

        // Warmup
        j.buildAndAssertSuccess(project);

        await().pollInterval(250, TimeUnit.MILLISECONDS)
                .atMost(10, TimeUnit.SECONDS)
                .until(() -> slave.toComputer().getExecutors().stream().allMatch(Executor::isActive));

        // Create CLI & run command
        CLICommandInvoker invoker = new CLICommandInvoker(j, new BuildCommand());
        CLICommandInvoker.Result result = invoker.invokeWithArgs("foo", "-p", "string=value");
        assertThat(result, failedWith(2));
        assertThat(result.stderr(), containsString("ERROR: No default value for the parameter 'FOO'."));

        Thread.sleep(5000); // Give the job 5 seconds to be submitted
        assertNull(j.jenkins.getQueue().getItem(project), "Build should not be scheduled");
        assertNull(project.getBuildByNumber(2), "Build should not be scheduled");

        // Check executors health after a timeout
        await().pollInterval(250, TimeUnit.MILLISECONDS)
                .atMost(10, TimeUnit.SECONDS)
                .until(() -> slave.toComputer().getExecutors().stream().allMatch(Executor::isActive));
    }

    public static final class NullDefaultValueParameterDefinition extends SimpleParameterDefinition {

        /*package*/ NullDefaultValueParameterDefinition() {
            super("FOO", "Always null default value");
        }

        @Override
        public ParameterValue createValue(String value) {
            return new StringParameterValue("FOO", "BAR");
        }

        @Override
        public ParameterValue createValue(StaplerRequest2 req, JSONObject jo) {
            return createValue("BAR");
        }

        @Override
        public ParameterValue getDefaultParameterValue() {
            return null; // Equals to super.getDefaultParameterValue();
        }

        @Extension
        public static class DescriptorImpl extends ParameterDescriptor {}

    }

    @Issue("JENKINS-41745")
    @Test
    void fileParameter() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject("myjob");
        p.addProperty(new ParametersDefinitionProperty(new FileParameterDefinition("file", null)));
        p.getBuildersList().add(new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                listener.getLogger().println("Found in my workspace: " + build.getWorkspace().child("file").readToString());
                return true;
            }
        });
        assertThat(new CLICommandInvoker(j, "build").
                withStdin(new ByteArrayInputStream("uploaded content here".getBytes(Charset.defaultCharset()))).
                invokeWithArgs("-f", "-p", "file=", "myjob"),
            CLICommandInvoker.Matcher.succeeded());
        FreeStyleBuild b = p.getBuildByNumber(1);
        assertNotNull(b);
        j.assertLogContains("uploaded content here", b);
    }

}
