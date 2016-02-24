package hudson.cli;

import static hudson.cli.CLICommandInvoker.Matcher.failedWith;
import static hudson.cli.CLICommandInvoker.Matcher.succeededSilently;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.CoreMatchers.is;

import jenkins.model.Jenkins;
import hudson.cli.CLICommandInvoker;
import hudson.cli.CLICommandInvoker.Result;

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
        assertThat(invocation, failedWith(7));
        assertThat(invocation.stderr(), containsString("ERROR: Bad Credentials. Search the server log for "));
        assertThat("Unauthorized command was executed", Jenkins.getInstance().isQuietingDown(), is(false));

        invocation = command.invokeWithArgs("--username", "foo", "--password", "foo");
        assertThat(invocation, succeededSilently());
        assertThat("Authorized command was not executed", Jenkins.getInstance().isQuietingDown(), is(true));
    }
}
