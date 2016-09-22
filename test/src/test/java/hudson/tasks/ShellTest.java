package hudson.tasks;

import static org.junit.Assert.assertNull;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

import hudson.Functions;
import hudson.Launcher.ProcStarter;
import hudson.Proc;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;

import org.apache.commons.io.FileUtils;
import org.jvnet.hudson.test.FakeLauncher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.PretendSlave;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * Tests for the Shell tasks class
 *
 * @author Kohsuke Kawaguchi
 */
public class ShellTest {

    @Rule
    public JenkinsRule rule = new JenkinsRule();

    @Test
    public void validateShellCommandEOL() throws Exception {
        Shell obj = new Shell("echo A\r\necho B\recho C");
        rule.assertStringContains(obj.getCommand(), "echo A\necho B\necho C");
    }

    @Test
    public void validateShellContents() throws Exception {
        Shell obj = new Shell("echo A\r\necho B\recho C");
        rule.assertStringContains(obj.getContents(), "\necho A\necho B\necho C");
    }

    @Test
    public void testBasic() throws Exception {
        Assume.assumeFalse("If we're on Windows, don't bother doing this", Functions.isWindows());

        // TODO: define a FakeLauncher implementation with easymock so that this kind of assertions can be simplified.
        PretendSlave s = rule.createPretendSlave(new FakeLauncher() {
            public Proc onLaunch(ProcStarter p) throws IOException {
                // test the command line argument.
                List<String> cmds = p.cmds();
                rule.assertStringContains("/bin/sh",cmds.get(0));
                rule.assertStringContains("-xe",cmds.get(1));
                assertTrue(new File(cmds.get(2)).exists());

                // fake the execution
                PrintStream ps = new PrintStream(p.stdout());
                ps.println("Hudson was here");
                ps.close();

                return new FinishedProc(0);
            }
        });
        FreeStyleProject p = rule.createFreeStyleProject();
        p.getBuildersList().add(new Shell("echo abc"));
        p.setAssignedNode(s);

        FreeStyleBuild b = rule.assertBuildStatusSuccess(p.scheduleBuild2(0).get());

        assertEquals(1,s.numLaunch);
        assertTrue(FileUtils.readFileToString(b.getLogFile()).contains("Hudson was here"));
    }

    /* A FakeLauncher that just returns the specified error code */
    private class ReturnCodeFakeLauncher implements FakeLauncher {
        final int code;

        ReturnCodeFakeLauncher(int code)
        {
            super();
            this.code = code;
        }

        @Override
        public Proc onLaunch(ProcStarter p) throws IOException {
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
        rule.assertBuildStatus(Result.UNSTABLE, p.scheduleBuild2(0).get());
    }

    @Test
    @Issue("JENKINS-23786")
    public void unixExitCodes1To255ShouldMakeBuildUnstable() throws Exception {
        assumeFalse(Functions.isWindows());
        for( int exitCode: new int [] {1, 2, 255}) {
            nonZeroExitCodeShouldMakeBuildUnstable(exitCode);
        }
    }

    private void nonZeroExitCodeShouldBreakTheBuildByDefault(int exitCode) throws Exception {
        PretendSlave slave = rule.createPretendSlave(new ReturnCodeFakeLauncher(exitCode));

        FreeStyleProject p;

        p = rule.createFreeStyleProject();
        p.getBuildersList().add(createNewShell("", null));
        p.setAssignedNode(slave);
        rule.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0).get());

        p = rule.createFreeStyleProject();
        p.getBuildersList().add(createNewShell("", 0));
        p.setAssignedNode(slave);
        rule.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0).get());
    }

    @Test
    @Issue("JENKINS-23786")
    public void unixExitCodes1To255ShouldBreakTheBuildByDefault() throws Exception {
        assumeFalse(Functions.isWindows());

        for( int exitCode: new int [] {1, 2, 255}) {
            nonZeroExitCodeShouldBreakTheBuildByDefault(exitCode);
        }
    }

    private void nonZeroExitCodeShouldBreakTheBuildIfNotMatching(int exitCode) throws Exception {
        PretendSlave slave = rule.createPretendSlave(new ReturnCodeFakeLauncher(exitCode));

        final int notMatchingExitCode = 44;

        FreeStyleProject p = rule.createFreeStyleProject();
        p.getBuildersList().add(createNewShell("", notMatchingExitCode));
        p.setAssignedNode(slave);
        rule.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0).get());
    }

    @Test
    @Issue("JENKINS-23786")
    public void unixExitCodes1To255ShouldBreakTheBuildIfNotMatching() throws Exception {
        assumeFalse(Functions.isWindows());
        for( int exitCode: new int [] {1, 2, 255}) {
            nonZeroExitCodeShouldBreakTheBuildIfNotMatching(exitCode);
        }
    }

    @Test
    @Issue("JENKINS-23786")
    public void unixExitCodes0ShouldNeverMakeTheBuildUnstable() throws Exception {
        assumeFalse(Functions.isWindows());

        PretendSlave slave = rule.createPretendSlave(new ReturnCodeFakeLauncher(0));
        for( Integer unstableReturn: new Integer [] {null, 0, 1}) {
            FreeStyleProject p = rule.createFreeStyleProject();
            p.getBuildersList().add(createNewShell("", unstableReturn));
            p.setAssignedNode(slave);
            rule.assertBuildStatus(Result.SUCCESS, p.scheduleBuild2(0).get());
        }
    }

    @Issue("JENKINS-23786")
    @Test
    public void unixUnstableCodeZeroIsSameAsUnset() throws Exception {
        assumeFalse(Functions.isWindows());

        /* Creating unstable=0 produces unstable=null */
        assertNull( createNewShell("",0).getUnstableReturn() );
    }

}
