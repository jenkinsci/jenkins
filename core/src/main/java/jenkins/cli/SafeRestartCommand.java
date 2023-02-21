package jenkins.cli;

import hudson.Extension;
import hudson.cli.CLICommand;
import hudson.cli.Messages;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.kohsuke.args4j.Option;

/**
 * Safe Restart Jenkins - do not accept any new jobs and try to pause existing.
 *
 * @author meiswjn
 * @since TODO
 */
@Extension
public class SafeRestartCommand extends CLICommand {
    private static final Logger LOGGER = Logger.getLogger(SafeRestartCommand.class.getName());

    @Option(name = "-message", usage = "Message for safe restart that will be visible to users")
    public String message = null;

    @Override
    public String getShortDescription() {
        return Messages.SafeRestartCommand_ShortDescription();
    }

    @Override
    protected int run() throws Exception {
        Jenkins.get().doSafeRestart(null, message);
        return 0;
    }
}
