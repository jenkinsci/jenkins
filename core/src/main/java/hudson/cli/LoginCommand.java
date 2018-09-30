package hudson.cli;

import hudson.Extension;
import hudson.security.ACL;
import jenkins.cli.CLIReturnCode;
import jenkins.cli.CLIReturnCodeStandard;
import jenkins.cli.UnprotectedCLICommand;
import jenkins.model.Jenkins;
import jenkins.security.SecurityListener;
import org.acegisecurity.Authentication;
import org.kohsuke.args4j.CmdLineException;

import java.io.PrintStream;

/**
 * Saves the current credential to allow future commands to run without explicit credential information.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.351
 * @deprecated Assumes Remoting, and vulnerable to JENKINS-12543.
 */
@Extension
@Deprecated
public class LoginCommand extends CLICommand implements UnprotectedCLICommand {
    @Override
    public String getShortDescription() {
        return Messages.LoginCommand_ShortDescription();
    }

    @Override
    protected void printUsageSummary(PrintStream stderr) {
        super.printUsageSummary(stderr);
        stderr.println(Messages.LoginCommand_FullDescription());
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
    protected CLIReturnCode execute() throws Exception {
        Authentication a = Jenkins.getAuthentication();
        if (ACL.isAnonymous(a)) {
            throw new CmdLineException("No credentials specified."); // this causes CLI to show the command line options.
        }

        ClientAuthenticationCache store = new ClientAuthenticationCache(checkChannel());
        store.set(a);

        SecurityListener.fireLoggedIn(a.getName());

        return CLIReturnCodeStandard.OK;
    }

}
