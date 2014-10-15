package jenkins.security;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.remoting.Callable;
import hudson.remoting.ChannelBuilder;
import jenkins.model.Jenkins;
import org.jenkinsci.remoting.Role;
import org.jenkinsci.remoting.RoleChecker;
import org.jenkinsci.remoting.RoleSensitive;

import java.util.Collection;

/**
 * Used on the master to selectively allow specific {@link Callable}s to execute on the master
 * even when those {@link Callable}s do not have proper {@link Role} declarations from its
 * {@link Callable#checkRoles(RoleChecker)} method.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.THU
 */
public abstract class CallableWhitelist implements ExtensionPoint {
    /**
     * Returns true if given {@code subject} should be allowed to execute on the master even though
     * it came over channel from other JVMs.
     *
     * @param subject
     *      See {@link RoleChecker#check(RoleSensitive, Collection)}
     * @param expected
     *      See {@link RoleChecker#check(RoleSensitive, Collection)}
     * @param context
     *      Parameter given to {@link ChannelConfigurator#onChannelBuilding(ChannelBuilder, Object)}
     */
    public abstract boolean isWhitelisted(RoleSensitive subject, Collection<Role> expected, Object context);

    public static ExtensionList<CallableWhitelist> all() {
        return Jenkins.getInstance().getExtensionList(CallableWhitelist.class);
    }
}
