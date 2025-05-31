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

import static org.junit.jupiter.api.Assertions.assertEquals;

import hudson.EnvVars;
import hudson.Launcher;
import hudson.model.RunParameterDefinition.RunParameterFilter;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.util.LogTaskListener;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockFolder;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class RunParameterDefinitionTest {

    private static final Logger LOGGER = Logger.getLogger(Run.class.getName());

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Issue("JENKINS-31954")
    @Test
    void configRoundtrip() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(new RunParameterDefinition("build", "p", "", RunParameterFilter.COMPLETED)));
        j.configRoundtrip(p);
        RunParameterDefinition rpd = (RunParameterDefinition) p.getProperty(ParametersDefinitionProperty.class).getParameterDefinition("build");
        assertEquals("p", rpd.getProjectName());
        assertEquals(RunParameterFilter.COMPLETED, rpd.getFilter());
    }

    @Issue("JENKINS-16462")
    @Test
    void inFolders() throws Exception {
        MockFolder dir = j.createFolder("dir");
        MockFolder subdir = dir.createProject(MockFolder.class, "sub dir");
        FreeStyleProject p = subdir.createProject(FreeStyleProject.class, "some project");
        j.buildAndAssertSuccess(p);
        FreeStyleBuild build2 = j.buildAndAssertSuccess(p);
        j.buildAndAssertSuccess(p);
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
    void testNULLFilter() throws Exception {

        FreeStyleProject project = j.createFreeStyleProject("project");
        FreeStyleBuild successfulBuild = j.buildAndAssertSuccess(project);

        project.getPublishersList().replaceBy(Set.of(new ResultPublisher(Result.UNSTABLE)));
        FreeStyleBuild unstableBuild = j.buildAndAssertStatus(Result.UNSTABLE, project);

        project.getPublishersList().replaceBy(Set.of(new ResultPublisher(Result.FAILURE)));
        FreeStyleBuild failedBuild = j.buildAndAssertStatus(Result.FAILURE, project);

        project.getPublishersList().replaceBy(Set.of(new ResultPublisher(Result.NOT_BUILT)));
        FreeStyleBuild notBuiltBuild = j.buildAndAssertStatus(Result.NOT_BUILT, project);

        project.getPublishersList().replaceBy(Set.of(new ResultPublisher(Result.ABORTED)));
        FreeStyleBuild abortedBuild = j.buildAndAssertStatus(Result.ABORTED, project);

        FreeStyleProject paramProject = j.createFreeStyleProject("paramProject");
        ParametersDefinitionProperty pdp =
                new ParametersDefinitionProperty(new RunParameterDefinition("RUN",
                                                                             project.getName(),
                                                                             "run description",
                                                                             null));
        paramProject.addProperty(pdp);

        FreeStyleBuild build = j.buildAndAssertSuccess(paramProject);
        assertEquals(Integer.toString(project.getLastBuild().getNumber()),
                     build.getEnvironment(new LogTaskListener(LOGGER, Level.INFO)).get("RUN_NUMBER"));
    }


    @Test
    void testALLFilter() throws Exception {

        FreeStyleProject project = j.createFreeStyleProject("project");
        FreeStyleBuild successfulBuild = j.buildAndAssertSuccess(project);

        project.getPublishersList().replaceBy(Set.of(new ResultPublisher(Result.UNSTABLE)));
        FreeStyleBuild unstableBuild = j.buildAndAssertStatus(Result.UNSTABLE, project);

        project.getPublishersList().replaceBy(Set.of(new ResultPublisher(Result.FAILURE)));
        FreeStyleBuild failedBuild = j.buildAndAssertStatus(Result.FAILURE, project);

        project.getPublishersList().replaceBy(Set.of(new ResultPublisher(Result.NOT_BUILT)));
        FreeStyleBuild notBuiltBuild = j.buildAndAssertStatus(Result.NOT_BUILT, project);

        project.getPublishersList().replaceBy(Set.of(new ResultPublisher(Result.ABORTED)));
        FreeStyleBuild abortedBuild = j.buildAndAssertStatus(Result.ABORTED, project);

        FreeStyleProject paramProject = j.createFreeStyleProject("paramProject");
        ParametersDefinitionProperty pdp =
                new ParametersDefinitionProperty(new RunParameterDefinition("RUN",
                                                                             project.getName(),
                                                                             "run description",
                                                                             RunParameterFilter.ALL));
        paramProject.addProperty(pdp);

        FreeStyleBuild build = j.buildAndAssertSuccess(paramProject);
        assertEquals(Integer.toString(project.getLastBuild().getNumber()),
                     build.getEnvironment(new LogTaskListener(LOGGER, Level.INFO)).get("RUN_NUMBER"));
    }

    @Test
    void testCOMPLETEDFilter() throws Exception {

        FreeStyleProject project = j.createFreeStyleProject("project");
        FreeStyleBuild successfulBuild = j.buildAndAssertSuccess(project);

        project.getPublishersList().replaceBy(Set.of(new ResultPublisher(Result.UNSTABLE)));
        FreeStyleBuild unstableBuild = j.buildAndAssertStatus(Result.UNSTABLE, project);

        project.getPublishersList().replaceBy(Set.of(new ResultPublisher(Result.FAILURE)));
        FreeStyleBuild failedBuild = j.buildAndAssertStatus(Result.FAILURE, project);

        project.getPublishersList().replaceBy(Set.of(new ResultPublisher(Result.NOT_BUILT)));
        FreeStyleBuild notBuiltBuild = j.buildAndAssertStatus(Result.NOT_BUILT, project);

        project.getPublishersList().replaceBy(Set.of(new ResultPublisher(Result.ABORTED)));
        FreeStyleBuild abortedBuild = j.buildAndAssertStatus(Result.ABORTED, project);

        FreeStyleProject paramProject = j.createFreeStyleProject("paramProject");
        ParametersDefinitionProperty pdp =
                new ParametersDefinitionProperty(new RunParameterDefinition("RUN",
                                                                             project.getName(),
                                                                             "run description",
                                                                             RunParameterFilter.COMPLETED));
        paramProject.addProperty(pdp);

        FreeStyleBuild build = j.buildAndAssertSuccess(paramProject);
        assertEquals(Integer.toString(abortedBuild.getNumber()),
                     build.getEnvironment(new LogTaskListener(LOGGER, Level.INFO)).get("RUN_NUMBER"));
    }

    @Test
    void testSUCCESSFULFilter() throws Exception {

        FreeStyleProject project = j.createFreeStyleProject("project");
        FreeStyleBuild successfulBuild = j.buildAndAssertSuccess(project);

        project.getPublishersList().replaceBy(Set.of(new ResultPublisher(Result.UNSTABLE)));
        FreeStyleBuild unstableBuild = j.buildAndAssertStatus(Result.UNSTABLE, project);

        project.getPublishersList().replaceBy(Set.of(new ResultPublisher(Result.FAILURE)));
        FreeStyleBuild failedBuild = j.buildAndAssertStatus(Result.FAILURE, project);

        project.getPublishersList().replaceBy(Set.of(new ResultPublisher(Result.NOT_BUILT)));
        FreeStyleBuild notBuiltBuild = j.buildAndAssertStatus(Result.NOT_BUILT, project);

        project.getPublishersList().replaceBy(Set.of(new ResultPublisher(Result.ABORTED)));
        FreeStyleBuild abortedBuild = j.buildAndAssertStatus(Result.ABORTED, project);

        FreeStyleProject paramProject = j.createFreeStyleProject("paramProject");
        ParametersDefinitionProperty pdp =
                new ParametersDefinitionProperty(new RunParameterDefinition("RUN",
                                                                             project.getName(),
                                                                             "run description",
                                                                             RunParameterFilter.SUCCESSFUL));
        paramProject.addProperty(pdp);

        FreeStyleBuild build = j.buildAndAssertSuccess(paramProject);
        assertEquals(Integer.toString(unstableBuild.getNumber()),
                     build.getEnvironment(new LogTaskListener(LOGGER, Level.INFO)).get("RUN_NUMBER"));
    }


    @Test
    void testSTABLEFilter() throws Exception {

        FreeStyleProject project = j.createFreeStyleProject("project");
        FreeStyleBuild successfulBuild = j.buildAndAssertSuccess(project);

        project.getPublishersList().replaceBy(Set.of(new ResultPublisher(Result.UNSTABLE)));
        FreeStyleBuild unstableBuild = j.buildAndAssertStatus(Result.UNSTABLE, project);

        project.getPublishersList().replaceBy(Set.of(new ResultPublisher(Result.FAILURE)));
        FreeStyleBuild failedBuild = j.buildAndAssertStatus(Result.FAILURE, project);

        project.getPublishersList().replaceBy(Set.of(new ResultPublisher(Result.NOT_BUILT)));
        FreeStyleBuild notBuiltBuild = j.buildAndAssertStatus(Result.NOT_BUILT, project);

        project.getPublishersList().replaceBy(Set.of(new ResultPublisher(Result.ABORTED)));
        FreeStyleBuild abortedBuild = j.buildAndAssertStatus(Result.ABORTED, project);

        FreeStyleProject paramProject = j.createFreeStyleProject("paramProject");
        ParametersDefinitionProperty pdp =
                new ParametersDefinitionProperty(new RunParameterDefinition("RUN",
                                                                             project.getName(),
                                                                             "run description",
                                                                             RunParameterFilter.STABLE));
        paramProject.addProperty(pdp);

        FreeStyleBuild build = j.buildAndAssertSuccess(paramProject);
        assertEquals(Integer.toString(successfulBuild.getNumber()),
                     build.getEnvironment(new LogTaskListener(LOGGER, Level.INFO)).get("RUN_NUMBER"));
    }


    @Test
    void testLoadEnvironmentVariablesWhenRunParameterJobHasBeenDeleted() throws Exception {

        FreeStyleProject project = j.createFreeStyleProject("project");
        FreeStyleBuild successfulBuild = j.buildAndAssertSuccess(project);

        FreeStyleProject paramProject = j.createFreeStyleProject("paramProject");
        ParametersDefinitionProperty pdp =
                new ParametersDefinitionProperty(new RunParameterDefinition("RUN",
                                                                             project.getName(),
                                                                             "run description",
                                                                             RunParameterFilter.ALL));
        paramProject.addProperty(pdp);

        FreeStyleBuild build = j.buildAndAssertSuccess(paramProject);
        assertEquals(Integer.toString(project.getLastBuild().getNumber()),
                     build.getEnvironment(new LogTaskListener(LOGGER, Level.INFO)).get("RUN_NUMBER"));

        successfulBuild.delete();
        // We should still be able to retrieve non RunParameter environment variables for the parameterized build
        // even when the selected RunParameter build has been deleted.
        assertEquals("paramProject", build.getEnvironment(new LogTaskListener(LOGGER, Level.INFO)).get("JOB_NAME"));
    }

    static class ResultPublisher extends Publisher {

        private final Result result;

        ResultPublisher(Result result) {
            this.result = result;
        }

        @Override
        public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {
            build.setResult(result);
            return true;
        }

        @Override
        public BuildStepMonitor getRequiredMonitorService() {
            return BuildStepMonitor.NONE;
        }

        @Override
        public Descriptor<Publisher> getDescriptor() {
            return new Descriptor<>(ResultPublisher.class) {};
        }
    }
}
