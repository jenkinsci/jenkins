package hudson.cli;

import hudson.Extension;
import hudson.model.Hudson;
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
        return "Saves the current credential to allow future commands to run without explicit credential information";
    }

    /**
     * If we use the stored authentication for the login command, login becomes no-op, which is clearly not what
     * the user has intended.
     */
    @Override
    protected Authentication loadStoredAuthentication() throws InterruptedException {
        return Hudson.ANONYMOUS;
    }

    @Override
    protected int run() throws Exception {
        Authentication a = Hudson.getAuthentication();
        if (a==Hudson.ANONYMOUS)
            throw new CmdLineException("No credentials specified."); // this causes CLI to show the command line options.

        ClientAuthenticationCache store = new ClientAuthenticationCache(channel);
        store.set(a);

        return 0;
    }

}
