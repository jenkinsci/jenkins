package hudson.model.listeners;

import hudson.model.Job;
import hudson.model.Hudson;

/**
 * Receives notifications about jobs.
 *
 * <p>
 * This is an abstract class so that methods added in the future won't break existing listeners.
 *
 * @author Kohsuke Kawaguchi
 * @see Hudson#addListener(JobListener)
 */
public abstract class JobListener {
    /**
     * Called after a new job is created and added to {@link Hudson}.
     */
    public void onCreated(Job j) {
    }

    /**
     * Called right before a job is going to be deleted.
     *
     * At this point the data files of the job is already gone.
     */
    public void onDeleted(Job j) {
    }
}
