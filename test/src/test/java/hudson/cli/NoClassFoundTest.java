package hudson.cli;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
public class NoClassFoundTest {

    private JenkinsRule j = new JenkinsRule();

    @BeforeEach
    public void setup(JenkinsRule j) throws Exception {
        this.j = j;
    }

    @Test
    void testCLI() throws Exception {
        CLI._main(new String[]{"-http", "-s", j.getURL().toString(), "who-am-i"});
    }
}
