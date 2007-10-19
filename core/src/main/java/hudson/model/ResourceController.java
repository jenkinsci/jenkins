package hudson.model;

import hudson.model.Queue.Task;
import hudson.util.AdaptedIterator;

import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Collection;
import java.util.AbstractCollection;
import java.util.Iterator;

import org.apache.commons.collections.iterators.FilterIterator;

/**
 * Controls mutual exclusion of {@link ResourceList}.
 * @author Kohsuke Kawaguchi
 */
public class ResourceController {
    /**
     * {@link ResourceList}s that are used by activities that are in progress.
     */
    private final Set<ResourceActivity> inProgress = new HashSet<ResourceActivity>();

    /**
     * View of {@link #inProgress} that exposes its {@link ResourceList}.
     */
    private final Collection<ResourceList> resourceView = new AbstractCollection<ResourceList>() {
        public Iterator<ResourceList> iterator() {
            return new AdaptedIterator<ResourceActivity,ResourceList>(inProgress.iterator()) {
                protected ResourceList adapt(ResourceActivity item) {
                    return item.getResourceList();
                }
            };
        }

        public int size() {
            return inProgress.size();
        }
    };

    /**
     * Union of all {@link Resource}s that are currently in use.
     * Updated as a task starts/completes executing. 
     */
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
    public void execute( Runnable task, ResourceActivity activity ) throws InterruptedException {
        ResourceList resources = activity.getResourceList();
        synchronized(this) {
            while(inUse.isCollidingWith(resources))
                wait();

            // we have a go
            inProgress.add(activity);
            inUse = ResourceList.union(inUse,resources);
        }

        try {
            task.run();
        } finally {
            synchronized(this) {
                inProgress.remove(activity);
                inUse = ResourceList.union(resourceView);
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
     * gets to call {@link #execute(Runnable, ResourceActivity)}.
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

    /**
     * Of the activities that are in progress, return one that's blocking
     * the given activity, or null if it's not blocked (and thus the
     * given activity can be executed immediately.)
     */
    public synchronized ResourceActivity getBlockingActivity(ResourceActivity activity) {
        ResourceList res = activity.getResourceList();
        for (ResourceActivity a : inProgress)
            if(res.isCollidingWith(a.getResourceList()))
                return a;
        return null;
    }
}

