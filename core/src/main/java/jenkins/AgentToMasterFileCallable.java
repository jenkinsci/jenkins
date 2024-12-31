package jenkins;

import hudson.FilePath.FileCallable;
import hudson.Main;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.security.Roles;
import jenkins.util.JenkinsJVM;
import org.jenkinsci.remoting.RoleChecker;

/**
 * {@link FileCallable}s that can be executed on the master, sent by the agent.
 * Note that any serializable fields must either be defined in your plugin or included in the stock JEP-200 whitelist.
 * Additionally, this callable can be called with any {@link hudson.FilePath}, it is your responsibility to validate it
 * in {@link #invoke(java.io.File, hudson.remoting.VirtualChannel)}.
 * @since 1.587 / 1.580.1
 * @deprecated Use {@link jenkins.security.AgentToMasterCallable} instead (and only if you really have to), and think
 * carefully about the <a href="https://www.jenkins.io/doc/developer/security/remoting-callables/">security implications</a>.
 *
 * @see jenkins.security.AgentToMasterCallable
 * @see org.jenkinsci.remoting.RoleSensitive
 */
@Deprecated
public abstract class AgentToMasterFileCallable<T> implements FileCallable<T> {

    public static final Logger LOGGER = Logger.getLogger(AgentToMasterFileCallable.class.getName());

    @Override
    public void checkRoles(RoleChecker checker) throws SecurityException {
        warnOnController();
        checker.check(this, Roles.MASTER);
    }

    protected Object readResolve() {
        warnOnController();
        return this;
    }

    private void warnOnController() {
        if (JenkinsJVM.isJenkinsJVM() && (Main.isUnitTest || Main.isDevelopmentMode)) { // No point in spamming admins who cannot do anything
            LOGGER.log(Level.WARNING, "AgentToMasterFileCallable is deprecated. '" + this + "' should be replaced. See https://www.jenkins.io/doc/developer/security/remoting-callables/");
        }
    }

    private static final long serialVersionUID = 1L;
}
