package hudson.tasks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assume.assumeTrue;

import hudson.Functions;
import hudson.Launcher.ProcStarter;
import hudson.Proc;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.FakeLauncher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.PretendAgent;
import org.jvnet.hudson.test.recipes.LocalData;

/**
 * Tests for the BatchFile tasks class.
 *
 * @author David Ruhmann
 */
public class BatchFileTest {

    @Rule
    public JenkinsRule rule = new JenkinsRule();

    @Issue("JENKINS-7478")
    @Test
    public void validateBatchFileCommandEOL() {
        BatchFile obj = new BatchFile("echo A\necho B\recho C");
        rule.assertStringContains(obj.getCommand(), "echo A\r\necho B\r\necho C");
    }

    @Test
    public void validateBatchFileContents() {
        BatchFile obj = new BatchFile("echo A\necho B\recho C");
        rule.assertStringContains(obj.getContents(), "echo A\r\necho B\r\necho C\r\nexit %ERRORLEVEL%");
    }

    /* A FakeLauncher that just returns the specified error code */
    private class ReturnCodeFakeLauncher implements FakeLauncher {
        final int code;

        ReturnCodeFakeLauncher(int code)
        {
            this.code = code;
        }

        @Override
        public Proc onLaunch(ProcStarter p) {
            return new FinishedProc(this.code);
        }
    }

    private static Shell createNewBatchTask(String command, Integer unstableReturn) {
        Shell shell = new Shell(command);
        shell.setUnstableReturn(unstableReturn);
        return shell;
    }

    private void nonZeroErrorlevelShouldMakeBuildUnstable(int exitCode) throws Exception {
        PretendAgent agent = rule.createPretendAgent(new BatchFileTest.ReturnCodeFakeLauncher(exitCode));

        FreeStyleProject p = rule.createFreeStyleProject();
        p.getBuildersList().add(createNewBatchTask("", exitCode));
        p.setAssignedNode(agent);
        rule.buildAndAssertStatus(Result.UNSTABLE, p);
    }

    @Test
    @Issue("JENKINS-23786")
    public void windowsNonZeroErrorlevelsShouldMakeBuildUnstable() throws Exception {
        assumeTrue(Functions.isWindows());
        for (int exitCode : new int [] {Integer.MIN_VALUE, -1, 1, Integer.MAX_VALUE}) {
            nonZeroErrorlevelShouldMakeBuildUnstable(exitCode);
        }
    }

    private void nonZeroErrorlevelShouldBreakTheBuildByDefault(int exitCode) throws Exception {
        PretendAgent agent = rule.createPretendAgent(new BatchFileTest.ReturnCodeFakeLauncher(exitCode));

        FreeStyleProject p;

        p = rule.createFreeStyleProject();
        p.getBuildersList().add(createNewBatchTask("", null));
        p.setAssignedNode(agent);
        rule.buildAndAssertStatus(Result.FAILURE, p);

        p = rule.createFreeStyleProject();
        p.getBuildersList().add(createNewBatchTask("", 0));
        p.setAssignedNode(agent);
        rule.buildAndAssertStatus(Result.FAILURE, p);
    }

    @Test
    @Issue("JENKINS-23786")
    public void windowsNonZeroErrorlevelsShouldBreakTheBuildByDefault() throws Exception {
        assumeTrue(Functions.isWindows());
        for (int exitCode : new int [] {Integer.MIN_VALUE, -1, 1, Integer.MAX_VALUE}) {
            nonZeroErrorlevelShouldBreakTheBuildByDefault(exitCode);
        }
    }

    private void nonZeroErrorlevelShouldBreakTheBuildIfNotMatching(int exitCode) throws Exception {
        PretendAgent agent = rule.createPretendAgent(new BatchFileTest.ReturnCodeFakeLauncher(exitCode));

        final int notMatchingExitCode = 44;

        FreeStyleProject p = rule.createFreeStyleProject();
        p.getBuildersList().add(createNewBatchTask("", notMatchingExitCode));
        p.setAssignedNode(agent);
        rule.buildAndAssertStatus(Result.FAILURE, p);
    }

    @Test
    @Issue("JENKINS-23786")
    public void windowsErrorlevelsShouldBreakTheBuildIfNotMatching() throws Exception {
        assumeTrue(Functions.isWindows());
        for (int exitCode : new int [] {Integer.MIN_VALUE, -1, 1, Integer.MAX_VALUE}) {
            nonZeroErrorlevelShouldBreakTheBuildIfNotMatching(exitCode);
        }
    }

    @Test
    @Issue("JENKINS-23786")
    public void windowsErrorlevel0ShouldNeverMakeTheBuildUnstable() throws Exception {
        assumeTrue(Functions.isWindows());

        PretendAgent agent = rule.createPretendAgent(new BatchFileTest.ReturnCodeFakeLauncher(0));
        for (Integer unstableReturn : new Integer [] {null, 0, 1}) {
            FreeStyleProject p = rule.createFreeStyleProject();
            p.getBuildersList().add(createNewBatchTask("", unstableReturn));
            p.setAssignedNode(agent);
            rule.buildAndAssertSuccess(p);
        }
    }

    @Issue("JENKINS-23786")
    @Test
    public void windowsUnstableCodeZeroIsSameAsUnset() {
        assumeTrue(Functions.isWindows());

        /* Creating unstable=0 produces unstable=null */
        assertNull(createNewBatchTask("", 0).getUnstableReturn());
    }

    @Issue("JENKINS-40894")
    @Test
    @LocalData
    public void canLoadUnstableReturnFromDisk() {
        FreeStyleProject p = (FreeStyleProject) rule.jenkins.getItemByFullName("batch");
        BatchFile batchFile = (BatchFile) p.getBuildersList().get(0);
        assertEquals("unstable return", (Integer) 1, batchFile.getUnstableReturn());
    }
}
