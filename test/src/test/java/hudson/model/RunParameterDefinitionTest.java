/*
 * The MIT License
 *
 * Copyright 2013 Jesse Glick, Geoff Cummings
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

package hudson.model;

import hudson.EnvVars;
import static org.junit.Assert.*;
import hudson.Launcher;
import hudson.model.RunParameterDefinition.RunParameterFilter;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.util.LogTaskListener;

import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockFolder;

public class RunParameterDefinitionTest {

    private static final Logger LOGGER = Logger.getLogger(Run.class.getName());

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Issue("JENKINS-31954")
    @Test public void configRoundtrip() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(new RunParameterDefinition("build", "p", "", RunParameterFilter.COMPLETED)));
        j.configRoundtrip((Item)p);
        RunParameterDefinition rpd = (RunParameterDefinition) p.getProperty(ParametersDefinitionProperty.class).getParameterDefinition("build");
        assertEquals("p", rpd.getProjectName());
        assertEquals(RunParameterFilter.COMPLETED, rpd.getFilter());
    }

    @Issue("JENKINS-16462")
    @Test public void inFolders() throws Exception {
        MockFolder dir = j.createFolder("dir");
        MockFolder subdir = dir.createProject(MockFolder.class, "sub dir");
        FreeStyleProject p = subdir.createProject(FreeStyleProject.class, "some project");
        p.scheduleBuild2(0).get();
        FreeStyleBuild build2 = p.scheduleBuild2(0).get();
        p.scheduleBuild2(0).get();
        String id = build2.getExternalizableId();
        assertEquals("dir/sub dir/some project#2", id);
        assertEquals(build2, Run.fromExternalizableId(id));
        RunParameterDefinition def = new RunParameterDefinition("build", "dir/sub dir/some project", "my build", null);
        assertEquals("dir/sub dir/some project", def.getProjectName());
        assertEquals(p, def.getProject());
        EnvVars env = new EnvVars();
        def.getDefaultParameterValue().buildEnvironment(null, env);
        assertEquals(j.jenkins.getRootUrl() + "job/dir/job/sub%20dir/job/some%20project/3/", env.get("build"));
        RunParameterValue val = def.createValue(id);
        assertEquals(build2, val.getRun());
        assertEquals("dir/sub dir/some project", val.getJobName());
        assertEquals("2", val.getNumber());
        val.buildEnvironment(null, env);
        assertEquals(j.jenkins.getRootUrl() + "job/dir/job/sub%20dir/job/some%20project/2/", env.get("build"));
        assertEquals("dir/sub dir/some project", env.get("build.jobName"));
        assertEquals("dir/sub dir/some project", env.get("build_JOBNAME"));
        assertEquals("2", env.get("build.number"));
        assertEquals("2", env.get("build_NUMBER"));
    }

    @Test
    public void testNULLFilter() throws Exception {

        FreeStyleProject project = j.createFreeStyleProject("project");
        FreeStyleBuild successfulBuild = project.scheduleBuild2(0).get();

        project.getPublishersList().replaceBy(Collections.singleton(new ResultPublisher(Result.UNSTABLE)));
        FreeStyleBuild unstableBuild = project.scheduleBuild2(0).get();

        project.getPublishersList().replaceBy(Collections.singleton(new ResultPublisher(Result.FAILURE)));
        FreeStyleBuild failedBuild = project.scheduleBuild2(0).get();

        project.getPublishersList().replaceBy(Collections.singleton(new ResultPublisher(Result.NOT_BUILT)));
        FreeStyleBuild notBuiltBuild = project.scheduleBuild2(0).get();
        
        project.getPublishersList().replaceBy(Collections.singleton(new ResultPublisher(Result.ABORTED)));
        FreeStyleBuild abortedBuild = project.scheduleBuild2(0).get();

        FreeStyleProject paramProject = j.createFreeStyleProject("paramProject");
        ParametersDefinitionProperty pdp = 
                new ParametersDefinitionProperty(new RunParameterDefinition("RUN", 
                                                                             project.getName(),
                                                                             "run description",
                                                                             null));
        paramProject.addProperty(pdp);

        FreeStyleBuild build = paramProject.scheduleBuild2(0).get();
        assertEquals(Integer.toString(project.getLastBuild().getNumber()),
                     build.getEnvironment(new LogTaskListener(LOGGER, Level.INFO)).get("RUN_NUMBER"));
    }

    
    @Test
    public void testALLFilter() throws Exception {

        FreeStyleProject project = j.createFreeStyleProject("project");
        FreeStyleBuild successfulBuild = project.scheduleBuild2(0).get();

        project.getPublishersList().replaceBy(Collections.singleton(new ResultPublisher(Result.UNSTABLE)));
        FreeStyleBuild unstableBuild = project.scheduleBuild2(0).get();

        project.getPublishersList().replaceBy(Collections.singleton(new ResultPublisher(Result.FAILURE)));
        FreeStyleBuild failedBuild = project.scheduleBuild2(0).get();

        project.getPublishersList().replaceBy(Collections.singleton(new ResultPublisher(Result.NOT_BUILT)));
        FreeStyleBuild notBuiltBuild = project.scheduleBuild2(0).get();
        
        project.getPublishersList().replaceBy(Collections.singleton(new ResultPublisher(Result.ABORTED)));
        FreeStyleBuild abortedBuild = project.scheduleBuild2(0).get();

        FreeStyleProject paramProject = j.createFreeStyleProject("paramProject");
        ParametersDefinitionProperty pdp = 
                new ParametersDefinitionProperty(new RunParameterDefinition("RUN", 
                                                                             project.getName(),
                                                                             "run description",
                                                                             RunParameterFilter.ALL));
        paramProject.addProperty(pdp);

        FreeStyleBuild build = paramProject.scheduleBuild2(0).get();
        assertEquals(Integer.toString(project.getLastBuild().getNumber()),
                     build.getEnvironment(new LogTaskListener(LOGGER, Level.INFO)).get("RUN_NUMBER"));
    }

    @Test
    public void testCOMPLETEDFilter() throws Exception {

        FreeStyleProject project = j.createFreeStyleProject("project");
        FreeStyleBuild successfulBuild = project.scheduleBuild2(0).get();

        project.getPublishersList().replaceBy(Collections.singleton(new ResultPublisher(Result.UNSTABLE)));
        FreeStyleBuild unstableBuild = project.scheduleBuild2(0).get();

        project.getPublishersList().replaceBy(Collections.singleton(new ResultPublisher(Result.FAILURE)));
        FreeStyleBuild failedBuild = project.scheduleBuild2(0).get();

        project.getPublishersList().replaceBy(Collections.singleton(new ResultPublisher(Result.NOT_BUILT)));
        FreeStyleBuild notBuiltBuild = project.scheduleBuild2(0).get();
        
        project.getPublishersList().replaceBy(Collections.singleton(new ResultPublisher(Result.ABORTED)));
        FreeStyleBuild abortedBuild = project.scheduleBuild2(0).get();

        FreeStyleProject paramProject = j.createFreeStyleProject("paramProject");
        ParametersDefinitionProperty pdp = 
                new ParametersDefinitionProperty(new RunParameterDefinition("RUN", 
                                                                             project.getName(),
                                                                             "run description",
                                                                             RunParameterFilter.COMPLETED));
        paramProject.addProperty(pdp);

        FreeStyleBuild build = paramProject.scheduleBuild2(0).get();
        assertEquals(Integer.toString(abortedBuild.getNumber()),
                     build.getEnvironment(new LogTaskListener(LOGGER, Level.INFO)).get("RUN_NUMBER"));
    }
    
    @Test
    public void testSUCCESSFULFilter() throws Exception {

        FreeStyleProject project = j.createFreeStyleProject("project");
        FreeStyleBuild successfulBuild = project.scheduleBuild2(0).get();

        project.getPublishersList().replaceBy(Collections.singleton(new ResultPublisher(Result.UNSTABLE)));
        FreeStyleBuild unstableBuild = project.scheduleBuild2(0).get();

        project.getPublishersList().replaceBy(Collections.singleton(new ResultPublisher(Result.FAILURE)));
        FreeStyleBuild failedBuild = project.scheduleBuild2(0).get();

        project.getPublishersList().replaceBy(Collections.singleton(new ResultPublisher(Result.NOT_BUILT)));
        FreeStyleBuild notBuiltBuild = project.scheduleBuild2(0).get();
        
        project.getPublishersList().replaceBy(Collections.singleton(new ResultPublisher(Result.ABORTED)));
        FreeStyleBuild abortedBuild = project.scheduleBuild2(0).get();

        FreeStyleProject paramProject = j.createFreeStyleProject("paramProject");
        ParametersDefinitionProperty pdp = 
                new ParametersDefinitionProperty(new RunParameterDefinition("RUN", 
                                                                             project.getName(),
                                                                             "run description",
                                                                             RunParameterFilter.SUCCESSFUL));
        paramProject.addProperty(pdp);

        FreeStyleBuild build = paramProject.scheduleBuild2(0).get();
        assertEquals(Integer.toString(unstableBuild.getNumber()),
                     build.getEnvironment(new LogTaskListener(LOGGER, Level.INFO)).get("RUN_NUMBER"));
    }
    
    
    @Test
    public void testSTABLEFilter() throws Exception {

        FreeStyleProject project = j.createFreeStyleProject("project");
        FreeStyleBuild successfulBuild = project.scheduleBuild2(0).get();

        project.getPublishersList().replaceBy(Collections.singleton(new ResultPublisher(Result.UNSTABLE)));
        FreeStyleBuild unstableBuild = project.scheduleBuild2(0).get();

        project.getPublishersList().replaceBy(Collections.singleton(new ResultPublisher(Result.FAILURE)));
        FreeStyleBuild failedBuild = project.scheduleBuild2(0).get();

        project.getPublishersList().replaceBy(Collections.singleton(new ResultPublisher(Result.NOT_BUILT)));
        FreeStyleBuild notBuiltBuild = project.scheduleBuild2(0).get();
        
        project.getPublishersList().replaceBy(Collections.singleton(new ResultPublisher(Result.ABORTED)));
        FreeStyleBuild abortedBuild = project.scheduleBuild2(0).get();

        FreeStyleProject paramProject = j.createFreeStyleProject("paramProject");
        ParametersDefinitionProperty pdp = 
                new ParametersDefinitionProperty(new RunParameterDefinition("RUN", 
                                                                             project.getName(),
                                                                             "run description",
                                                                             RunParameterFilter.STABLE));
        paramProject.addProperty(pdp);

        FreeStyleBuild build = paramProject.scheduleBuild2(0).get();
        assertEquals(Integer.toString(successfulBuild.getNumber()),
                     build.getEnvironment(new LogTaskListener(LOGGER, Level.INFO)).get("RUN_NUMBER"));
    }
    
    
    @Test
    public void testLoadEnvironmentVariablesWhenRunParameterJobHasBeenDeleted() throws Exception {

        FreeStyleProject project = j.createFreeStyleProject("project");
        FreeStyleBuild successfulBuild = project.scheduleBuild2(0).get();

        FreeStyleProject paramProject = j.createFreeStyleProject("paramProject");
        ParametersDefinitionProperty pdp =
                new ParametersDefinitionProperty(new RunParameterDefinition("RUN",
                                                                             project.getName(),
                                                                             "run description",
                                                                             RunParameterFilter.ALL));
        paramProject.addProperty(pdp);

        FreeStyleBuild build = paramProject.scheduleBuild2(0).get();
        assertEquals(Integer.toString(project.getLastBuild().getNumber()),
                     build.getEnvironment(new LogTaskListener(LOGGER, Level.INFO)).get("RUN_NUMBER"));

        successfulBuild.delete();
        // We should still be able to retrieve non RunParameter environment variables for the parameterized build
        // even when the selected RunParameter build has been deleted.
        assertEquals("paramProject", build.getEnvironment(new LogTaskListener(LOGGER, Level.INFO)).get("JOB_NAME"));
    }

    static class ResultPublisher extends Publisher {

        private final Result result;

        public ResultPublisher(Result result) {
            this.result = result;
        }

        public @Override
        boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {
            build.setResult(result);
            return true;
        }

        public BuildStepMonitor getRequiredMonitorService() {
            return BuildStepMonitor.NONE;
        }

        public Descriptor<Publisher> getDescriptor() {
            return new Descriptor<Publisher>(ResultPublisher.class) {};
        }
    }
}
