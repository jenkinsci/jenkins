package jenkins.security;

import org.jenkinsci.remoting.Role;

/**
 * Predefined {@link Role}s in Jenkins.
 *
 * <p>
 * In Jenkins, there is really only one interesting role, which is the Jenkins controller.
 * Agents, CLI, and Maven processes are all going to load classes from the controller,
 * which means it accepts anything that the controller asks for, and thus they need
 * not have any role.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.587 / 1.580.1
 */
public class Roles {
    /**
     * Indicates that a callable runs on controllers, requested by agents/CLI/maven/whatever.
     * @since TODO
     */
    public static final Role CONTROLLER = new Role("controller");

    /**
     * @deprecated Use {@link #CONTROLLER}
     */
    @Deprecated
    public static final Role MASTER = CONTROLLER;

    /**
     * Indicates that a callable is meant to run on agents.
     *
     * This isn't used to reject callables to run on the agent, but rather to allow
     * the controller to promptly reject callables that are really not meant to be run on
     * the controller (as opposed to ones that do not have that information, which gets
     * {@link Role#UNKNOWN})
     *
     * @since TODO
     */
    public static final Role AGENT = new Role("agent");

    /**
     * @deprecated Use {@link #AGENT}
     */
    @Deprecated
    public static final Role SLAVE = AGENT;

    private Roles() {}
}
