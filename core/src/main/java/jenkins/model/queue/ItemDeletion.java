/*
 * The MIT License
 *
 * Copyright (c) 2017 CloudBees, Inc.
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

package jenkins.model.queue;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.AbstractItem;
import hudson.model.Action;
import hudson.model.Computer;
import hudson.model.Executor;
import hudson.model.Failure;
import hudson.model.Item;
import hudson.model.Messages;
import hudson.model.Queue;
import hudson.model.Result;
import hudson.model.queue.Executables;
import hudson.model.queue.SubTask;
import hudson.model.queue.Tasks;
import hudson.model.queue.WorkUnit;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import net.jcip.annotations.GuardedBy;

/**
 * A {@link Queue.QueueDecisionHandler} that blocks items being deleted from entering the queue.
 * @see AbstractItem#delete()
 * @since 2.55
 */
@Extension
public class ItemDeletion extends Queue.QueueDecisionHandler {

    private static final Logger LOGGER = Logger.getLogger(ItemDeletion.class.getName());

    /**
     * Lock to guard the {@link #registrations} set.
     */
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    /**
     * The explicit deletions in progress.
     */
    @GuardedBy("lock")
    private final Set<Item> registrations = new HashSet<>();

    @GuardedBy("lock")
    private boolean _contains(@NonNull Item item) {
        if (registrations.isEmpty()) {
            // no point walking everything if there is nothing in-flight
            return false;
        }

        while (item != null) {
            if (registrations.contains(item)) {
                return true;
            }
            if (item.getParent() instanceof Item) {
                item = (Item) item.getParent();
            } else {
                break;
            }
        }
        return false;
    }

    /**
     * Checks if the supplied {@link Item} or any of its {@link Item#getParent()} are being deleted.
     *
     * @param item the item.
     * @return {@code true} if the {@link Item} or any of its {@link Item#getParent()} are being deleted.
     */
    public static boolean contains(@NonNull Item item) {
        ItemDeletion instance = instance();
        if (instance == null) {
            return false;
        }
        instance.lock.readLock().lock();
        try {
            return instance._contains(item);
        } finally {
            instance.lock.readLock().unlock();
        }
    }

    /**
     * Checks if the supplied {@link Item} is explicitly registered for deletion.
     *
     * @param item the item.
     * @return {@code true} if and only if the supplied {@link Item} has been {@linkplain #register(Item)}ed for
     * deletion.
     */
    public static boolean isRegistered(@NonNull Item item) {
        ItemDeletion instance = instance();
        if (instance == null) {
            return false;
        }
        instance.lock.readLock().lock();
        try {
            return instance.registrations.contains(item);
        } finally {
            instance.lock.readLock().unlock();
        }
    }

    /**
     * Register the supplied {@link Item} for deletion.
     *
     * @param item the {@link Item} that is to be deleted.
     * @return {@code true} if and only if the {@link Item} was registered and the caller is now responsible to call
     * {@link #deregister(Item)}.
     */
    public static boolean register(@NonNull Item item) {
        ItemDeletion instance = instance();
        if (instance == null) {
            return false;
        }
        instance.lock.writeLock().lock();
        try {
            return instance.registrations.add(item);
        } finally {
            instance.lock.writeLock().unlock();
        }
    }

    /**
     * Deregister the supplied {@link Item} for deletion.
     *
     * @param item the {@link Item} that was to be deleted and is now either deleted or the delete was aborted.
     */
    public static void deregister(@NonNull Item item) {
        ItemDeletion instance = instance();
        if (instance != null) {
            instance.lock.writeLock().lock();
            try {
                instance.registrations.remove(item);
            } finally {
                instance.lock.writeLock().unlock();
            }
        }
    }

    /**
     * Gets the singleton instance.
     *
     * @return the {@link ItemDeletion} singleton.
     */
    @CheckForNull
    private static ItemDeletion instance() {
        return ExtensionList.lookup(Queue.QueueDecisionHandler.class).get(ItemDeletion.class);
    }

    @Override
    public boolean shouldSchedule(Queue.Task p, List<Action> actions) {
        Item item = Tasks.getItemOf(p);
        if (item != null) {
            lock.readLock().lock();
            try {
                return !_contains(item);
            } finally {
                lock.readLock().unlock();
            }
        }
        return true;
    }

    /**
     * Cancels any builds in progress of this item (if a job) or descendants (if a folder).
     * Also cancels any associated queue items.
     * @param initiatingItem an item being deleted
     * @since 2.470
     */
    public static void cancelBuildsInProgress(@NonNull Item initiatingItem) throws Failure, InterruptedException {
        Queue queue = Queue.getInstance();
        if (initiatingItem instanceof Queue.Task) {
            // clear any items in the queue so they do not get picked up
            queue.cancel((Queue.Task) initiatingItem);
        }
        // now cancel any child items - this happens after ItemDeletion registration, so we can use a snapshot
        for (Queue.Item i : queue.getItems()) {
            Item item = Tasks.getItemOf(i.task);
            while (item != null) {
                if (item == initiatingItem) {
                    if (!queue.cancel(i)) {
                        LOGGER.warning(() -> "failed to cancel " + i);
                    }
                    break;
                }
                if (item.getParent() instanceof Item) {
                    item = (Item) item.getParent();
                } else {
                    break;
                }
            }
        }
        // interrupt any builds in progress (and this should be a recursive test so that folders do not pay
        // the 15 second delay for every child item). This happens after queue cancellation, so will be
        // a complete set of builds in flight
        Map<Executor, Queue.Executable> buildsInProgress = new LinkedHashMap<>();
        for (Computer c : Jenkins.get().getComputers()) {
            for (Executor e : c.getAllExecutors()) {
                final WorkUnit workUnit = e.getCurrentWorkUnit();
                final Queue.Executable executable = workUnit != null ? workUnit.getExecutable() : null;
                final SubTask subtask = executable != null ? Executables.getParentOf(executable) : null;
                if (subtask != null) {
                    Item item = Tasks.getItemOf(subtask);
                    while (item != null) {
                        if (item == initiatingItem) {
                            buildsInProgress.put(e, e.getCurrentExecutable());
                            e.interrupt(Result.ABORTED);
                            break;
                        }
                        if (item.getParent() instanceof Item) {
                            item = (Item) item.getParent();
                        } else {
                            break;
                        }
                    }
                }
            }
        }
        if (!buildsInProgress.isEmpty()) {
            // give them 15 seconds or so to respond to the interrupt
            long expiration = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
            // comparison with executor.getCurrentExecutable() == computation currently should always be true
            // as we no longer recycle Executors, but safer to future-proof in case we ever revisit recycling
            while (!buildsInProgress.isEmpty() && expiration - System.nanoTime() > 0L) {
                // we know that ItemDeletion will prevent any new builds in the queue
                // ItemDeletion happens-before Queue.cancel so we know that the Queue will stay clear
                // Queue.cancel happens-before collecting the buildsInProgress list
                // thus buildsInProgress contains the complete set we need to interrupt and wait for
                for (Iterator<Map.Entry<Executor, Queue.Executable>> iterator =
                     buildsInProgress.entrySet().iterator();
                     iterator.hasNext(); ) {
                    Map.Entry<Executor, Queue.Executable> entry = iterator.next();
                    // comparison with executor.getCurrentExecutable() == executable currently should always be
                    // true as we no longer recycle Executors, but safer to future-proof in case we ever
                    // revisit recycling.
                    if (!entry.getKey().isAlive()
                            || entry.getValue() != entry.getKey().getCurrentExecutable()) {
                        iterator.remove();
                    }
                    // I don't know why, but we have to keep interrupting
                    entry.getKey().interrupt(Result.ABORTED);
                }
                Thread.sleep(50L);
            }
            if (!buildsInProgress.isEmpty()) {
                throw new Failure(Messages.AbstractItem_FailureToStopBuilds(
                        buildsInProgress.size(), initiatingItem.getFullDisplayName()
                ));
            }
        }
    }

}
