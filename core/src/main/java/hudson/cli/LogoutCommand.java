package hudson.cli;

import hudson.Extension;
import java.io.PrintStream;

/**
 * Deletes the credential stored with the login command.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.351
 * @deprecated See {@link LoginCommand}.
 */
@Deprecated
@Extension
public class LogoutCommand extends CLICommand {
    @Override
    public String getShortDescription() {
        return Messages.LogoutCommand_ShortDescription();
    }

    @Override
    protected void printUsageSummary(PrintStream stderr) {
        super.printUsageSummary(stderr);
        stderr.println(Messages.LogoutCommand_FullDescription());
    }

    @Override
    protected int run() throws Exception {
        ClientAuthenticationCache store = new ClientAuthenticationCache(checkChannel());
        store.remove();
        return 0;
    }
}
