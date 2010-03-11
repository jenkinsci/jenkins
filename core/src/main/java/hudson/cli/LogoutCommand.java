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
        return "Deletes the credential stored with the login command";
    }

    @Override
    protected int run() throws Exception {
        ClientAuthenticationCache store = new ClientAuthenticationCache(channel);
        store.remove();
        return 0;
    }
}
