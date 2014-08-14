package hudson.cli;

import static hudson.cli.CLICommandInvoker.Matcher.failedWith;
import static hudson.cli.CLICommandInvoker.Matcher.succeededSilently;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.containsString;
import jenkins.model.Jenkins;
import hudson.cli.CLICommandInvoker;
import hudson.cli.CLICommandInvoker.Result;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class CLIRegistererTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void testAuthWithSecurityRealmCLIAuthenticator() {
        j.getInstance().setSecurityRealm(j.createDummySecurityRealm());

        CLICommandInvoker command = new CLICommandInvoker(j, "quiet-down");

        Result invocation = command.invokeWithArgs("--username", "foo", "--password", "invalid");
        assertThat(invocation, failedWith(1));
        assertThat(invocation.stderr(), containsString("BadCredentialsException: foo"));
        Assert.assertFalse("Unauthorized command was executed", Jenkins.getInstance().isQuietingDown());

        invocation = command.invokeWithArgs("--username", "foo", "--password", "foo");
        assertThat(invocation, succeededSilently());
        Assert.assertTrue("Authorized command was not executed", Jenkins.getInstance().isQuietingDown());
    }
}
