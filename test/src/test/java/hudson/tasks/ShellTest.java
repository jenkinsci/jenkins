package hudson.tasks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;


import hudson.Functions;
import hudson.Launcher.ProcStarter;
import hudson.Proc;
import hudson.model.Result;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import org.apache.commons.io.FileUtils;
import org.jvnet.hudson.test.FakeLauncher;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.PretendSlave;
import org.jvnet.hudson.test.Issue;

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

    @Issue("JENKINS-23786")
    public void testUnstableReturn() throws Exception {
        if(Functions.isWindows())
            return;

        PretendSlave returns2 = rule.createPretendSlave(new ReturnCodeFakeLauncher(2));
        PretendSlave returns1 = rule.createPretendSlave(new ReturnCodeFakeLauncher(1));
        PretendSlave returns0 = rule.createPretendSlave(new ReturnCodeFakeLauncher(0));

        FreeStyleProject p;
        FreeStyleBuild b;

        /* Unstable=2, error codes 0/1/2 */
        p = rule.createFreeStyleProject();
        p.getBuildersList().add(new Shell("", 2));
        p.setAssignedNode(returns2);
        b = assertBuildStatus(Result.UNSTABLE, p.scheduleBuild2(0).get());

        p = rule.createFreeStyleProject();
        p.getBuildersList().add(new Shell("", 2));
        p.setAssignedNode(returns1);
        b = assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0).get());

        p = rule.createFreeStyleProject();
        p.getBuildersList().add(new Shell("", 2));
        p.setAssignedNode(returns0);
        b = assertBuildStatus(Result.SUCCESS, p.scheduleBuild2(0).get());

        /* unstable=null, error codes 0/1/2 */
        p = rule.createFreeStyleProject();
        p.getBuildersList().add(new Shell("", null));
        p.setAssignedNode(returns2);
        b = assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0).get());

        p = rule.createFreeStyleProject();
        p.getBuildersList().add(new Shell("", null));
        p.setAssignedNode(returns1);
        b = assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0).get());

        p = rule.createFreeStyleProject();
        p.getBuildersList().add(new Shell("", null));
        p.setAssignedNode(returns0);
        b = assertBuildStatus(Result.SUCCESS, p.scheduleBuild2(0).get());

        /* Creating unstable=0 produces unstable=null */
        assertNull( new Shell("",0).getUnstableReturn() );

    }


}
