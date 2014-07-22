package hudson.tasks;

import static org.junit.Assert.asssertNull;
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
import org.jvnet.hudson.test.JenkinsRule;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;

    @Rule
    public JenkinsRule rule = new JenkinsRule();

    @Issue("JENKINS-7478")
    @Test
    public void validateBatchFileCommandEOL() throws Exception {
        BatchFile obj = new BatchFile("echo A\necho B\recho C");
        rule.assertStringContains(obj.getCommand(), "echo A\r\necho B\r\necho C");
    }

    @Test
    public void validateBatchFileContents() throws Exception {
        BatchFile obj = new BatchFile("echo A\necho B\recho C");
        rule.assertStringContains(obj.getContents(), "echo A\r\necho B\r\necho C\r\nexit %ERRORLEVEL%");
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
        if(!Functions.isWindows())
            return;

        PretendSlave returns2 = createPretendSlave(new ReturnCodeFakeLauncher(2));
        PretendSlave returns1 = createPretendSlave(new ReturnCodeFakeLauncher(1));
        PretendSlave returns0 = createPretendSlave(new ReturnCodeFakeLauncher(0));

        FreeStyleProject p;
        FreeStyleBuild b;

        /* Unstable=2, error codes 0/1/2 */
        p = createFreeStyleProject();
        p.getBuildersList().add(new BatchFile("", 2));
        p.setAssignedNode(returns2);
        b = assertBuildStatus(Result.UNSTABLE, p.scheduleBuild2(0).get());

        p = createFreeStyleProject();
        p.getBuildersList().add(new BatchFile("", 2));
        p.setAssignedNode(returns1);
        b = assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0).get());

        p = createFreeStyleProject();
        p.getBuildersList().add(new BatchFile("", 2));
        p.setAssignedNode(returns0);
        b = assertBuildStatus(Result.SUCCESS, p.scheduleBuild2(0).get());

        /* unstable=null, error codes 0/1/2 */
        p = createFreeStyleProject();
        p.getBuildersList().add(new BatchFile("", null));
        p.setAssignedNode(returns2);
        b = assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0).get());

        p = createFreeStyleProject();
        p.getBuildersList().add(new BatchFile("", null));
        p.setAssignedNode(returns1);
        b = assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0).get());

        p = createFreeStyleProject();
        p.getBuildersList().add(new BatchFile("", null));
        p.setAssignedNode(returns0);
        b = assertBuildStatus(Result.SUCCESS, p.scheduleBuild2(0).get());

        /* Creating unstable=0 produces unstable=null */
        assertNull( new BatchFile("",0).getUnstableReturn() );

    }

}
