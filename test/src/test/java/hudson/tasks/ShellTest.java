package hudson.tasks;

import static org.junit.Assert.*;
import hudson.Functions;
import hudson.Launcher.ProcStarter;
import hudson.Proc;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;

import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.FakeLauncher;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.PretendSlave;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;

/**
 * @author Kohsuke Kawaguchi
 */
public class ShellTest {

    @Rule public JenkinsRule j = new JenkinsRule();

    @Test public void testBasic() throws Exception {
        // If we're on Windows, don't bother doing this.
        if (Functions.isWindows())
            return;

        // TODO: define a FakeLauncher implementation with easymock so that this kind of assertions can be simplified.
        PretendSlave s = j.createPretendSlave(new FakeLauncher() {
            public Proc onLaunch(ProcStarter p) throws IOException {
                // test the command line argument.
                List<String> cmds = p.cmds();
                assertEquals("/bin/sh",cmds.get(0));
                assertEquals("-xe",cmds.get(1));
                assertTrue(new File(cmds.get(2)).exists());

                // fake the execution
                PrintStream ps = new PrintStream(p.stdout());
                ps.println("Jenkins was here");
                ps.close();

                return new FinishedProc(0);
            }
        });
        FreeStyleProject p = j.createFreeStyleProject();
        p.getBuildersList().add(new Shell("echo abc"));
        p.setAssignedNode(s);

        FreeStyleBuild b = j.assertBuildStatusSuccess(p.scheduleBuild2(0).get());

        assertEquals(1,s.numLaunch);
        assertTrue(FileUtils.readFileToString(b.getLogFile()).contains("Jenkins was here"));
    }

    @Test public void testBuildFailsIfCommandFails() throws Exception {
        // If we're on Windows, don't bother doing this.
        if (Functions.isWindows())
            return;

        // TODO: define a FakeLauncher implementation with easymock so that this kind of assertions can be simplified.
        PretendSlave s = j.createPretendSlave(new FakeLauncher() {
            public Proc onLaunch(ProcStarter p) throws IOException {
                // test the command line argument.
                List<String> cmds = p.cmds();
                assertEquals("/bin/sh",cmds.get(0));
                assertEquals("-xe",cmds.get(1));
                assertTrue(new File(cmds.get(2)).exists());

                // fake the execution
                PrintStream ps = new PrintStream(p.stdout());
                ps.println("Jenkins was here");
                ps.close();

                return new FinishedProc(1);
            }
        });
        FreeStyleProject p = j.createFreeStyleProject();
        p.getBuildersList().add(new Shell("echo abc"));
        p.setAssignedNode(s);

        j.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0).get());
    }

    @Test public void testBuildUnstableIfCommandFailsAndMarkAsUnstableOptionSelected() throws Exception {
        // If we're on Windows, don't bother doing this.
        if (Functions.isWindows())
            return;

        // TODO: define a FakeLauncher implementation with easymock so that this kind of assertions can be simplified.
        PretendSlave s = j.createPretendSlave(new FakeLauncher() {
            public Proc onLaunch(ProcStarter p) throws IOException {
                // test the command line argument.
                List<String> cmds = p.cmds();
                assertEquals("/bin/sh",cmds.get(0));
                assertEquals("-xe",cmds.get(1));
                assertTrue(new File(cmds.get(2)).exists());

                // fake the execution
                PrintStream ps = new PrintStream(p.stdout());
                ps.println("Jenkins was here");
                ps.close();

                return new FinishedProc(1);
            }
        });
        FreeStyleProject p = j.createFreeStyleProject();
        p.getBuildersList().add(new Shell("echo abc", true));
        p.setAssignedNode(s);

        j.assertBuildStatus(Result.UNSTABLE, p.scheduleBuild2(0).get());
    }
}
