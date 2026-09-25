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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class Security3314Test {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    /**
     * connect-node to test the CLICommand behavior
     * disable-job to test the CLIRegisterer behavior (@CLIMethod)
     */
    static List<String> commands() {
        return Arrays.asList("connect-node", "disable-job");
    }


    @ParameterizedTest
    @MethodSource("commands")
    void commandShouldNotParseAt(String commandName) throws Exception {
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
