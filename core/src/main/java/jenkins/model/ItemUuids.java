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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.util.SystemProperties;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;
import org.kohsuke.accmod.restrictions.NoExternalUse;

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

    /** Full names by identity, for the items loaded here. */
    private static final Map<String, String> fullNames = new ConcurrentHashMap<>();

    private ItemUuids() {}

    /**
     * The item with this identity, if it is loaded here and the current user may see it.
     * Answers from an in-memory index (no I/O).
     *
     * @return the item, or null (unknown here, not loaded yet, or not visible)
     */
    @CheckForNull
    public static Item get(@NonNull String uuid) {
        String fullName = fullNames.get(uuid);
        Item item = fullName == null ? null : Jenkins.get().getItemByFullName(fullName);
        return item != null && uuid.equals(item.getItemUuid()) ? item : null;
    }

    /**
     * Pins an item's identity: from now on {@link Item#getItemUuid} stays what it answers now (the name-derived value
     * of an unpinned item), across renames and moves, and is saved with the item. Saves the item once if it was not
     * pinned; does nothing otherwise. Every new item is pinned already where {@link #pinsNewItems} holds.
     *
     * @return the pinned identity
     * @throws IllegalArgumentException if this kind of item has no identity
     */
    @NonNull
    public static String pin(@NonNull Item item) throws IOException {
        if (item instanceof AbstractItem ai) {
            String id = ai.pinItemUuid();
            index(item);
            return id;
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

    private static void index(Item item) {
        String id = item.getItemUuid();
        if (id != null) {
            fullNames.put(id, item.getFullName());
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
            List<Item> derived = new ArrayList<>();
            try (ACLContext ctx = ACL.as2(ACL.SYSTEM2)) {
                for (Item item : Jenkins.get().allItems()) {
                    if (isPinned(item)) {
                        index(item);
                    } else {
                        derived.add(item);
                    }
                }
                // a pinned identity is kept by the item it was pinned for, even if it was derived from a name another
                // item has now (as when an item is pinned, renamed, and another made with its old name)
                for (Item item : derived) {
                    claim(item);
                }
                fullNames.entrySet().removeIf(e -> {
                    Item item = Jenkins.get().getItemByFullName(e.getValue());
                    return item == null || !e.getKey().equals(item.getItemUuid());
                });
            }
        }

        @Override
        public void onCreated(Item item) {
            claim(item);
        }

        @Override
        public void onUpdated(Item item) {
            claim(item); // as for configuration posted with another item's identity
        }

        /** Indexes an item, after giving it an identity of its own if it has another item's. */
        private static void claim(Item item) {
            String id = item.getItemUuid();
            if (id == null) {
                return;
            }
            String other = fullNames.get(id);
            boolean clash;
            try (ACLContext ctx = ACL.as2(ACL.SYSTEM2)) {
                // whoever made the change, and whatever they may see: the identity of an item hidden from them is not theirs
                Item holder = other == null || other.equals(item.getFullName()) ? null : Jenkins.get().getItemByFullName(other);
                clash = holder != null && id.equals(holder.getItemUuid());
            }
            if (!clash) {
                index(item);
            } else if (item instanceof AbstractItem ai) {
                LOGGER.fine(() -> item.getFullName() + " has the identity of another item; giving it one of its own");
                ai.newItemUuid();
                try {
                    ai.save();
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Could not save the new identity of " + item.getFullName(), e);
                }
                index(item);
            } // else not taken from the item that has it
        }

        @Override
        public void onLocationChanged(Item item, String oldFullName, String newFullName) {
            if (!isPinned(item)) {
                fullNames.remove(AbstractItem.derivedItemUuid(oldFullName), oldFullName);
            }
            claim(item);
        }

        @Override
        public void onDeleted(Item item) {
            String id = item.getItemUuid();
            if (id != null) {
                fullNames.remove(id, item.getFullName());
            }
        }
    }
}
