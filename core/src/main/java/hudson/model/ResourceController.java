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

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.util.AdaptedIterator;
import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import jenkins.util.ThrowingCallable;
import jenkins.util.ThrowingRunnable;

/**
 * Controls mutual exclusion of {@link ResourceList}.
 * @author Kohsuke Kawaguchi
 */
public class ResourceController {
    /**
     * {@link ResourceList}s that are used by activities that are in progress.
     */
    private final Set<ResourceActivity> inProgress = new CopyOnWriteArraySet<>();

    /**
     * View of {@link #inProgress} that exposes its {@link ResourceList}.
     */
    private final Collection<ResourceList> resourceView = new AbstractCollection<>() {
        @Override
        public Iterator<ResourceList> iterator() {
            return new AdaptedIterator<>(inProgress.iterator()) {
                @Override
                protected ResourceList adapt(ResourceActivity item) {
                    return item.getResourceList();
                }
            };
        }

        @Override
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
    public void execute(@NonNull Runnable task, final ResourceActivity activity) throws InterruptedException {
        final ResourceList resources = activity.getResourceList();
        _runWithLock(() -> {
            while (inUse.isCollidingWith(resources)) {
                // TODO revalidate the resource list after re-acquiring lock, for now we just let the build fail
                _await();
            }

            // we have a go
            inProgress.add(activity);
            inUse = ResourceList.union(inUse, resources);
        });

        try {
            task.run();
        } finally {
           // TODO if AsynchronousExecution, do that later
            _runWithLock(() -> {
                inProgress.remove(activity);
                inUse = ResourceList.union(resourceView);
                _signalAll();
            });
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
    public boolean canRun(final ResourceList resources) {
        return _callWithLock(() -> !inUse.isCollidingWith(resources));
    }

    /**
     * Of the resource in the given resource list, return the one that's
     * currently in use.
     *
     * <p>
     * If more than one such resource exists, one is chosen and returned.
     * This method is used for reporting what's causing the blockage.
     */
    public Resource getMissingResource(final ResourceList resources) {
        return _callWithLock(() -> resources.getConflict(inUse));
    }

    /**
     * Of the activities that are in progress, return one that's blocking
     * the given activity, or null if it's not blocked (and thus the
     * given activity can be executed immediately.)
     */
    public ResourceActivity getBlockingActivity(ResourceActivity activity) {
        ResourceList res = activity.getResourceList();
        for (ResourceActivity a : inProgress)
            if (res.isCollidingWith(a.getResourceList()))
                return a;
        return null;
    }

    @SuppressFBWarnings(value = "WA_NOT_IN_LOOP", justification = "the caller does indeed call this method in a loop")
    protected void _await() throws InterruptedException {
        wait();
    }

    protected void _signalAll() {
        notifyAll();
    }

    protected <T extends Throwable> void _runWithLock(ThrowingRunnable<T> runnable) throws T {
        synchronized (this) {
            runnable.run();
        }
    }

    protected void _withLock(Runnable runnable) {
        synchronized (this) {
            runnable.run();
        }
    }

    protected <V, T extends Throwable> V _callWithLock(ThrowingCallable<V, T> callable) throws T {
        synchronized (this) {
            return callable.call();
        }
    }

    protected <V> V _withLock(java.util.concurrent.Callable<V> callable) throws Exception {
        synchronized (this) {
            return callable.call();
        }
    }

    protected <V, T extends Throwable> V _withLock(hudson.remoting.Callable<V, T> callable) throws T {
        synchronized (this) {
            return callable.call();
        }
    }
}
