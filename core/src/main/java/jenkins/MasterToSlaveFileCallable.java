package jenkins;

import hudson.FilePath.FileCallable;
import jenkins.security.Roles;
import org.jenkinsci.remoting.RoleChecker;

/**
 * {@link FileCallable}s that are meant to be only used on the master.
 *
 * @since 1.THU
 */
public abstract class MasterToSlaveFileCallable<T> implements FileCallable<T> {
    @Override
    public void checkRoles(RoleChecker checker) throws SecurityException {
        checker.check(this, Roles.SLAVE);
    }
    private static final long serialVersionUID = 1L;
}
