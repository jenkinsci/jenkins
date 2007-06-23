package hudson.model;

import hudson.scm.SCM;
import hudson.triggers.SCMTrigger;

/**
 * {@link Item}s that has associated SCM.
 *
 * @author Kohsuke Kawaguchi
 * @see SCMTrigger
 */
public interface SCMedItem extends BuildableItem {
    /**
     * Gets the {@link SCM} for this item.
     *
     * @return
     *      may return null for indicating "no SCM".
     */
    SCM getScm();

    /**
     * {@link SCMedItem} needs to be an instance of
     * {@link AbstractProject}.
     *
     * <p>
     * This method must be always implemented as {@code (AbstractProject)this}, but
     * defining this method emphasizes the fact that this cast must be doable.
     */
    AbstractProject<?,?> asProject();

    /**
     * Checks if there's any update in SCM, and returns true if any is found.
     *
     * <p>
     * The caller is responsible for coordinating the mutual exclusion between
     * a build and polling, as both touches the workspace.
     */
    boolean pollSCMChanges( TaskListener listener );
}
