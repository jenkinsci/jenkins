package jenkins;

import hudson.cli.FullDuplexHttpStream;
import hudson.model.Computer;
import hudson.model.Failure;
import hudson.remoting.Channel;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.FileNotFoundException;
import java.net.URL;

import static org.junit.Assert.*;

/**
 * @author Kohsuke Kawaguchi
 */
public class CLITest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    /**
     * Checks if the kill switch works correctly
     */
    @Test
    public void killSwitch() throws Exception {
        // this should succeed, as a control case
        makeHttpCall();
        makeJnlpCall();

        CLI.DISABLED = true;
        try {
            try {
                makeHttpCall();
                fail("Should have been rejected");
            } catch (FileNotFoundException e) {
                // attempt to make a call should fail
            }
            try {
                makeJnlpCall();
                fail("Should have been rejected");
            } catch (Exception e) {
                // attempt to make a call should fail
                e.printStackTrace();

                // the current expected failure mode is EOFException, though we don't really care how it fails
            }
        } finally {
            CLI.DISABLED = false;
        }
    }

    private void makeHttpCall() throws Exception {
        FullDuplexHttpStream con = new FullDuplexHttpStream(new URL(j.getURL(), "cli"));
        Channel ch = new Channel("test connection", Computer.threadPoolForRemoting, con.getInputStream(), con.getOutputStream());
        ch.close();
    }

    private void makeJnlpCall() throws Exception {
        int r = hudson.cli.CLI._main(new String[]{"-s",j.getURL().toString(), "version"});
        if (r!=0)
            throw new Failure("CLI failed");
    }
}
