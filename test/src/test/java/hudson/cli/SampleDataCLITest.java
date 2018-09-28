package hudson.cli;

import jenkins.data.SampleDataCLI;
import org.apache.tools.ant.filters.StringInputStream;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class SampleDataCLITest {

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    @Test
    public void sampleDataTest() {

        CLICommandInvoker invoker = new CLICommandInvoker(jenkins, new SampleDataCLI());
        CLICommandInvoker.Result result = invoker.withStdin(new StringInputStream("{\"version\": 1,\"data\": [{\"memoryGB\": 2}]}")).invoke();
        System.out.println(result.stdout());


    }
}
