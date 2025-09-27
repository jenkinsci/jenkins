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

import com.thoughtworks.xstream.XStream;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.XmlFile;
import hudson.model.listeners.ItemListener;
import hudson.remoting.Callable;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.security.AccessControlled;
import hudson.triggers.Trigger;
import hudson.util.DescriptorList;
import hudson.util.EditDistance;
import hudson.util.XStream2;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Stack;
import java.util.StringTokenizer;
import java.util.function.Predicate;
import jenkins.model.DirectlyModifiableTopLevelItemGroup;
import jenkins.model.Jenkins;
import jenkins.util.MemoryReductionUtil;
import jenkins.util.ThrowingCallable;
import jenkins.util.ThrowingRunnable;
import org.apache.commons.io.FileUtils;
import org.springframework.security.core.Authentication;

/**
 * Convenience methods related to {@link Item}.
 *
 * @author Kohsuke Kawaguchi
 */
public class Items {
    /**
     * List of all installed {@link TopLevelItem} types.
     *
     * @deprecated as of 1.286
     *      Use {@link #all()} for read access and {@link Extension} for registration.
     */
    @Deprecated
    public static final List<TopLevelItemDescriptor> LIST = (List) new DescriptorList<>(TopLevelItem.class);

    /**
     * Used to behave differently when loading posted configuration as opposed to persisted configuration.
     * @see Trigger#start
     * @since 1.482
     */
    private static final ThreadLocal<Boolean> updatingByXml = ThreadLocal.withInitial(() -> false);
    /**
     * A comparator of {@link Item} instances that uses a case-insensitive comparison of {@link Item#getName()}.
     * If you are replacing {@link #getAllItems(ItemGroup, Class)} with {@link #allItems(ItemGroup, Class)} and
     * need to restore the sort order of a further filtered result, you probably want {@link #BY_FULL_NAME}.
     *
     * @since 2.37
     */
    public static final Comparator<Item> BY_NAME = new Comparator<Item>() {
        @Override public int compare(Item i1, Item i2) {
            return name(i1).compareToIgnoreCase(name(i2));
        }

        String name(Item i) {
            String n = i.getName();
            if (i instanceof ItemGroup) {
                n += '/';
            }
            return n;
        }
    };
    /**
     * A comparator of {@link Item} instances that uses a case-insensitive comparison of {@link Item#getFullName()}.
     *
     * @since 2.37
     */
    public static final Comparator<Item> BY_FULL_NAME = new Comparator<Item>() {
        @Override public int compare(Item i1, Item i2) {
            return name(i1).compareToIgnoreCase(name(i2));
        }

        String name(Item i) {
            String n = i.getFullName();
            if (i instanceof ItemGroup) {
                n += '/';
            }
            return n;
        }
    };

    /**
     * Runs a block while making {@link #currentlyUpdatingByXml} be temporarily true.
     * Use this when you are creating or changing an item.
     * @param <T> an error type (may be {@link Error})
     * @param runnable a block, typically running {@link #load} or {@link Item#onLoad}
     * @throws T anything {@code runnable} throws
     * @since TODO
     */
    public static <T extends Throwable> void runWhileUpdatingByXml(ThrowingRunnable<T> runnable) throws T {
        updatingByXml.set(true);
        try {
            runnable.run();
        } finally {
            updatingByXml.set(false);
        }
    }

    /**
     * Runs a block while making {@link #currentlyUpdatingByXml} be temporarily true.
     * Use this when you are creating or changing an item.
     * @param <V> a return value type (may be {@link Void})
     * @param <T> an error type (may be {@link Error})
     * @param callable a block, typically running {@link #load} or {@link Item#onLoad}
     * @return whatever {@code callable} returned
     * @throws T anything {@code callable} throws
     * @since TODO
     */
    public static <V, T extends Throwable> V callWhileUpdatingByXml(ThrowingCallable<V, T> callable) throws T {
        updatingByXml.set(true);
        try {
            return callable.call();
        } finally {
            updatingByXml.set(false);
        }
    }

    /**
     * Prefer {@link #runWhileUpdatingByXml} or {@link #callWhileUpdatingByXml}.
     * @since 1.546
     */
    public static <V, T extends Throwable> V whileUpdatingByXml(Callable<V, T> callable) throws T {
        updatingByXml.set(true);
        try {
            return callable.call();
        } finally {
            updatingByXml.set(false);
        }
    }

    /**
     * Checks whether we are in the middle of creating or configuring an item via XML.
     * Used to determine the {@code newInstance} parameter for {@link Trigger#start}.
     * @return true if {@link #runWhileUpdatingByXml} or {@link #callWhileUpdatingByXml} is currently being called,
     *         false for example when merely starting Jenkins or reloading from disk
     * @since 1.546
     */
    public static boolean currentlyUpdatingByXml() {
        return updatingByXml.get();
    }

    /**
     * Returns all the registered {@link TopLevelItemDescriptor}s.
     */
    public static DescriptorExtensionList<TopLevelItem, TopLevelItemDescriptor> all() {
        return Jenkins.get().getDescriptorList(TopLevelItem.class);
    }

    /**
     * Returns all the registered {@link TopLevelItemDescriptor}s that the current security principal is allowed to
     * create within the specified item group.
     *
     * @since 1.607
     */
    public static List<TopLevelItemDescriptor> all(ItemGroup c) {
        return all2(Jenkins.getAuthentication2(), c);
    }

    /**
     * Returns all the registered {@link TopLevelItemDescriptor}s that the specified security principal is allowed to
     * create within the specified item group.
     *
     * @since 2.266
     */
    public static List<TopLevelItemDescriptor> all2(Authentication a, ItemGroup c) {
        List<TopLevelItemDescriptor> result = new ArrayList<>();
        ACL acl;
        if (c instanceof AccessControlled) {
            acl = ((AccessControlled) c).getACL();
        } else {
            // fall back to root
            acl = Jenkins.get().getACL();
        }
        for (TopLevelItemDescriptor d : all()) {
            if (acl.hasCreatePermission2(a, c, d) && d.isApplicableIn(c)) {
                result.add(d);
            }
        }
        return result;
    }

    /**
     * @deprecated use {@link #all2(Authentication, ItemGroup)}
     * @since 1.607
     */
    @Deprecated
    public static List<TopLevelItemDescriptor> all(org.acegisecurity.Authentication a, ItemGroup c) {
        return all2(a.toSpring(), c);
    }

    /**
     * @deprecated Underspecified what the parameter is. {@link Descriptor#getId}? A {@link Describable} class name?
     */
    @Deprecated
    public static TopLevelItemDescriptor getDescriptor(String fqcn) {
        return Descriptor.find(all(), fqcn);
    }

    /**
     * Converts a list of items into a comma-separated list of full names.
     */
    public static String toNameList(Collection<? extends Item> items) {
        StringBuilder buf = new StringBuilder();
        for (Item item : items) {
            if (!buf.isEmpty())
                buf.append(", ");
            buf.append(item.getFullName());
        }
        return buf.toString();
    }

    /**
     * @deprecated as of 1.406
     *      Use {@link #fromNameList(ItemGroup, String, Class)}
     */
    @Deprecated
    public static <T extends Item> List<T> fromNameList(String list, Class<T> type) {
        return fromNameList(null, list, type);
    }

    /**
     * Does the opposite of {@link #toNameList(Collection)}.
     */
    public static <T extends Item> List<T> fromNameList(ItemGroup context, @NonNull String list, @NonNull Class<T> type) {
        final Jenkins jenkins = Jenkins.get();

        List<T> r = new ArrayList<>();

        StringTokenizer tokens = new StringTokenizer(list, ",");
        while (tokens.hasMoreTokens()) {
            String fullName = tokens.nextToken().trim();
            if (fullName != null && !fullName.isEmpty()) {
                T item = jenkins.getItem(fullName, context, type);
                if (item != null)
                    r.add(item);
            }
        }
        return r;
    }

    /**
     * Computes the canonical full name of a relative path in an {@link ItemGroup} context, handling relative
     * positions ".." and "." as absolute path starting with "/". The resulting name is the item fullName from Jenkins
     * root.
     */
    public static String getCanonicalName(ItemGroup context, String path) {
        String[] c = context.getFullName().split("/");
        String[] p = path.split("/");

        Stack<String> name = new Stack<>();
        for (int i = 0; i < c.length; i++) {
            if (i == 0 && c[i].isEmpty()) continue;
            name.push(c[i]);
        }
        for (int i = 0; i < p.length; i++) {
            if (i == 0 && p[i].isEmpty()) {
                // Absolute path starting with a "/"
                name.clear();
                continue;
            }
            if (p[i].equals("..")) {
                if (name.isEmpty()) {
                    throw new IllegalArgumentException(String.format(
                            "Illegal relative path '%s' within context '%s'", path, context.getFullName()
                    ));
                }
                name.pop();
                continue;
            }
            if (p[i].equals(".")) {
                continue;
            }
            name.push(p[i]);
        }
        return String.join("/", name);
    }

    /**
     * Computes the relative name of list of items after a rename or move occurred.
     * Used to manage job references as names in plugins to support {@link hudson.model.listeners.ItemListener#onLocationChanged}.
     * <p>
     * In a hierarchical context, when a plugin has a reference to a job as {@code ../foo/bar} this method will
     * handle the relative path as "foo" is renamed to "zot" to compute {@code ../zot/bar}
     *
     * @param oldFullName the old full name of the item
     * @param newFullName the new full name of the item
     * @param relativeNames coma separated list of Item relative names
     * @param context the {link ItemGroup} relative names refer to
     * @return relative name for the renamed item, based on the same ItemGroup context
     */
    public static String computeRelativeNamesAfterRenaming(String oldFullName, String newFullName, String relativeNames, ItemGroup context) {

        StringTokenizer tokens = new StringTokenizer(relativeNames, ",");
        List<String> newValue = new ArrayList<>();
        while (tokens.hasMoreTokens()) {
            String relativeName = tokens.nextToken().trim();
            String canonicalName = getCanonicalName(context, relativeName);
            if (canonicalName.equals(oldFullName) || canonicalName.startsWith(oldFullName + '/')) {
                String newCanonicalName = newFullName + canonicalName.substring(oldFullName.length());
                if (relativeName.startsWith("/")) {
                    newValue.add("/" + newCanonicalName);
                } else {
                    newValue.add(getRelativeNameFrom(newCanonicalName, context.getFullName()));
                }
            } else {
                newValue.add(relativeName);
            }
        }
        return String.join(",", newValue);
    }

    // Had difficulty adapting the version in Functions to use no live items, so rewrote it:
    static String getRelativeNameFrom(String itemFullName, String groupFullName) {
        String[] itemFullNameA = itemFullName.isEmpty() ? MemoryReductionUtil.EMPTY_STRING_ARRAY : itemFullName.split("/");
        String[] groupFullNameA = groupFullName.isEmpty() ? MemoryReductionUtil.EMPTY_STRING_ARRAY : groupFullName.split("/");
        for (int i = 0; ; i++) {
            if (i == itemFullNameA.length) {
                if (i == groupFullNameA.length) {
                    // itemFullName and groupFullName are identical
                    return ".";
                } else {
                    // itemFullName is an ancestor of groupFullName; insert ../ for rest of groupFullName
                    StringBuilder b = new StringBuilder();
                    for (int j = 0; j < groupFullNameA.length - itemFullNameA.length; j++) {
                        if (j > 0) {
                            b.append('/');
                        }
                        b.append("..");
                    }
                    return b.toString();
                }
            } else if (i == groupFullNameA.length) {
                // groupFullName is an ancestor of itemFullName; insert rest of itemFullName
                StringBuilder b = new StringBuilder();
                for (int j = i; j < itemFullNameA.length; j++) {
                    if (j > i) {
                        b.append('/');
                    }
                    b.append(itemFullNameA[j]);
                }
                return b.toString();
            } else if (itemFullNameA[i].equals(groupFullNameA[i])) {
                // identical up to this point
                continue;
            } else {
                // first mismatch; insert ../ for rest of groupFullName, then rest of itemFullName
                StringBuilder b = new StringBuilder();
                for (int j = i; j < groupFullNameA.length; j++) {
                    if (j > i) {
                        b.append('/');
                    }
                    b.append("..");
                }
                for (int j = i; j < itemFullNameA.length; j++) {
                    b.append('/').append(itemFullNameA[j]);
                }
                return b.toString();
            }
        }
    }

    /**
     * Loads a {@link Item} from a config file.
     *
     * @param dir
     *      The directory that contains the config file, not the config file itself.
     */
    public static Item load(ItemGroup parent, File dir) throws IOException {
        Item item = (Item) getConfigFile(dir).read();
        item.onLoad(parent, parent.getItemName(dir, item));
        return item;
    }

    /**
     * The file we save our configuration.
     */
    public static XmlFile getConfigFile(File dir) {
        return new XmlFile(XSTREAM, new File(dir, "config.xml"));
    }

    /**
     * The file we save our configuration.
     */
    public static XmlFile getConfigFile(Item item) {
        return getConfigFile(item.getRootDir());
    }

    /**
     * Gets all the {@link Item}s recursively in the {@link ItemGroup} tree
     * and filter them by the given type. The returned list will represent a snapshot view of the items present at some
     * time during the call. If items are moved during the call, depending on the move, it may be possible for some
     * items to escape the snapshot entirely.
     * <p>
     * If you do not need to iterate all items, or if the order of the items is not required, consider using
     * {@link #allItems(ItemGroup, Class)} instead.
     *
     * @param root Root node to start searching from
     * @param type Given type of of items being searched for
     * @return List of items matching given criteria
     *
     * @since 1.512
     */
    public static <T extends Item> List<T> getAllItems(final ItemGroup root, Class<T> type) {
        return getAllItems(root, type, t -> true);
    }

    /**
     * Similar to {@link #getAllItems(ItemGroup, Class)} but with a predicate to pre-filter items to
     * avoid checking ACLs unnecessarily and returning items not required by the caller
     * @param root Root node to start searching from
     * @param type Given type of of items being searched for
     * @param pred Predicate condition to filter items
     * @return List of items matching given criteria
     *
     * @since 2.221
     */
    public static <T extends Item> List<T> getAllItems(final ItemGroup root, Class<T> type, Predicate<T> pred) {
        List<T> r = new ArrayList<>();
        getAllItems(root, type, r, pred);
        return r;
    }

    private static <T extends Item> void getAllItems(final ItemGroup root, Class<T> type, List<T> r, Predicate<T> pred) {
        List<Item> items = new ArrayList<>(((ItemGroup<?>) root).getItems(t -> t instanceof ItemGroup || (type.isInstance(t) && pred.test(type.cast(t)))));
        // because we add items depth first, we can use the quicker BY_NAME comparison
        items.sort(BY_NAME);
        for (Item i : items) {
            if (type.isInstance(i) && pred.test(type.cast(i))) {
                if (i.hasPermission(Item.READ)) {
                    r.add(type.cast(i));
                }
            }
            if (i instanceof ItemGroup) {
                getAllItems((ItemGroup) i, type, r, pred);
            }
        }
    }

    /**
     * Gets a read-only view of all the {@link Item}s recursively in the {@link ItemGroup} tree visible to
     * {@link Jenkins#getAuthentication2()} without concern for the order in which items are returned. Each iteration
     * of the view will be "live" reflecting the items available between the time the iteration was started and the
     * time the iteration was completed, however if items are moved during an iteration - depending on the move - it
     * may be possible for such items to escape the entire iteration.
     *
     * @param root the root.
     * @param type the type.
     * @param <T> the type.
     * @return An {@link Iterable} for all items.
     * @since 2.37
     */
    public static <T extends Item> Iterable<T> allItems(ItemGroup root, Class<T> type) {
        return allItems2(Jenkins.getAuthentication2(), root, type);
    }

    /**
     * Gets a read-only view of all the {@link Item}s recursively matching type and predicate
     * in the {@link ItemGroup} tree visible to
     * {@link Jenkins#getAuthentication2()} without concern for the order in which items are returned. Each iteration
     * of the view will be "live" reflecting the items available between the time the iteration was started and the
     * time the iteration was completed, however if items are moved during an iteration - depending on the move - it
     * may be possible for such items to escape the entire iteration.
     *
     * @param root the root.
     * @param type the type.
     * @param pred the predicate.
     * @param <T> the type.
     * @return An {@link Iterable} for all items.
     * @since 2.221
     */
    public static <T extends Item> Iterable<T> allItems(ItemGroup root, Class<T> type, Predicate<T> pred) {
        return allItems2(Jenkins.getAuthentication2(), root, type, pred);
    }

    /**
     * Gets a read-only view all the {@link Item}s recursively in the {@link ItemGroup} tree visible to the supplied
     * authentication without concern for the order in which items are returned. Each iteration
     * of the view will be "live" reflecting the items available between the time the iteration was started and the
     * time the iteration was completed, however if items are moved during an iteration - depending on the move - it
     * may be possible for such items to escape the entire iteration.
     *
     * @param root the root.
     * @param type the type.
     * @param <T> the type.
     * @return An {@link Iterable} for all items.
     * @since 2.266
     */
    public static <T extends Item> Iterable<T> allItems2(Authentication authentication, ItemGroup root, Class<T> type) {
        return allItems2(authentication, root, type, t -> true);
    }

    /**
     * @deprecated use {@link #allItems2(Authentication, ItemGroup, Class)}
     * @since 2.37
     */
    @Deprecated
    public static <T extends Item> Iterable<T> allItems(org.acegisecurity.Authentication authentication, ItemGroup root, Class<T> type) {
        return allItems2(authentication.toSpring(), root, type);
    }

    /**
     * Gets a read-only view all the {@link Item}s recursively matching supplied type and predicate conditions
     * in the {@link ItemGroup} tree visible to the supplied
     * authentication without concern for the order in which items are returned. Each iteration
     * of the view will be "live" reflecting the items available between the time the iteration was started and the
     * time the iteration was completed, however if items are moved during an iteration - depending on the move - it
     * may be possible for such items to escape the entire iteration.
     *
     * @param root the root.
     * @param type the type.
     * @param <T> the type.
     * @param pred the predicate.
     * @return An {@link Iterable} for all items.
     * @since 2.266
     */
    public static <T extends Item> Iterable<T> allItems2(Authentication authentication, ItemGroup root, Class<T> type, Predicate<T> pred) {
        return new AllItemsIterable<>(root, authentication, type, pred);
    }

    /**
     * @deprecated use {@link #allItems2(Authentication, ItemGroup, Class, Predicate)}
     * @since 2.221
     */
    @Deprecated
    public static <T extends Item> Iterable<T> allItems(org.acegisecurity.Authentication authentication, ItemGroup root, Class<T> type, Predicate<T> pred) {
        return allItems2(authentication.toSpring(), root, type, pred);
    }

    /**
     * Finds an item whose name (when referenced from the specified context) is closest to the given name.
     * @param <T> the type of item being considered
     * @param type same as {@code T}
     * @param name the supplied name
     * @param context a context to start from (used to compute relative names)
     * @return the closest available item
     * @since 1.538
     */
    public static @CheckForNull <T extends Item> T findNearest(Class<T> type, String name, ItemGroup context) {
        List<String> names = new ArrayList<>();
        for (T item : Jenkins.get().allItems(type)) {
            names.add(item.getRelativeNameFrom(context));
        }
        String nearest = EditDistance.findNearest(name, names);
        return Jenkins.get().getItem(nearest, context, type);
    }

    /**
     * Moves an item between folders (or top level).
     * Fires all relevant events but does not verify that the item’s directory is not currently being used in some way (for example by a running build).
     * Does not check any permissions.
     * @param item some item (job or folder)
     * @param destination the destination of the move (a folder or {@link Jenkins}); not the current parent (or you could just call {@link AbstractItem#renameTo})
     * @return the new item (usually the same object as {@code item})
     * @throws IOException if the move fails, or some subsequent step fails (directory might have already been moved)
     * @throws IllegalArgumentException if the move would really be a rename, or the destination cannot accept the item, or the destination already has an item of that name
     * @since 1.548
     */
    public static <I extends AbstractItem & TopLevelItem> I move(I item, DirectlyModifiableTopLevelItemGroup destination) throws IOException, IllegalArgumentException {
        DirectlyModifiableTopLevelItemGroup oldParent = (DirectlyModifiableTopLevelItemGroup) item.getParent();
        if (oldParent == destination) {
            throw new IllegalArgumentException();
        }
        // TODO verify that destination is to not equal to, or inside, item
        if (!destination.canAdd(item)) {
            throw new IllegalArgumentException();
        }
        String name = item.getName();
        verifyItemDoesNotAlreadyExist(destination, name, null);
        String oldFullName = item.getFullName();
        // TODO AbstractItem.renameTo has a more baroque implementation; factor it out into a utility method perhaps?
        File destDir = destination.getRootDirFor(item);
        FileUtils.forceMkdir(destDir.getParentFile());
        FileUtils.moveDirectory(item.getRootDir(), destDir);
        oldParent.remove(item);
        I newItem = destination.add(item, name);
        item.movedTo(destination, newItem, destDir);
        ItemListener.fireLocationChange(newItem, oldFullName);
        return newItem;
    }

    private static class AllItemsIterable<T extends Item> implements Iterable<T> {

        /**
         * The authentication we are iterating as.
         */
        private final Authentication authentication;
        /**
         * The root we are iterating from.
         */
        private final ItemGroup root;
        /**
         * The type of item we want to return.
         */
        private final Class<T> type;
        /**
        * Predicate to filter items with
        */
        private final Predicate<T> pred;

        private AllItemsIterable(ItemGroup root, Authentication authentication, Class<T> type, Predicate<T> pred) {
            this.root = root;
            this.authentication = authentication;
            this.type = type;
            this.pred = pred;
        }

        @Override
        public Iterator<T> iterator() {
            return new AllItemsIterator();
        }

        private class AllItemsIterator implements Iterator<T> {

            /**
             * The stack of {@link ItemGroup}s that we have left to descend into.
             */
            private final Stack<ItemGroup> stack = new Stack<>();
            /**
             * The iterator of the current {@link ItemGroup} we are iterating.
             */
            private Iterator<Item> delegate = null;
            /**
             * The next item.
             */
            private T next = null;

            private AllItemsIterator() {
                // put on the stack so that hasNext() is the only place that has to worry about authentication
                // alternative would be to impersonate and populate delegate.
                stack.push(root);
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean hasNext() {
                if (next != null) {
                    return true;
                }
                Predicate<Item> search = t -> t instanceof ItemGroup || (type.isInstance(t) && pred.test(type.cast(t)));
                while (true) {
                    if (delegate == null || !delegate.hasNext()) {
                        if (stack.isEmpty()) {
                            return false;
                        }
                        ItemGroup group = stack.pop();
                        // group.getItems() is responsible for performing the permission check so we will not repeat it
                        if (Jenkins.getAuthentication2().equals(authentication)) {
                            delegate = group.getItems(search).iterator();
                        } else {
                            // slower path because the caller has switched authentication
                            // we need to keep the original authentication so that allItems() can be used
                            // like getAllItems() without the cost of building the entire list up front
                            try (ACLContext ctx = ACL.as2(authentication)) {
                                delegate = group.getItems(search).iterator();
                            }
                        }
                    }
                    while (delegate.hasNext()) {
                        Item item = delegate.next();
                        if (item instanceof ItemGroup) {
                            stack.push((ItemGroup) item);
                        }
                        if (type.isInstance(item) && pred.test(type.cast(item))) {
                            next = type.cast(item);
                            return true;
                        }
                    }
                }
            }

            @Override
            public T next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                try {
                    return next;
                } finally {
                    next = null;
                }
            }

        }
    }

    /**
     * Securely check for the existence of an item before trying to create one with the same name.
     * @param parent the folder where we are about to create/rename/move an item
     * @param newName the proposed new name
     * @param variant if not null, an existing item which we accept could be there
     * @throws IllegalArgumentException if there is already something there, which you were supposed to know about
     * @throws Failure if there is already something there but you should not be told details
     */
    static void verifyItemDoesNotAlreadyExist(@NonNull ItemGroup<?> parent, @NonNull String newName, @CheckForNull Item variant) throws IllegalArgumentException, Failure {
        Item existing;
        try (ACLContext ctxt = ACL.as2(ACL.SYSTEM2)) {
            existing = parent.getItem(newName);
        }
        if (existing != null && existing != variant) {
            if (existing.hasPermission(Item.DISCOVER)) {
                String prefix = parent.getFullName();
                throw new IllegalArgumentException((prefix.isEmpty() ? "" : prefix + "/") + newName + " already exists");
            } else {
                // Cannot hide its existence, so at least be as vague as possible.
                throw new Failure("");
            }
        }
    }

    /**
     * Used to load/save job configuration.
     *
     * When you extend {@link Job} in a plugin, try to put the alias so
     * that it produces a reasonable XML.
     */
    public static final XStream XSTREAM = new XStream2();

    /**
     * Alias to {@link #XSTREAM} so that one can access additional methods on {@link XStream2} more easily.
     */
    public static final XStream2 XSTREAM2 = (XStream2) XSTREAM;

    static {
        XSTREAM.alias("project", FreeStyleProject.class);
    }
}
