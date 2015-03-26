/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.model;

import hudson.util.AdaptedIterator;

import java.util.Set;
import java.util.Collection;
import java.util.AbstractCollection;
import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArraySet;
import javax.annotation.Nonnull;
import javax.annotation.concurrent.GuardedBy;

/**
 * Controls mutual exclusion of {@link ResourceList}.
 * @author Kohsuke Kawaguchi
 */
public class ResourceController {
    /**
     * Lock to guard the mutation of {@link #inUse}.
     */
    private final Object lock = new Object();

    /**
     * {@link ResourceList}s that are used by activities that are in progress.
     */
    private final Set<ResourceActivity> inProgress = new CopyOnWriteArraySet<ResourceActivity>();

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
    @GuardedBy("lock")
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
    public void execute(@Nonnull Runnable task, ResourceActivity activity ) throws InterruptedException {
        ResourceList resources = activity.getResourceList();
        synchronized(lock) {
            while(inUse.isCollidingWith(resources))
                lock.wait();

            // we have a go
            inProgress.add(activity);
            inUse = ResourceList.union(inUse,resources);
        }

        try {
            task.run();
        } finally {
            synchronized(lock) {
                inProgress.remove(activity);
                inUse = ResourceList.union(resourceView);
                lock.notifyAll();
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
    public boolean canRun(ResourceList resources) {
        synchronized (lock) {
            return !inUse.isCollidingWith(resources);
        }
    }

    /**
     * Of the resource in the given resource list, return the one that's
     * currently in use.
     *
     * <p>
     * If more than one such resource exists, one is chosen and returned.
     * This method is used for reporting what's causing the blockage.
     */
    public Resource getMissingResource(ResourceList resources) {
        synchronized (lock) {
            return resources.getConflict(inUse);
        }
    }

    /**
     * Of the activities that are in progress, return one that's blocking
     * the given activity, or null if it's not blocked (and thus the
     * given activity can be executed immediately.)
     */
    public ResourceActivity getBlockingActivity(ResourceActivity activity) {
        ResourceList res = activity.getResourceList();
        for (ResourceActivity a : inProgress)
            if(res.isCollidingWith(a.getResourceList()))
                return a;
        return null;
    }
}

