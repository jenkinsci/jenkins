package jenkins.security;

import org.jenkinsci.remoting.Role;

/**
 * Predefined {@link Role}s in Jenkins.
 *
 * <p>
 * In Jenkins, there is really only one interesting role, which is the Jenkins master.
 * Slaves, CLI, and Maven processes are all going to load classes from the master,
 * which means it accepts anything that the master asks for, and thus they need
 * not have any role.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.THU
 */
public class Roles {
    /**
     * Indicates that a callable runs on masters, requested by slaves/CLI/maven/whatever.
     */
    public static final Role MASTER = new Role("master");

    /**
     * Indicates that a callable is meant to run on slaves.
     *
     * This isn't used to reject callables to run on the slave, but rather to allow
     * the master to promptly reject callables that are really not meant to be run on
     * the master (as opposed to ones that do not have that information, which gets
     * {@link Role#UNKNOWN})
     */
    public static final Role SLAVE = new Role("slave");

    private Roles() {}
}
