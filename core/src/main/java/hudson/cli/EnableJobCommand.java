package hudson.cli;

import hudson.Extension;
import hudson.model.AbstractProject;
import org.kohsuke.args4j.Argument;

/**
 * Enables a job.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class EnableJobCommand extends CLICommand {
    @Argument
    public AbstractProject src;

    public String getShortDescription() {
        return "Enables a previously disabled job";
    }

    protected int run() throws Exception {
        src.makeDisabled(false);
        return 0;
    }
}
