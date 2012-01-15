package hudson.cli;

import hudson.Extension;
import jenkins.model.Jenkins;
import org.acegisecurity.Authentication;
import org.kohsuke.args4j.CmdLineException;

/**
 * Saves the current credential to allow future commands to run without explicit credential information.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.351
 */
@Extension
public class LoginCommand extends CLICommand {
    @Override
    public String getShortDescription() {
        return Messages.LoginCommand_ShortDescription();
    }

    /**
     * If we use the stored authentication for the login command, login becomes no-op, which is clearly not what
     * the user has intended.
     */
    @Override
    protected Authentication loadStoredAuthentication() throws InterruptedException {
        return Jenkins.ANONYMOUS;
    }

    @Override
    protected int run() throws Exception {
        Authentication a = Jenkins.getAuthentication();
        if (a== Jenkins.ANONYMOUS)
            throw new CmdLineException("No credentials specified."); // this causes CLI to show the command line options.

        ClientAuthenticationCache store = new ClientAuthenticationCache(checkChannel());
        store.set(a);

        return 0;
    }

}
