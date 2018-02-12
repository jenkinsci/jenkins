package jenkins;

import hudson.FilePath.FileCallable;
import hudson.remoting.VirtualChannel;
import jenkins.security.Roles;
import jenkins.slaves.RemotingVersionInfo;
import org.jenkinsci.remoting.RoleChecker;

import java.io.File;

/**
 * {@link FileCallable}s that are meant to be only used on the master.
 *
 * Note that the logic within {@link #invoke(File, VirtualChannel)} should use API of a minimum supported Remoting version.
 * See {@link RemotingVersionInfo#getMinimumSupportedVersion()}.
 *
 * @since 1.587 / 1.580.1
 * @param <T> the return type; note that this must either be defined in your plugin or included in the stock JEP-200 whitelist
 */
public abstract class MasterToSlaveFileCallable<T> implements FileCallable<T> {
    @Override
    public void checkRoles(RoleChecker checker) throws SecurityException {
        checker.check(this, Roles.SLAVE);
    }
    private static final long serialVersionUID = 1L;
}
