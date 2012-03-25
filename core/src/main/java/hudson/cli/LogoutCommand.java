package hudson.cli;

import hudson.Extension;

/**
 * Deletes the credential stored with the login command.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.351
 */
@Extension
public class LogoutCommand extends CLICommand {
    @Override
    public String getShortDescription() {
        return Messages.LogoutCommand_ShortDescription();
    }

    @Override
    protected int run() throws Exception {
        ClientAuthenticationCache store = new ClientAuthenticationCache(checkChannel());
        store.remove();
        return 0;
    }
}
