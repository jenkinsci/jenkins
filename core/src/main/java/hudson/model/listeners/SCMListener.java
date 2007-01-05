package hudson.model.listeners;

import hudson.model.AbstractBuild;
import hudson.model.Hudson;
import hudson.scm.SCM;
import hudson.scm.ChangeLogSet;

/**
 * Receives notifications about SCM activities in Hudson.
 *
 * <p>
 * This is an abstract class so that methods added in the future won't break existing listeners.
 *
 * <p>
 * Once instanciated, use the {@link #register()} method to start receiving events. 
 *
 * @author Kohsuke Kawaguchi
 * @see Hudson#getSCMListeners()
 * @since 1.70
 */
public abstract class SCMListener {
    /**
     * Called once the changelog is determined.
     *
     * <p>
     * During a build, Hudson fetches the update of the workspace from SCM,
     * and determines the changelog (see {@link SCM#checkout}). Immediately
     * after that, a build will invoke this method on all registered
     * {@link SCMListener}s.
     *
     * <p>
     * This is an opportunity for SCM-related plugins to act on changelog.
     * A typical usage includes parsing commit messages and do cross-referencing
     * between other systems.
     *
     * <p>
     * If a build failed before we successfully determine changelog, this method
     * will not be invoked (for example, if "cvs update" failed.) OTOH, this method
     * is invoked before the actual build (like ant invocation) happens. 
     *
     * <p>
     * TODO: once we have cvsnews plugin, refer to its usage.
     *
     * @param build
     *      The build in progress, which just finished determining changelog.
     *      At this point this build is still in progress. Never null.
     * @param changelog
     *      Set of changes detected in this build. This is the same value
     *      returned from {@link AbstractBuild#getChangeSet()} but passed
     *      separately for convenience.
     */
    public void onChangeLogParsed(AbstractBuild<?,?> build, ChangeLogSet<?> changelog) {
    }

    /**
     * Registers this {@link SCMListener} so that it will start receiving events.
     */
    public final void register() {
        Hudson.getInstance().getSCMListeners().add(this);
    }

    /**
     * Unregisters this {@link SCMListener} so that it will never receive further events.
     *
     * <p>
     * Unless {@link SCMListener} is unregistered, it will never be a subject of GC.
     */
    public final boolean unregister() {
        return Hudson.getInstance().getSCMListeners().remove(this);
    }
}
