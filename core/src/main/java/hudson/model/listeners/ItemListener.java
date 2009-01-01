package hudson.model.listeners;

import hudson.ExtensionPoint;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.model.Job;

/**
 * Receives notifications about CRUD operations of {@link Item}.
 *
 * @since 1.74
 * @author Kohsuke Kawaguchi
 */
public class ItemListener implements ExtensionPoint {
    /**
     * Called after a new job is created and added to {@link Hudson}.
     */
    public void onCreated(Item item) {
    }

    /**
     * Called after all the jobs are loaded from disk into {@link Hudson}
     * object.
     */
    public void onLoaded() {
    }

    /**
     * Called right before a job is going to be deleted.
     *
     * At this point the data files of the job is already gone.
     */
    public void onDeleted(Item item) {
    }

    /**
     * Called after a job is renamed.
     *
     * @param item
     *      The job being renamed.
     * @param oldName
     *      The old name of the job.
     * @param newName
     *      The new name of the job. Same as {@link Item#getName()}.
     * @since 1.146
     */
    public void onRenamed(Item item, String oldName, String newName) {
    }
}
