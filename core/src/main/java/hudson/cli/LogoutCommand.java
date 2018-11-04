package hudson.cli;

import hudson.Extension;
import jenkins.security.SecurityListener;
import org.acegisecurity.Authentication;

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
public class LogoutCommand extends CLICommand implements UnprotectedCLICommand {
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
    protected CLIReturnCode execute() throws Exception {
        ClientAuthenticationCache store = new ClientAuthenticationCache(checkChannel());

        Authentication auth = store.get();

        store.remove();

        SecurityListener.fireLoggedOut(auth.getName());

        return StandardCLIReturnCode.OK;
    }
}
