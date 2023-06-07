package hudson.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.EnvVars;
import hudson.Functions;
import hudson.Launcher;
import hudson.Proc;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Slave;
import hudson.tasks.Maven;
import hudson.tasks.Shell;
import hudson.util.ProcessTreeRemoting.IOSProcess;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;
import org.junit.After;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.ExtractResourceSCM;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestBuilder;
import org.jvnet.hudson.test.TestExtension;

public class ProcessTreeTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();
    private Process process;

    @After
    public void tearDown() {
        ProcessTree.vetoersExist = null;
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
        FreeStyleBuild b = project.scheduleBuild2(0).waitForStart();

        b.doStop();

        j.waitForCompletion(b);

        // will fail (at least on windows) if test process is still running
        b.getWorkspace().deleteRecursive();
    }

    @Test
    public void killNullProcess() throws Exception {
        ProcessTree.enabled = true;
        ProcessTree.get().killAll(null, null);
    }

    @Test
    @Issue("JENKINS-22641")
    public void processProperlyKilledUnix() throws Exception {
        ProcessTree.enabled = true;
        Assume.assumeFalse("This test does not involve windows", Functions.isWindows());

        FreeStyleProject sleepProject = j.createFreeStyleProject();
        FreeStyleProject processJob = j.createFreeStyleProject();

        sleepProject.getBuildersList().add(new Shell("nohup sleep 100000 &"));

        j.buildAndAssertSuccess(sleepProject);

        processJob.getBuildersList().add(new Shell("ps -ef | grep sleep"));

        j.assertLogNotContains("sleep 100000", processJob.scheduleBuild2(0).get());
    }

    @Test
    public void doNotKillProcessWithCookie() throws Exception {
        ProcessTree.enabled = true;
        SpawnBuilder spawner = new SpawnBuilder();

        FreeStyleProject p = j.createFreeStyleProject();
        p.getBuildersList().add(spawner);
        try {
            j.buildAndAssertSuccess(p);

            assertTrue("Process should be running", spawner.proc.isAlive());
        } finally {
            spawner.proc.kill();
            assertFalse("Process should be dead", spawner.proc.isAlive());
        }
    }

    public static final class SpawnBuilder extends TestBuilder {
        private Proc proc;

        @Override
        public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
            EnvVars envvars = build.getEnvironment(listener);
            envvars.addLine("BUILD_ID=dontKillMe");
            final String[] cmd = Functions.isWindows()
                    ? new String[]{"ping", "-n", "100000", "localhost"}
                    : new String[]{"nohup", "sleep", "100000"};
            proc = launcher.launch().envs(envvars).cmds(cmd).start();
            return true;
        }
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
            pb.command("sleep", "300");
        }

        process = pb.start();

        ProcessTree processTree = ProcessTree.get();
        processTree.killAll(Map.of("cookie", "testKeepDaemonsAlive"));
        assertThrows("Process should have been excluded from the killing", IllegalThreadStateException.class, () -> process.exitValue());
    }

    @Test
    @Issue("JENKINS-9104")
    public void considersKillingVetosOnSlave() throws Exception {
        // on some platforms where we fail to list any processes, this test will
        // just not work
        assumeTrue(ProcessTree.get() != ProcessTree.DEFAULT);

        // Define a process we (shouldn't) kill
        ProcessBuilder pb = new ProcessBuilder();
        pb.environment().put("cookie", "testKeepDaemonsAlive");

        if (File.pathSeparatorChar == ';') {
            pb.command("cmd");
        } else {
            pb.command("sleep", "300");
        }

        // Create an agent so we can tell it to kill the process
        Slave s = j.createSlave();
        s.toComputer().connect(false).get();

        // Start the process
        process = pb.start();

        // Call killall (somewhat roundabout though) to (not) kill it
        StringWriter out = new StringWriter();
        s.createLauncher(new StreamTaskListener(out)).kill(Map.of("cookie", "testKeepDaemonsAlive"));

        assertThrows("Process should have been excluded from the killing", IllegalThreadStateException.class, () -> process.exitValue());
    }

    @TestExtension({"considersKillingVetos", "considersKillingVetosOnSlave"})
    public static class VetoAllKilling extends ProcessKillingVeto {
        @Override
        public VetoCause vetoProcessKilling(@NonNull IOSProcess p) {
            return new VetoCause("Peace on earth");
        }
    }
}
