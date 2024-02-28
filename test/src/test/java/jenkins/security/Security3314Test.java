package jenkins.security;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import hudson.cli.CLICommandInvoker;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import jenkins.model.Jenkins;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.jvnet.hudson.test.JenkinsRule;

@RunWith(Parameterized.class)
public class Security3314Test {
    private String commandName;

    @Rule
    public final JenkinsRule j = new JenkinsRule();

    /**
     * connect-node to test the CLICommand behavior
     * disable-job to test the CLIRegisterer behavior (@CLIMethod)
     */
    @Parameterized.Parameters
    public static List<String> commands() {
        return Arrays.asList("connect-node", "disable-job");
    }

    public Security3314Test(String commandName) {
        this.commandName = commandName;
    }

    @Test
    public void commandShouldNotParseAt() throws Exception {
        CLICommandInvoker command = new CLICommandInvoker(j, commandName);

        Path tempPath = Files.createTempFile("tempFile", ".txt");
        tempPath.toFile().deleteOnExit();
        String content = "AtGotParsed";
        Files.write(tempPath, content.getBytes());

        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ)
                .invokeWithArgs("@" + tempPath);

        assertThat(result.stderr(), containsString("@" + tempPath));
        assertThat(result.stderr(), not(containsString("AtGotParsed")));
    }
}
