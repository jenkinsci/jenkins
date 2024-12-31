package hudson.console;

import static org.junit.Assert.assertTrue;

import hudson.model.Computer;
import hudson.model.Run;
import hudson.agents.AgentComputer;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;

/**
 * @author Kohsuke Kawaguchi
 */
public class ConsoleLogFilterTest {
    @Rule
    public JenkinsRule r = new JenkinsRule();

    /**
     * Checks
     */
    @Issue("JENKINS-30777")
    @Test public void decorateAgentLog() throws Exception {
        AgentComputer c = r.createAgent().getComputer();
        c.connect(false).get();
        assertTrue(c.getLog().contains("[[" + c.getName() + "]] "));
    }

    @TestExtension
    public static class Impl extends ConsoleLogFilter {
        @Override
        public OutputStream decorateLogger(Run build, OutputStream logger) {
            return logger;
        }

        @Override
        public OutputStream decorateLogger(final Computer c, OutputStream out) {
            return new LineTransformationOutputStream.Delegating(out) {
                @Override
                protected void eol(byte[] b, int len) throws IOException {
                    out.write(("[[" + c.getName() + "]] ").getBytes(Charset.defaultCharset()));
                    out.write(b, 0, len);
                }
            };
        }
    }
}
