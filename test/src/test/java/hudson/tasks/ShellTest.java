package hudson.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import hudson.Functions;
import hudson.Launcher.ProcStarter;
import hudson.Proc;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.FakeLauncher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.PretendSlave;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.hudson.test.recipes.LocalData;

/**
 * Tests for the Shell tasks class
 *
 * @author Kohsuke Kawaguchi
 */
@WithJenkins
class ShellTest {

    private JenkinsRule rule;

    @BeforeEach
    void setUp(JenkinsRule j) {
        rule = j;
    }

    @Test
    void validateShellCommandEOL() {
        Shell obj = new Shell("echo A\r\necho B\recho C");
        rule.assertStringContains(obj.getCommand(), "echo A\necho B\necho C");
    }

    @Test
    void validateShellContents() {
        Shell obj = new Shell("echo A\r\necho B\recho C");
        rule.assertStringContains(obj.getContents(), "\necho A\necho B\necho C");
    }

    @Test
    void testBasic() throws Exception {
        Assumptions.assumeFalse(Functions.isWindows(), "If we're on Windows, don't bother doing this");

        // TODO: define a FakeLauncher implementation with easymock so that this kind of assertions can be simplified.
        PretendSlave s = rule.createPretendSlave(p -> {
            // test the command line argument.
            List<String> cmds = p.cmds();
            rule.assertStringContains("/bin/sh", cmds.get(0));
            rule.assertStringContains("-xe", cmds.get(1));
            assertTrue(new File(cmds.get(2)).exists());

            // fake the execution
            PrintStream ps = new PrintStream(p.stdout());
            ps.println("Hudson was here");
            ps.close();

            return new FakeLauncher.FinishedProc(0);
        });
        FreeStyleProject p = rule.createFreeStyleProject();
        p.getBuildersList().add(new Shell("echo abc"));
        p.setAssignedNode(s);

        FreeStyleBuild b = rule.buildAndAssertSuccess(p);

        assertEquals(1, s.numLaunch);
        assertTrue(IOUtils.toString(b.getLogInputStream(), StandardCharsets.UTF_8).contains("Hudson was here"));
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

    private static Shell createNewShell(String command, Integer unstableReturn) {
        Shell shell = new Shell(command);
        shell.setUnstableReturn(unstableReturn);
        return shell;
    }

    private void nonZeroExitCodeShouldMakeBuildUnstable(int exitCode) throws Exception {
        PretendSlave slave = rule.createPretendSlave(new ReturnCodeFakeLauncher(exitCode));

        FreeStyleProject p = rule.createFreeStyleProject();
        p.getBuildersList().add(createNewShell("", exitCode));
        p.setAssignedNode(slave);
        rule.buildAndAssertStatus(Result.UNSTABLE, p);
    }

    @Test
    @Issue("JENKINS-23786")
    void unixExitCodes1To255ShouldMakeBuildUnstable() throws Exception {
        assumeFalse(Functions.isWindows());
        for (int exitCode : new int [] {1, 2, 255}) {
            nonZeroExitCodeShouldMakeBuildUnstable(exitCode);
        }
    }

    private void nonZeroExitCodeShouldBreakTheBuildByDefault(int exitCode) throws Exception {
        PretendSlave slave = rule.createPretendSlave(new ReturnCodeFakeLauncher(exitCode));

        FreeStyleProject p;

        p = rule.createFreeStyleProject();
        p.getBuildersList().add(createNewShell("", null));
        p.setAssignedNode(slave);
        rule.buildAndAssertStatus(Result.FAILURE, p);

        p = rule.createFreeStyleProject();
        p.getBuildersList().add(createNewShell("", 0));
        p.setAssignedNode(slave);
        rule.buildAndAssertStatus(Result.FAILURE, p);
    }

    @Test
    @Issue("JENKINS-23786")
    void unixExitCodes1To255ShouldBreakTheBuildByDefault() throws Exception {
        assumeFalse(Functions.isWindows());

        for (int exitCode : new int [] {1, 2, 255}) {
            nonZeroExitCodeShouldBreakTheBuildByDefault(exitCode);
        }
    }

    private void nonZeroExitCodeShouldBreakTheBuildIfNotMatching(int exitCode) throws Exception {
        PretendSlave slave = rule.createPretendSlave(new ReturnCodeFakeLauncher(exitCode));

        final int notMatchingExitCode = 44;

        FreeStyleProject p = rule.createFreeStyleProject();
        p.getBuildersList().add(createNewShell("", notMatchingExitCode));
        p.setAssignedNode(slave);
        rule.buildAndAssertStatus(Result.FAILURE, p);
    }

    @Test
    @Issue("JENKINS-23786")
    void unixExitCodes1To255ShouldBreakTheBuildIfNotMatching() throws Exception {
        assumeFalse(Functions.isWindows());
        for (int exitCode : new int [] {1, 2, 255}) {
            nonZeroExitCodeShouldBreakTheBuildIfNotMatching(exitCode);
        }
    }

    @Test
    @Issue("JENKINS-23786")
    void unixExitCodes0ShouldNeverMakeTheBuildUnstable() throws Exception {
        assumeFalse(Functions.isWindows());

        PretendSlave slave = rule.createPretendSlave(new ReturnCodeFakeLauncher(0));
        for (Integer unstableReturn : new Integer [] {null, 0, 1}) {
            FreeStyleProject p = rule.createFreeStyleProject();
            p.getBuildersList().add(createNewShell("", unstableReturn));
            p.setAssignedNode(slave);
            rule.buildAndAssertSuccess(p);
        }
    }

    @Issue("JENKINS-23786")
    @Test
    void unixUnstableCodeZeroIsSameAsUnset() {
        assumeFalse(Functions.isWindows());

        /* Creating unstable=0 produces unstable=null */
        assertNull(createNewShell("", 0).getUnstableReturn());
    }

    @Issue("JENKINS-40894")
    @Test
    @LocalData
    void canLoadUnstableReturnFromDisk() {
        FreeStyleProject p = (FreeStyleProject) rule.jenkins.getItemByFullName("test");
        Shell shell = (Shell) p.getBuildersList().getFirst();
        assertEquals((Integer) 1, shell.getUnstableReturn(), "unstable return");
    }

}
