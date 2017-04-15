package jenkins;

import hudson.model.Failure;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class CLITest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    /**
     * Checks if the kill switch works correctly
     */
    @Test
    public void killSwitch() throws Exception {
        // this should succeed, as a control case
        j.jenkins.setSlaveAgentPort(-1); // force HTTP connection
        makeCall();
        j.jenkins.setSlaveAgentPort(0); // allow TCP connection
        makeCall();

        CLI.get().setEnabled(false);
        try {
            j.jenkins.setSlaveAgentPort(-1);
            makeCall();
            fail("Should have been rejected");
        } catch (Exception e) {
            // attempt to make a call should fail
            e.printStackTrace();
            // currently sends a 403
        }
        try {
            j.jenkins.setSlaveAgentPort(0);
            makeCall();
            fail("Should have been rejected");
        } catch (Exception e) {
            // attempt to make a call should fail
            e.printStackTrace();

            // the current expected failure mode is EOFException, though we don't really care how it fails
        }
    }

    private void makeCall() throws Exception {
        int r = hudson.cli.CLI._main(new String[] {"-s", j.getURL().toString(), "-remoting", "-noKeyAuth", "version"});
        if (r!=0)
            throw new Failure("CLI failed");
    }
}
