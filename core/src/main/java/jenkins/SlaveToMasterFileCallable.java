package jenkins;

import hudson.FilePath.FileCallable;
import jenkins.security.Roles;
import org.jenkinsci.remoting.RoleChecker;

/**
 * {@link FileCallable}s that can be executed on the master, sent by the slave.
 *
 * @since 1.THU
 */
public abstract class SlaveToMasterFileCallable<T> implements FileCallable<T> {
    @Override
    public void checkRoles(RoleChecker checker) throws SecurityException {
        checker.check(this, Roles.MASTER);
    }
    private static final long serialVersionUID = 1L;
}
