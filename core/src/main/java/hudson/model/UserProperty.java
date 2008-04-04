package hudson.model;

import hudson.ExtensionPoint;
import hudson.Plugin;
import hudson.tasks.Mailer;
import org.kohsuke.stapler.export.ExportedBean;

/**
 * Extensible property of {@link User}.
 *
 * <p>
 * {@link Plugin}s can extend this to define custom properties
 * for {@link User}s. {@link UserProperty}s show up in the user
 * configuration screen, and they are persisted with the user object.
 *
 * <p>
 * Configuration screen should be defined in <tt>config.jelly</tt>.
 * Within this page, the {@link UserProperty} instance is available
 * as <tt>instance</tt> variable (while <tt>it</tt> refers to {@link User}.
 * See {@link Mailer.UserProperty}'s <tt>config.jelly</tt> for an example.
 *
 *
 * @author Kohsuke Kawaguchi
 * @see UserProperties#LIST
 */
@ExportedBean
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

    // descriptor must be of the UserPropertyDescriptor type
    public abstract UserPropertyDescriptor getDescriptor();
}
