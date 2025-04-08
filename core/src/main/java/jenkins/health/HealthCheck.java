package jenkins.health;

import hudson.ExtensionPoint;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;

/**
 * Defines a health check that is critical to the operation of Jenkins.
 * @since XXX
 */
@Restricted(Beta.class)
public interface HealthCheck extends ExtensionPoint {

    /**
     * @return the name of the health check. Must be unique among health check implementations.
     */
    default String getName() {
        return getClass().getName();
    }

    /**
     * @return true if the health check passed.
     */
    boolean check();
}
