package hudson.console;

import hudson.model.Computer;
import hudson.model.Run;
import hudson.slaves.SlaveComputer;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;

import java.io.IOException;
import java.io.OutputStream;

/**
 * @author Kohsuke Kawaguchi
 */
public class ConsoleLogFilterTest extends Assert {
    @Rule
    public JenkinsRule r = new JenkinsRule();

    /**
     * Checks
     */
    @Issue("JENKINS-30777")
    @Test public void decorateSlaveLog() throws Exception {
        SlaveComputer c = r.createSlave().getComputer();
        c.connect(false).get();
        assertTrue(c.getLog().contains("[["+c.getName()+"]] "));
    }

    @TestExtension
    public static class Impl extends ConsoleLogFilter {
        @Override
        public OutputStream decorateLogger(Run build, OutputStream logger) throws IOException, InterruptedException {
            return logger;
        }

        @Override
        public OutputStream decorateLogger(final Computer c, final OutputStream out) throws IOException, InterruptedException {
            return new LineTransformationOutputStream() {
                @Override
                protected void eol(byte[] b, int len) throws IOException {
                    out.write(("[["+c.getName()+"]] ").getBytes());
                    out.write(b, 0, len);
                }
            };
        }
    }
}
