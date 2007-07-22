package hudson.model;

import java.util.Set;
import java.util.HashSet;

/**
 * Controls mutual exclusion of {@link ResourceList}.
 * @author Kohsuke Kawaguchi
 */
public class ResourceController {
    /**
     * {@link ResourceList}s that are used by activities that are in progress.
     */
    private final Set<ResourceList> inProgress = new HashSet<ResourceList>();

    private ResourceList inUse = ResourceList.EMPTY;

    /**
     * Performs the task that requires the given list of resources.
     *
     * <p>
     * The execution is blocked until the resource is available.
     *
     * @throws InterruptedException
     *      the thread can be interrupted while waiting for the available resources.
     */
    public void execute( Runnable task, ResourceList resources ) throws InterruptedException {
        synchronized(this) {
            while(inUse.isCollidingWith(resources))
                wait();

            // we have a go
            inProgress.add(resources);
            inUse = ResourceList.union(inUse,resources);
        }

        try {
            task.run();
        } finally {
            synchronized(this) {
                inProgress.remove(resources);
                inUse = ResourceList.union(inProgress);
                notifyAll();
            }
        }
    }

    /**
     * Checks if an activity that requires the given resource list
     * can run immediately.
     *
     * <p>
     * This method is really only useful as a hint, since
     * another activity might acquire resources before the caller
     * gets to call {@link #execute(Runnable, ResourceList)}.
     */
    public synchronized boolean canRun(ResourceList resources) {
        return !inUse.isCollidingWith(resources);
    }

    /**
     * Of the resource in the given resource list, return the one that's
     * currently in use.
     *
     * <p>
     * If more than one such resource exists, one is chosen and returned.
     * This method is used for reporting what's causing the blockage.
     */
    public synchronized Resource getMissingResource(ResourceList resources) {
        return resources.getConflict(inUse);
    }
}
