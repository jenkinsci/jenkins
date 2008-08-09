package org.jvnet.hudson.main;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.tasks.Shell;
import org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

/**
 * Experimenting with Hudson test suite.
 */
public class AppTest extends HudsonTestCase
{
    public AppTest(String name) {
        super(name);
    }

    public void test1() throws Exception {
        meat();
    }

    public void test2() throws Exception {
        meat();
    }

    private void meat() throws IOException, InterruptedException, ExecutionException {
        FreeStyleProject project = (FreeStyleProject)hudson.createProject(FreeStyleProject.DESCRIPTOR, "test" );
        project.getBuildersList().add(new Shell("echo hello"));

        FreeStyleBuild build = project.scheduleBuild2(0).get();
        System.out.println(build.getDisplayName()+" completed");

        String s = FileUtils.readFileToString(build.getLogFile());
        assertTrue(s.contains("+ echo hello"));
    }
}
