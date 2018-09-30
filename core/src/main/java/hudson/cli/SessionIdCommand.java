package hudson.cli;

import hudson.Extension;
import jenkins.cli.CLIReturnCode;
import jenkins.cli.CLIReturnCodeStandard;
import jenkins.model.Jenkins;

/**
 * Prints the current session ID number (that changes for every run)
 * to allow clients to reliably wait for a restart.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class SessionIdCommand extends CLICommand {
    @Override
    public String getShortDescription() {
        return Messages.SessionIdCommand_ShortDescription();
    }

    protected CLIReturnCode execute() {
        stdout.println(Jenkins.SESSION_HASH);
        return CLIReturnCodeStandard.OK;
    }
}

