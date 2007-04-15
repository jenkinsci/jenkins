package hudson.model;

import hudson.ExtensionPoint;
import hudson.Plugin;

/**
 * Extensible property of {@link Job}.
 *
 * <p>
 * {@link Plugin}s can extend this to define custom properties
 * for {@link Job}s. {@link JobProperty}s show up in the user
 * configuration screen, and they are persisted with the job object.
 *
 * <p>
 * Configuration screen should be defined in <tt>config.jelly</tt>.
 * Within this page, the {@link JobProperty} instance is available
 * as <tt>instance</tt> variable (while <tt>it</tt> refers to {@link Job}.
 *
 *
 * @author Kohsuke Kawaguchi
 * @see JobPropertyDescriptor
 * @since 1.72
 */
public abstract class JobProperty<J extends Job<?,?>> implements Describable<JobProperty<?>>, ExtensionPoint {
    /**
     * The {@link Job} object that owns this property.
     * This value will be set by the Hudson code.
     * Derived classes can expect this value to be always set.
     */
    protected transient J owner;

    /*package*/ final void setOwner(J owner) {
        this.owner = owner;
    }

    /**
     * {@inheritDoc}
     */
    public abstract JobPropertyDescriptor getDescriptor();

    /**
     * {@link Action} to be displayed in the job page.
     *
     * @return
     *      null if there's no such action.
     * @since 1.102
     */
    public Action getJobAction(J job) {
        return null;
    }
}
