package jenkins.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;

import java.util.logging.Level;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;

public class JenkinsVersionTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Rule
    public LoggerRule logging = new LoggerRule().record(Jenkins.class, Level.INFO).capture(100);

    @Test
    public void printsVersion() {
        assertThat(logging.getMessages(), hasItem(containsString(Jenkins.getVersion().toString())));
    }
}
