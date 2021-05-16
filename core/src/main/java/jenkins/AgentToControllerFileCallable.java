package jenkins;

import hudson.FilePath;
import jenkins.security.Roles;
import org.jenkinsci.remoting.RoleChecker;

/**
 * {@link FilePath.FileCallable}s that can be executed on the controller, sent by the agent.
 * Note that any serializable fields must either be defined in your plugin or included in the stock JEP-200 whitelist.
 * @since TODO
 */
public abstract class AgentToControllerFileCallable<T> implements FilePath.FileCallable<T> {
    private static final long serialVersionUID = 1L;

    @Override
    public void checkRoles(RoleChecker checker) throws SecurityException {
        checker.check(this, Roles.CONTROLLER);
    }
}
