package hudson.cli;

import hudson.Extension;
import hudson.model.AbstractProject;
import org.kohsuke.args4j.Argument;

/**
 * Disables a job.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class DisableJobCommand extends CLICommand {
    @Argument
    public AbstractProject src;

    public String getShortDescription() {
        return "Disables a job";
    }

    protected int run() throws Exception {
        src.makeDisabled(true);
        return 0;
    }
}
