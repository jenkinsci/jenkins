package hudson.cli;

import static hudson.cli.CLICommandInvoker.Matcher.failedWith;
import static hudson.cli.CLICommandInvoker.Matcher.succeededSilently;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import hudson.cli.CLICommandInvoker.Result;
import hudson.cli.declarative.CLIMethod;
import hudson.model.User;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.kohsuke.args4j.Option;

public class CLIRegistererTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void testAuthWithSecurityRealmCLIAuthenticator() {
        j.getInstance().setSecurityRealm(j.createDummySecurityRealm());

        CLICommandInvoker command = new CLICommandInvoker(j, "command-for-test");

        Result invocation = command.invokeWithArgs("--username", "foo", "--password", "foo", "--expected", "foo");
        assertThat(invocation, succeededSilently());

        invocation = command.invokeWithArgs("--expected", "anonymous");
        assertThat(invocation, failedWith(1));
        assertThat(invocation.stderr(), containsString("Not authenticated"));

        invocation = command.invokeWithArgs("--username", "foo", "--password", "invalid", "--expected", "anonymous");
        assertThat(invocation, failedWith(1));
        assertThat(invocation.stderr(), containsString("BadCredentialsException: foo"));
    }

    @CLIMethod(name = "command-for-test")
    public static int commandForTest(@Option(name = "--expected") String expected) {
        final User user = User.current();
        if (user == null) throw new RuntimeException("Not authenticated");

        Assert.assertEquals(expected, user.getId());

        return 0;
    }
}
