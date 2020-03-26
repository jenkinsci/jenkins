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

import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.Action;
import hudson.model.Item;
import hudson.model.Queue;
import hudson.model.queue.Tasks;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import net.jcip.annotations.GuardedBy;

/**
 * A {@link Queue.QueueDecisionHandler} that blocks items being deleted from entering the queue.
 *
 * @since 2.55
 */
@Extension
public class ItemDeletion extends Queue.QueueDecisionHandler {

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
}
