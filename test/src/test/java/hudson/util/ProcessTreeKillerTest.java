package hudson.util;

import static org.junit.Assert.*;
import static org.junit.Assume.*;
import java.io.File;
import hudson.Functions;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.tasks.Maven;
import hudson.tasks.Shell;
import hudson.util.ProcessTreeRemoting.IOSProcess;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.ExtractResourceSCM;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import com.google.common.collect.ImmutableMap;

public class ProcessTreeKillerTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();
    private Process process;
    
    @After
    public void tearDown() throws Exception {
        if (null != process)
            process.destroy();
    }    
    
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
    
    @Test
    @Issue("JENKINS-9104")
    public void considersKillingVetos() throws Exception {
        // on some platforms where we fail to list any processes, this test will
        // just not work
        assumeTrue(ProcessTree.get() != ProcessTree.DEFAULT);

        // kick off a process we (shouldn't) kill
        ProcessBuilder pb = new ProcessBuilder();
        pb.environment().put("cookie", "testKeepDaemonsAlive");

        if (File.pathSeparatorChar == ';') {
            pb.command("cmd");
        } else {
            pb.command("sleep", "5m");
        }

        process = pb.start();

        ProcessTree processTree = ProcessTree.get();
        processTree.killAll(ImmutableMap.of("cookie", "testKeepDaemonsAlive"));
        try {
            process.exitValue();
            fail("Process should have been excluded from the killing");
        } catch (IllegalThreadStateException e) {
            // Means the process is still running
        }
    }

    @TestExtension("considersKillingVetos")
    public static class VetoAllKilling extends ProcessKillingVeto {
        @Override
        public VetoCause vetoProcessKilling(IOSProcess p) {
            return new VetoCause("Peace on earth");
        }
    }
}
