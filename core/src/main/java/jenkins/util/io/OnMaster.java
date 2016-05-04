package jenkins.util.io;

/**
 * Marks the objects in Jenkins that only exist in the core
 * and not on agents.
 *
 * <p>
 * This marker interface is for plugin developers to quickly
 * tell if they can take a specific object from a master to
 * an agent.
 *
 * (Core developers, if you find classes/interfaces that extend
 * from this, please be encouraged to add them.)
 *
 * @author Kohsuke Kawaguchi
 * @since 1.475
 */
public interface OnMaster {
}
