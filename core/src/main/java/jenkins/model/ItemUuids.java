/*
 * The MIT License
 *
 * Copyright (c) 2026, Jenkins project contributors
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

package jenkins.model;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.model.AbstractItem;
import hudson.model.Item;
import hudson.model.listeners.ItemListener;
import hudson.security.ACL;
import hudson.security.ACLContext;
import java.io.IOException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.util.SystemProperties;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.springframework.security.access.AccessDeniedException;

/**
 * Finds items by {@link Item#getItemUuid}, the identity renames and moves do not change, and pins that identity.
 *
 * @since TODO
 */
@Restricted(Beta.class)
public final class ItemUuids {

    private static final Logger LOGGER = Logger.getLogger(ItemUuids.class.getName());

    /**
     * Whether new items (created, copied or imported) are pinned from the start, so that each has an identity of its own
     * saved with it, rather than one derived from its name until something pins it.
     */
    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "for script console and tests")
    @Restricted(NoExternalUse.class)
    public static boolean PIN_NEW_ITEMS = SystemProperties.getBoolean(ItemUuids.class.getName() + ".pinNewItems");

    /**
     * The items loaded here by identity. Kept by item rather than by name, so that an item keeps its identity while
     * another takes its old name (or the same name in another case), and a reloaded item's object replaces the old one.
     */
    private static final Map<String, Item> items = new ConcurrentHashMap<>();

    /** The identity each item is indexed under, so that indexing one under a new one is not a scan. Guarded by {@link #lock}. */
    private static final Map<Item, String> indexed = new IdentityHashMap<>();

    /**
     * Held while an identity is checked and indexed, so that two items cannot both take one. Taken after an item's own
     * lock (as when configuration is submitted), never before it, so an item is not saved while it is held.
     */
    private static final Object lock = new Object();

    private ItemUuids() {}

    /**
     * The item with this identity, if it is loaded here and the current user may see it.
     * Answers from an in-memory index (no I/O).
     *
     * @return the item, or null (unknown here, not loaded yet, or not visible)
     */
    @CheckForNull
    public static Item get(@NonNull String uuid) {
        Item item = items.get(uuid);
        if (item == null || !uuid.equals(item.getItemUuid())) {
            return null;
        }
        try {
            return Jenkins.get().getItemByFullName(item.getFullName()) == item ? item : null;
        } catch (AccessDeniedException e) {
            return null; // may be discovered but not read
        }
    }

    /**
     * Pins an item's identity: gives an unpinned item an identity of its own, a new UUID that renames and moves do not
     * change, saved with the item. The identity an unpinned item derives from its name is not kept, as it would also be
     * that of a later item of the same name (once this one is deleted or renamed): data kept elsewhere about an item
     * should be keyed only by its pinned identity. A pinned item keeps its identity, unless another item has it (as
     * configuration posted with another item's, not yet checked), when it is given a new one too. Saves the item if it
     * gets an identity; does nothing otherwise. Every new item is pinned already where {@link #pinsNewItems} holds.
     *
     * @return the pinned identity
     * @throws IllegalArgumentException if this kind of item has no identity
     */
    @NonNull
    public static String pin(@NonNull Item item) throws IOException {
        if (item instanceof AbstractItem ai) {
            synchronized (ai) { // so it is pinned once; before the index lock, as everywhere
                String was = ai.isItemUuidPinned() ? ai.getItemUuid() : null;
                String id;
                try (ACLContext ctx = ACL.as2(ACL.SYSTEM2)) {
                    synchronized (lock) {
                        if (was != null && !heldByAnother(was, ai)) {
                            index(ai);
                            return was; // saved with it already
                        }
                        id = UUID.randomUUID().toString();
                        ai.setItemUuid(id);
                        index(ai);
                    }
                }
                try {
                    ai.save();
                } catch (IOException | RuntimeException e) {
                    try (ACLContext ctx = ACL.as2(ACL.SYSTEM2)) {
                        synchronized (lock) {
                            ai.setItemUuid(was); // as it was, and as it is saved
                            unindex(ai);
                            if (!heldByAnother(ai.getItemUuid(), ai)) {
                                index(ai);
                            }
                        }
                    }
                    throw e;
                }
                return id;
            }
        }
        String id = item.getItemUuid();
        if (id == null) {
            throw new IllegalArgumentException(item.getFullName() + " has no identity");
        }
        return id; // its own kind of identity: kept as that kind keeps it
    }

    /**
     * Whether a new item is pinned from the start: created, copied or imported, its configuration is first written
     * with an identity of its own.
     */
    @Restricted(NoExternalUse.class)
    public static boolean pinsNewItems() {
        return PIN_NEW_ITEMS;
    }

    /**
     * Indexes every item loaded here again, as at startup, giving one that has another's identity one of its own.
     * For a reload of the whole configuration, which makes new objects of the items and fires no {@link ItemListener}.
     */
    @Restricted(NoExternalUse.class)
    public static void reindex() {
        List<AbstractItem> renewed = new ArrayList<>();
        try (ACLContext ctx = ACL.as2(ACL.SYSTEM2)) {
            List<Item> all = new ArrayList<>();
            Jenkins.get().allItems().forEach(all::add);
            synchronized (lock) {
                items.clear();
                indexed.clear();
                // a pinned identity is kept by the item it was pinned for, even if it was derived from a name another
                // item has now (as when an item is pinned, renamed, and another made with its old name); of two pinned
                // with the same identity (as when a job's directory is copied), the first found keeps it
                for (Item item : all) {
                    if (isPinned(item) && claimLocked(item)) {
                        renewed.add((AbstractItem) item);
                    }
                }
                for (Item item : all) {
                    if (!isPinned(item) && claimLocked(item)) {
                        renewed.add((AbstractItem) item);
                    }
                }
            }
        }
        renewed.forEach(ItemUuids::save);
    }

    /** Indexes an item reloaded from disk, whose identity may have changed there. */
    @Restricted(NoExternalUse.class)
    public static void reloaded(@NonNull Item item) {
        claim(item);
    }

    /** Indexes an item, after giving it an identity of its own if it has another item's. */
    private static void claim(Item item) {
        boolean renewed;
        try (ACLContext ctx = ACL.as2(ACL.SYSTEM2)) {
            synchronized (lock) {
                renewed = claimLocked(item);
            }
        }
        if (renewed) {
            save((AbstractItem) item);
        }
    }

    /**
     * Indexes an item, first giving it an identity of its own if another item has its identity.
     *
     * @return whether it was given one, and so must be saved
     */
    private static boolean claimLocked(Item item) {
        String id = item.getItemUuid();
        if (id == null) {
            return false;
        }
        if (!heldByAnother(id, item)) {
            index(item);
            return false;
        }
        if (item instanceof AbstractItem ai) {
            LOGGER.log(ai.isItemUuidPinned() ? Level.WARNING : Level.FINE, "{0} has the identity of {1}; giving it one of its own",
                    new Object[] {item.getFullName(), items.get(id).getFullName()});
            ai.newItemUuid();
            index(item);
            return true;
        }
        return false; // not taken from the item that has it
    }

    /**
     * Whether another item loaded here has this identity: whoever made the change, and whatever they may see, the
     * identity of an item hidden from them is not theirs. Call with {@link #lock} held, as {@link ACL#SYSTEM2}.
     */
    private static boolean heldByAnother(String id, Item item) {
        Item holder = items.get(id);
        return holder != null && holder != item && id.equals(holder.getItemUuid())
                && Jenkins.get().getItemByFullName(holder.getFullName()) == holder;
    }

    /** Indexes an item under its identity, and no longer under any other. Call with {@link #lock} held. */
    private static void index(Item item) {
        String id = item.getItemUuid();
        if (id == null) {
            return;
        }
        String old = indexed.put(item, id);
        if (old != null && !old.equals(id)) {
            items.remove(old, item);
        }
        items.put(id, item);
    }

    /** No longer indexes an item. Call with {@link #lock} held. */
    private static void unindex(Item item) {
        String old = indexed.remove(item);
        if (old != null) {
            items.remove(old, item);
        }
    }

    private static void save(AbstractItem item) {
        try {
            item.save();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Could not save the new identity of " + item.getFullName(), e);
        }
    }

    private static boolean isPinned(Item item) {
        return !(item instanceof AbstractItem ai) || ai.isItemUuidPinned();
    }

    /** Keeps the index, and gives an item that has another's identity (as from posted configuration) one of its own. */
    @Restricted(NoExternalUse.class)
    @Extension
    public static final class Listener extends ItemListener {
        @Override
        public void onLoaded() {
            reindex();
        }

        @Override
        public void onCreated(Item item) {
            claim(item);
        }

        @Override
        public void onUpdated(Item item) {
            claim(item); // as for configuration posted with another item's identity
        }

        @Override
        public void onLocationChanged(Item item, String oldFullName, String newFullName) {
            claim(item); // under its new derived identity if it is not pinned
        }

        @Override
        public void onDeleted(Item item) {
            synchronized (lock) {
                String prefix = item.getFullName() + '/';
                try (ACLContext ctx = ACL.as2(ACL.SYSTEM2)) {
                    unindex(item);
                    // and the items in it, which are not told
                    for (Item i : new ArrayList<>(indexed.keySet())) {
                        if (i.getFullName().startsWith(prefix) && Jenkins.get().getItemByFullName(i.getFullName()) != i) {
                            unindex(i);
                        }
                    }
                }
            }
        }
    }
}
