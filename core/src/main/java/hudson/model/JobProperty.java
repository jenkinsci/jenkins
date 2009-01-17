package hudson.model;

import hudson.ExtensionPoint;
import hudson.Launcher;
import hudson.Plugin;
import hudson.tasks.BuildStep;
import hudson.tasks.Builder;
import hudson.tasks.Publisher;

import java.io.IOException;

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
 * <p>
 * Starting 1.150, {@link JobProperty} implements {@link BuildStep},
 * meaning it gets the same hook as {@link Publisher} and {@link Builder}.
 * The primary intention of this mechanism is so that {@link JobProperty}s
 * can add actions to the new build. The {@link #perform(AbstractBuild, Launcher, BuildListener)}
 * and {@link #prebuild(AbstractBuild, BuildListener)} are invoked after those
 * of {@link Publisher}s.
 *
 * @param <J>
 *      When you restrict your job property to be only applicable to a certain
 *      subtype of {@link Job}, you can use this type parameter to improve
 *      the type signature of this class. See {@link JobPropertyDescriptor#isApplicable(Class)}. 
 *
 * @author Kohsuke Kawaguchi
 * @see JobPropertyDescriptor
 * @since 1.72
 */
public abstract class JobProperty<J extends Job<?,?>> implements Describable<JobProperty<?>>, BuildStep, ExtensionPoint {
    /**
     * The {@link Job} object that owns this property.
     * This value will be set by the Hudson code.
     * Derived classes can expect this value to be always set.
     */
    protected transient J owner;

    /**
     * Hook for performing post-initialization action.
     *
     * <p>
     * This method is invoked in two cases. One is when the {@link Job} that owns
     * this property is loaded from disk, and the other is when a job is re-configured
     * and all the {@link JobProperty} instances got re-created.
     */
    protected void setOwner(J owner) {
        this.owner = owner;
    }

    /**
     * {@inheritDoc}
     */
    public abstract JobPropertyDescriptor getDescriptor();

    /**
     * {@link Action} to be displayed in the job page.
     *
     * <p>
     * Returning non-null from this method allows a job property to add an item
     * to the left navigation bar in the job page.
     *
     * <p>
     * {@link Action} can implement additional marker interface to integrate
     * with the UI in different ways.
     *
     * @param job
     *      Always the same as {@link #owner} but passed in anyway for backward compatibility (I guess.)
     *      You really need not use this value at all.
     * @return
     *      null if there's no such action.
     * @since 1.102
     * @see ProminentProjectAction
     * @see PermalinkProjectAction
     */
    public Action getJobAction(J job) {
        return null;
    }

//
// default no-op implementation
//

    public boolean prebuild(AbstractBuild<?,?> build, BuildListener listener) {
        return true;
    }

    public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        return true;
    }

    public final Action getProjectAction(AbstractProject<?,?> project) {
        return getJobAction((J)project);
    }
}
