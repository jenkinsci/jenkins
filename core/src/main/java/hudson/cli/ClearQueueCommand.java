package hudson.cli;

import hudson.Extension;
import hudson.model.Hudson;

/**
 * Clears the job queue.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class ClearQueueCommand extends CLICommand {
    public String getShortDescription() {
        return "Clears the job queue";
    }

    protected int run() throws Exception {
        Hudson.getInstance().getQueue().clear();
        return 0;
    }
}
