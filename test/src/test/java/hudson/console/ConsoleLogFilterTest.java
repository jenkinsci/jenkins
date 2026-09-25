package hudson.console;

import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.Computer;
import hudson.model.Run;
import hudson.slaves.SlaveComputer;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * @author Kohsuke Kawaguchi
 */
@WithJenkins
class ConsoleLogFilterTest {

    private JenkinsRule r;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        r = rule;
    }

    /**
     * Checks
     */
    @Issue("JENKINS-30777")
    @Test
    void decorateSlaveLog() throws Exception {
        SlaveComputer c = r.createSlave().getComputer();
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
