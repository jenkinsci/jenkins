package hudson.model;

import hudson.ExtensionPoint;
import hudson.Plugin;

/**
 * Extensible property of {@link User}.
 *
 * <p>
 * {@link Plugin}s can extend this to define custom properties
 * for {@link User}s. {@link UserProperty}s show up in the user
 * configuration screen, and they are persisted with the user object.
 *
 * @author Kohsuke Kawaguchi
 * @see UserProperties#LIST
 */
public abstract class UserProperty implements Describable<UserProperty>, ExtensionPoint {
    /**
     * The user object that owns this property.
     * This value will be set by the Hudson code.
     * Derived classes can expect this value to be always set.
     */
    protected transient User user;

    /*package*/ final void setUser(User u) {
        this.user = u;
    }
}
