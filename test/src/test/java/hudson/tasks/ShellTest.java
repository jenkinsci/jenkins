package hudson.tasks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import hudson.Functions;
import hudson.Launcher.ProcStarter;
import hudson.Proc;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import org.apache.commons.io.FileUtils;
import org.jvnet.hudson.test.FakeLauncher;
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
}
