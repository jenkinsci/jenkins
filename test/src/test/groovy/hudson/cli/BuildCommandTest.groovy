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
package hudson.cli

import org.apache.commons.io.output.TeeOutputStream

import static org.hamcrest.Matchers.*;
import static hudson.cli.CLICommandInvoker.Matcher.*;
import static org.junit.Assert.*
import hudson.Extension

import org.junit.Rule
import org.junit.Test
import org.jvnet.hudson.test.Issue
import org.jvnet.hudson.test.CaptureEnvironmentBuilder
import org.jvnet.hudson.test.JenkinsRule
import org.jvnet.hudson.test.RandomlyFails
import org.jvnet.hudson.test.TestBuilder
import org.jvnet.hudson.test.TestExtension
import org.kohsuke.stapler.StaplerRequest

import hudson.Launcher
import hudson.cli.CLICommandInvoker;
import hudson.model.AbstractBuild
import hudson.model.Action
import hudson.model.BuildListener
import hudson.model.Executor
import hudson.model.FreeStyleProject;
import hudson.model.ParameterDefinition.ParameterDescriptor
import hudson.model.ParameterValue
import hudson.model.ParametersAction
import hudson.model.ParametersDefinitionProperty
import hudson.model.Queue.QueueDecisionHandler
import hudson.model.Queue.Task;
import hudson.model.SimpleParameterDefinition
import hudson.model.StringParameterDefinition
import hudson.model.StringParameterValue
import hudson.model.labels.LabelAtom
import hudson.tasks.Shell
import hudson.util.OneShotEvent

import java.util.concurrent.Executor

import net.sf.json.JSONObject

/**
 * {@link BuildCommand} test.
 *
 * @author Kohsuke Kawaguchi
 */
public class BuildCommandTest {

    @Rule public JenkinsRule j = new JenkinsRule();

    /**
     * Just schedules a build and return.
     */
    @Test void async() {
        def p = j.createFreeStyleProject();
        def started = new OneShotEvent();
        def completed = new OneShotEvent();
        p.buildersList.add([perform: {AbstractBuild build, Launcher launcher, BuildListener listener ->
            started.signal();
            completed.block();
            return true;
        }] as TestBuilder);

        // this should be asynchronous
        def cli = new CLI(j.URL)
        try {
            assertEquals(0,cli.execute(["build", p.name]))
            started.block()
            assertTrue(p.getBuildByNumber(1).isBuilding())
            completed.signal()
        } finally {
            cli.close();
        }
    }

    /**
     * Tests synchronous execution.
     */
    @Test void sync() {
        def p = j.createFreeStyleProject();
        p.buildersList.add(new Shell("sleep 3"));

        def cli = new CLI(j.URL)
        try {
            cli.execute(["build","-s",p.name])
            assertFalse(p.getBuildByNumber(1).isBuilding())
        } finally {
            cli.close();
        }
    }

    /**
     * Tests synchronous execution with retried verbose output
     */
    @Test void syncWOutputStreaming() {
        def p = j.createFreeStyleProject();
        p.buildersList.add(new Shell("sleep 3"));

        def cli =new CLI(j.URL)
        try {
            cli.execute(["build","-s","-v","-r","5",p.name])
            assertFalse(p.getBuildByNumber(1).isBuilding())
        } finally {
            cli.close();
        }
    }

    @Test void parameters() {
        def p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty([new StringParameterDefinition("key",null)]));

        def cli = new CLI(j.URL)
        try {
            cli.execute(["build","-s","-p","key=foobar",p.name])
            def b = j.assertBuildStatusSuccess(p.getBuildByNumber(1))
            assertEquals("foobar",b.getAction(ParametersAction.class).getParameter("key").value)
        } finally {
            cli.close();
        }
    }

    @Test void defaultParameters() {
        def p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty([new StringParameterDefinition("key","default"), new StringParameterDefinition("key2","default2") ]));

        def cli = new CLI(j.URL)
        try {
            cli.execute(["build","-s","-p","key=foobar",p.name])
            def b = j.assertBuildStatusSuccess(p.getBuildByNumber(1))
            assertEquals("foobar",b.getAction(ParametersAction.class).getParameter("key").value)
            assertEquals("default2",b.getAction(ParametersAction.class).getParameter("key2").value)
        } finally {
            cli.close();
        }
    }

    @RandomlyFails("Started test0 #1")
    @Test void consoleOutput() {
        def p = j.createFreeStyleProject()
        def cli = new CLI(j.URL)
        try {
            def o = new ByteArrayOutputStream()
            cli.execute(["build","-s","-v",p.name],System.in,new TeeOutputStream(System.out,o),System.err)
            j.assertBuildStatusSuccess(p.getBuildByNumber(1))
            assertTrue(o.toString(), o.toString().contains("Started from command line by anonymous"))
            assertTrue(o.toString().contains("Finished: SUCCESS"))
        } finally {
            cli.close()
        }
    }

    @RandomlyFails("Started test0 #1")
    @Test void consoleOutputWhenBuildSchedulingRefused() {
        def p = j.createFreeStyleProject()
        def cli = new CLI(j.URL)
        try {
            def o = new ByteArrayOutputStream()
            cli.execute(["build","-s","-v",p.name],System.in,System.out,new TeeOutputStream(System.err,o))
            assertTrue(o.toString(), o.toString().contains(BuildCommand.BUILD_SCHEDULING_REFUSED))
        } finally {
            cli.close()
        }
    }
    // <=>
    @TestExtension("consoleOutputWhenBuildSchedulingRefused")
    static class UnschedulingVetoer extends QueueDecisionHandler {
        public boolean shouldSchedule(Task task, List<Action> actions) {
            return false;
        }
    }

    @Test void refuseToBuildDisabledProject() {

        def project = j.createFreeStyleProject("the-project");
        project.disable();
        def invoker = new CLICommandInvoker(j, new BuildCommand());
        def result = invoker.invokeWithArgs("the-project");

        assertThat(result, failedWith(-1));
        assertThat(result.stderr(), containsString("Cannot build the-project because it is disabled."));
        assertNull("Project should not be built", project.getBuildByNumber(1));
    }

    @Test void refuseToBuildNewlyCoppiedProject() {

        def original = j.createFreeStyleProject("original");
        def newOne = (FreeStyleProject) j.jenkins.copy(original, "new-one");
        def invoker = new CLICommandInvoker(j, new BuildCommand());
        def result = invoker.invokeWithArgs("new-one");

        assertThat(result, failedWith(-1));
        assertThat(result.stderr(), containsString("Cannot build new-one because its configuration has not been saved."));
        assertNull("Project should not be built", newOne.getBuildByNumber(1));
    }

    @Test void correctlyParseMapValuesContainingEqualsSign() {

        def project = j.createFreeStyleProject("the-project");
        project.addProperty(new ParametersDefinitionProperty([
            new StringParameterDefinition("expr", null)
        ]));

        def invoker = new CLICommandInvoker(j, new BuildCommand());
        def result = invoker.invokeWithArgs("the-project", "-p", "expr=a=b", "-s");

        assertThat(result, succeeded());
        assertEquals("a=b", project.getBuildByNumber(1).getBuildVariables().get("expr"));
    }
    
    @Issue("JENKINS-15094")
    @Test public void executorsAliveOnParameterWithNullDefaultValue() throws Exception {    
        def slave = j.createSlave();
        FreeStyleProject project = j.createFreeStyleProject("foo");
        project.setAssignedNode(slave);
        
        // Create test parameter with Null default value 
        def nullDefaultDefinition = new NullDefaultValueParameterDefinition();
        ParametersDefinitionProperty pdp = new ParametersDefinitionProperty(
                new StringParameterDefinition("string", "defaultValue", "description"),
                nullDefaultDefinition);
        project.addProperty(pdp);
        CaptureEnvironmentBuilder builder = new CaptureEnvironmentBuilder();
        project.getBuildersList().add(builder);
     
        // Warmup
        j.buildAndAssertSuccess(project);
        
        for (def exec : slave.toComputer().getExecutors()) {
            assertTrue("Executor has died before the test start: "+exec, exec.isActive());
        }
        
        // Create CLI & run command
        def invoker = new CLICommandInvoker(j, new BuildCommand());
        def result = invoker.invokeWithArgs("foo","-p","string=value");
        assertThat(result, failedWith(-1));
        assertThat(result.stderr(), containsString("No default value for the parameter \'FOO\'."));
        
        Thread.sleep(5000); // Give the job 5 seconds to be submitted
        assertNull("Build should not be scheduled", j.jenkins.getQueue().getItem(project));
        assertNull("Build should not be scheduled", project.getBuildByNumber(2));
        
        // Check executors health after a timeout
        for (def exec : slave.toComputer().getExecutors()) {
            assertTrue("Executor is dead: "+exec, exec.isActive());
        }
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
        public ParameterValue createValue(StaplerRequest req, JSONObject jo) {
            return createValue("BAR");
        }
        
        @Override
        public ParameterValue getDefaultParameterValue() {
            return null; // Equals to super.getDefaultParameterValue();
        }
        
        @Extension
        public static class DescriptorImpl extends ParameterDescriptor {

            @Override
            public String getDisplayName() {
                return "Parameter with the default NULL value"; 
            }   
        }
    }
}
