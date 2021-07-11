package jenkins;

import hudson.FilePath;
import hudson.remoting.VirtualChannel;
import jenkins.security.Roles;
import jenkins.slaves.RemotingVersionInfo;
import org.jenkinsci.remoting.RoleChecker;

import java.io.File;

/**
 * {@link FilePath.FileCallable}s that are meant to be only used on the controller.
 *
 * Note that the logic within {@link #invoke(File, VirtualChannel)} should use API of a minimum supported Remoting version.
 * See {@link RemotingVersionInfo#getMinimumSupportedVersion()}.
 *
 * @since TODO
 * @param <T> the return type; note that this must either be defined in your plugin or included in the stock JEP-200 whitelist
 */
public abstract class ControllerToAgentFileCallable<T> implements FilePath.FileCallable<T> {
    private static final long serialVersionUID = 1L;

    @Override
    public void checkRoles(RoleChecker checker) throws SecurityException {
        checker.check(this, Roles.AGENT);
    }
}
