package hudson.cli;

import hudson.cli.CLICommandInvoker.Result;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static hudson.cli.CLICommandInvoker.Matcher.failedWith;
import static hudson.cli.CLICommandInvoker.Matcher.succeededSilently;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CLIRegistererTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    /**
     * See also {@link ClientAuthenticationCacheTest#overHttpAlsoForRegisterer()} for the equivalent using remoting / authenticationCache, i.e. login+logout
     */
    @Test
    public void testAuthWithSecurityRealmCLIAuthenticator() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        FreeStyleProject project = j.createFreeStyleProject();
        FreeStyleBuild build1 = j.buildAndAssertSuccess(project);
        assertFalse("By default the build log should not be kept", build1.isKeepLog());

        CLICommandInvoker command = new CLICommandInvoker(j, "keep-build");

        Result invocation = command.invokeWithArgs(project.getName(), build1.getNumber() + "", "--username", "foo", "--password", "invalid");
        assertThat(invocation, failedWith(7));
        assertThat(invocation.stderr(), containsString("ERROR: Bad Credentials. Search the server log for "));
        assertFalse("The command should have not been executed", build1.isKeepLog());

        invocation = command.invokeWithArgs(project.getName(), build1.getNumber() + "", "--username", "foo", "--password", "foo");
        assertThat(invocation, succeededSilently());
        assertTrue("The command should have been executed", build1.isKeepLog());
    }
}
