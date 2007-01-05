package hudson.model.listeners;

import hudson.model.AbstractBuild;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.Hudson;
import hudson.scm.ChangeLogSet;
import hudson.scm.SCM;
import hudson.ExtensionPoint;

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
public abstract class SCMListener implements ExtensionPoint {
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
     * If a build failed before we successfully determine changelog, this method
     * will not be invoked (for example, if "cvs update" failed.) OTOH, this method
     * is invoked before the actual build (like ant invocation) happens. 
     *
     * <p>
     * This is an opportunity for SCM-related plugins to act on changelog.
     * A typical usage includes parsing commit messages and do cross-referencing
     * between other systems. Implementations can also contribute {@link Action}
     * to {@link AbstractBuild} (by {@code build.getActions().add(...)} to
     * display additional data on build views.
     *
     * <p>
     * TODO: once we have cvsnews plugin, refer to its usage.
     *
     * @param build
     *      The build in progress, which just finished determining changelog.
     *      At this point this build is still in progress. Never null.
     * @param listener
     *      {@link BuildListener} for on-going build. This can be used to report
     *      any errors or the general logging of what's going on. This will show
     *      up in the "console output" of the build. Never null.
     * @param changelog
     *      Set of changes detected in this build. This is the same value
     *      returned from {@link AbstractBuild#getChangeSet()} but passed
     *      separately for convenience.
     *
     * @throws Exception
     *      If any exception is thrown from this method, it will be recorded
     *      and causes the build to fail. 
     */
    public void onChangeLogParsed(AbstractBuild<?,?> build, BuildListener listener, ChangeLogSet<?> changelog) throws Exception {
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
