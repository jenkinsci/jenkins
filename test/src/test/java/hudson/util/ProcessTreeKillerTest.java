package hudson.util;

import hudson.Functions;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.tasks.Maven;
import hudson.tasks.Shell;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.ExtractResourceSCM;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

public class ProcessTreeKillerTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
	public void manualAbortProcess() throws Exception {
		ProcessTree.enabled = true;
		FreeStyleProject project = j.createFreeStyleProject();

		// this contains a maven project with a single test that sleeps 5s.
		project.setScm(new ExtractResourceSCM(getClass().getResource(
				"ProcessTreeKiller-test-project.jar")));
		project.getBuildersList().add(new Maven("install", "maven"));

		// build the project, wait until tests are running, then cancel.
		project.scheduleBuild2(0).waitForStart();

        FreeStyleBuild b = project.getLastBuild();
        b.doStop();

		Thread.sleep(1000);

		// will fail (at least on windows) if test process is still running
		b.getWorkspace().deleteRecursive();
	}

    @Test
    @Issue("JENKINS-22641")
    public void processProperlyKilledUnix() throws Exception {
        ProcessTree.enabled = true;
        if (Functions.isWindows()) return; // This test does not involve windows.

        FreeStyleProject sleepProject = j.createFreeStyleProject();
        FreeStyleProject processJob = j.createFreeStyleProject();

        sleepProject.getBuildersList().add(new Shell("nohup sleep 100000 &"));

        j.assertBuildStatusSuccess(sleepProject.scheduleBuild2(0).get());

        processJob.getBuildersList().add(new Shell("ps -ef | grep sleep"));

        j.assertLogNotContains("sleep 100000", processJob.scheduleBuild2(0).get());
    }
}
