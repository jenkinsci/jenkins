package hudson.model.listeners;

import hudson.ExtensionPoint;
import hudson.model.Hudson;
import hudson.model.Item;

/**
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
}
