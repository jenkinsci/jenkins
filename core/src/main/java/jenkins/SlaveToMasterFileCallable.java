package jenkins;

import hudson.FilePath.FileCallable;
import jenkins.security.Roles;
import org.jenkinsci.remoting.RoleChecker;

/**
 * {@link FileCallable}s that can be executed on the master, sent by the agent.
 * Note that any serializable fields must either be defined in your plugin or included in the stock JEP-200 whitelist.
 * @since 1.587 / 1.580.1
 */
public abstract class SlaveToMasterFileCallable<T> implements FileCallable<T> {
    @Override
    public void checkRoles(RoleChecker checker) throws SecurityException {
        checker.check(this, Roles.MASTER);
    }
    private static final long serialVersionUID = 1L;
}
