/*
 * The MIT License
 *
 * Copyright (c) 2026, Tim Jacomb
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

package jenkins.model.dashboard;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Functions;
import hudson.Util;
import hudson.model.AllView;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Items;
import hudson.model.Job;
import hudson.model.ModifiableItemGroup;
import hudson.model.View;
import hudson.model.ViewGroup;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Predicate;
import jenkins.model.Tab;
import jenkins.util.SystemProperties;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.verb.GET;

/**
 * A {@link Dashboard} tab that lists items, loading them a page at a time as the user scrolls,
 * with a search box.
 *
 * <p>Subclasses only provide the items. For example, a tab listing the current user's favorite jobs:
 * <pre>{@code
 * public class FavoritesTab extends ItemListTab {
 *     public FavoritesTab(Dashboard dashboard) { super(dashboard); }
 *     public String getDisplayName() { return "Favorites"; }
 *     public String getIconFileName() { return "symbol-star"; }
 *     public String getUrlName() { return "favorites"; }
 *     public Iterable<? extends Item> getItems() { return Favorites.getFavorites(User.current()); }
 * }
 * }</pre>
 * and contribute it with a {@link jenkins.model.TransientActionFactory} for {@link Dashboard}.
 *
 * @since TODO
 */
@Restricted(Beta.class)
public abstract class ItemListTab extends Tab {

    /**
     * How many items each request returns.
     */
    static /* non-final for Groovy */ int PAGE_SIZE = SystemProperties.getInteger(ItemListTab.class.getName() + ".pageSize", 50);

    /**
     * How many items of an expanded folder the first request returns. Each later request returns as many as are
     * already shown, up to {@link #MAX_CHILDREN_PAGE_SIZE}, so that a large folder takes fewer requests.
     */
    static /* non-final for Groovy */ int CHILDREN_PAGE_SIZE = SystemProperties.getInteger(ItemListTab.class.getName() + ".childrenPageSize", 20);

    /**
     * The most items of an expanded folder a request returns.
     */
    static /* non-final for Groovy */ int MAX_CHILDREN_PAGE_SIZE = 200;

    /**
     * The longest search query, as each search looks at the name of every item.
     */
    static final int MAX_QUERY_LENGTH = 200;

    protected ItemListTab(@NonNull Dashboard dashboard) {
        super(dashboard);
    }

    /**
     * The dashboard this tab belongs to.
     */
    public @NonNull Dashboard getDashboard() {
        return (Dashboard) getObject();
    }

    /**
     * The items to list, in display order, already filtered to those the current user can see.
     * Only the requested page is read from the returned {@link Iterable}, so it may be lazy.
     */
    public abstract @NonNull Iterable<? extends Item> getItems();

    /**
     * The items matching a search query. By default, the items from {@link #getItems()} whose name or display
     * name relative to the dashboard's owner contains the query, as ranked by {@link #rankedByName}.
     *
     * @param query a non-blank query
     */
    public @NonNull Iterable<? extends Item> search(@NonNull String query) {
        return rankedByName(query, getDashboard().getOwner(), getItems());
    }

    /**
     * The items of {@code items} whose name or display name relative to a group contains a query, ignoring case,
     * those whose own name or display name matches first, in their order, then those that only match through the
     * name of a folder between the group and the item, so that searching for a folder's name finds the folder
     * before everything in it. Goes through {@code items} twice, once for each kind, so it must give the same items
     * each time it is iterated, and holds no more than one item at a time.
     * {@code items} may also contain items that don't match, but filtering them out first with
     * {@link #matchingName}, as {@link Items#allItems(ItemGroup, Class, Predicate)} does before checking permissions,
     * saves checking the permissions of every item when searching a large tree.
     */
    protected static @NonNull Iterable<Item> rankedByName(@NonNull String query, @NonNull ItemGroup<?> base,
            @NonNull Iterable<? extends Item> items) {
        Predicate<Item> ownName = matchingOwnName(query);
        return () -> new Iterator<>() {
            private final Predicate<Item> name = matchingName(query, base);
            private Iterator<? extends Item> source = items.iterator();
            private boolean byFolderName;
            private Item next;

            @Override
            public boolean hasNext() {
                while (next == null) {
                    if (!source.hasNext()) {
                        if (byFolderName) {
                            return false;
                        }
                        byFolderName = true;
                        source = items.iterator();
                        continue;
                    }
                    Item item = source.next();
                    if (byFolderName ? !ownName.test(item) && name.test(item) : ownName.test(item)) {
                        next = item;
                    }
                }
                return true;
            }

            @Override
            public Item next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                Item item = next;
                next = null;
                return item;
            }
        };
    }

    /**
     * Whether the name or display name of an item relative to a group contains a query, ignoring case, as
     * {@link #rankedByName} matches items. A query can name folders as shown, separated by "\u00bb", or with "/" as
     * in full names. Remembers whether each folder matches, so testing many items in the same
     * folder costs little more than comparing their own names.
     */
    protected static @NonNull Predicate<Item> matchingName(@NonNull String query, @NonNull ItemGroup<?> base) {
        Predicate<Item> ownName = matchingOwnName(query);
        String q = query.toLowerCase(Locale.ROOT);
        if (q.contains("\u00bb") || q.contains("/") || !q.equals(q.strip())) {
            // A query that spans the separator between a folder's name and an item's name, as shown, or written as
            // in a full name, such as "folder/job"
            return item -> ownName.test(item) || relativeName(item, base).contains(q)
                    || relativeName(item, base).replace(" \u00bb ", "/").contains(q)
                    || item.getRelativeNameFrom(base).toLowerCase(Locale.ROOT).contains(q);
        }
        Map<ItemGroup<?>, Boolean> folders = new HashMap<>();
        return item -> ownName.test(item) || folders.computeIfAbsent(item.getParent(),
                parent -> parent != base && parent instanceof Item folder && relativeName(folder, base).contains(q));
    }

    private static Predicate<Item> matchingOwnName(String query) {
        String q = query.toLowerCase(Locale.ROOT);
        return item -> item.getName().toLowerCase(Locale.ROOT).contains(q)
                || item.getDisplayName().toLowerCase(Locale.ROOT).contains(q);
    }

    private static String relativeName(Item item, ItemGroup<?> base) {
        String name = Functions.getRelativeDisplayNameFrom(item, base);
        return (name != null ? name : item.getFullDisplayName()).toLowerCase(Locale.ROOT);
    }

    /**
     * Shown when there is nothing to list and no search is active.
     */
    public @CheckForNull String getEmptyMessage() {
        return null;
    }

    /**
     * Reads one page of items.
     *
     * <p>Items the current user cannot read are left out, whatever the subclass returns.
     *
     * @param start the index of the first item to return, used when {@code after} is not listed
     * @param after the full name of the last item already shown, or {@code null}. The page continues
     *     after it, so items removed from earlier in the list don't shift the page past items not yet shown.
     * @param search a query, or {@code null} to list everything
     */
    @Restricted(NoExternalUse.class)
    public @NonNull Page getPage(int start, @CheckForNull String after, @CheckForNull String search) {
        String query = Util.fixEmptyAndTrim(search);
        if (query != null && query.length() > MAX_QUERY_LENGTH) {
            query = query.substring(0, MAX_QUERY_LENGTH);
        }
        return slice(query == null ? getItems() : search(query), start, after, PAGE_SIZE, query != null,
                getDashboard().getOwner(), null);
    }

    /**
     * Whether a row can be expanded to list all the items directly inside it. No row can by default, as the tab
     * may list only some items, which expanding a folder would not respect.
     */
    public boolean isExpandable(@NonNull Item item) {
        return false;
    }

    /**
     * Whether an item is a folder that can be expanded. Jobs that hold other jobs, such as matrix projects, open
     * their own page.
     */
    static boolean isFolder(@NonNull Item item) {
        return item instanceof ItemGroup<?> && !(item instanceof Job<?, ?>);
    }

    /**
     * Reads one page of the items directly inside an expanded folder.
     *
     * @param folder the full name of the folder
     * @param start the index of the first item to return, used when {@code after} is not listed
     * @param after the full name of the last item of the folder already shown, or {@code null}
     * @return {@code null} if the folder is not inside the dashboard's owner, the current user cannot see it, or
     *     it cannot be expanded
     */
    @Restricted(NoExternalUse.class)
    public @CheckForNull Page getChildren(@NonNull String folder, int start, @CheckForNull String after) {
        Item item = Dashboard.visible(folder, Item.class);
        if (item == null || !getDashboard().contains(item) || !isExpandable(item)) {
            return null;
        }
        ItemGroup<?> group = (ItemGroup<?>) item;
        int size = Math.min(Math.max(start, CHILDREN_PAGE_SIZE), MAX_CHILDREN_PAGE_SIZE);
        return slice(group.getItems(), start, after, size, false, group, folder);
    }

    /**
     * Reads one page from a list of items, leaving out those the current user cannot read.
     *
     * @param start the index of the first item to return, used when {@code after} is not listed
     * @param after the full name of the last item already shown, or {@code null}. The page continues
     *     after it, so items removed from earlier in the list don't shift the page past items not yet shown.
     * @param size the number of items on a page
     * @param group the item group from which items are named
     * @param folder the full name of the expanded folder whose items these are, or {@code null}
     */
    private static @NonNull Page slice(@NonNull Iterable<? extends Item> source, int start, @CheckForNull String after,
            int size, boolean search, @NonNull ItemGroup<?> group, @CheckForNull String folder) {
        String anchor = Util.fixEmpty(after);
        int from = Math.max(start, 0);
        // The page at the offset, used if the anchor isn't listed
        List<Item> atOffset = new ArrayList<>(size + 1);
        // The page after the anchor, once it has been seen
        List<Item> afterAnchor = null;
        int anchorIndex = -1;
        int index = 0;
        // Whether the page is complete. The rest of a collection is still counted, to say how many items are left;
        // its size cannot be used, as it may include items the user cannot read.
        boolean complete = false;
        boolean countRest = source instanceof Collection<?>;
        for (Item item : source) {
            if (complete && !countRest) {
                break;
            }
            if (!item.hasPermission(Item.READ)) {
                continue;
            }
            int i = index++;
            if (complete) {
                continue;
            }
            if (afterAnchor != null) {
                afterAnchor.add(item);
                complete = afterAnchor.size() > size;
            } else if (anchor != null && anchor.equals(item.getFullName())) {
                afterAnchor = new ArrayList<>(size + 1);
                anchorIndex = i;
            } else if (i >= from && atOffset.size() <= size) {
                atOffset.add(item);
                complete = anchor == null && atOffset.size() > size;
            }
        }
        List<Item> items = afterAnchor != null ? afterAnchor : atOffset;
        int first = afterAnchor != null ? anchorIndex + 1 : from;
        boolean more = items.size() > size;
        if (more) {
            items = items.subList(0, size);
        }
        int next = first + items.size();
        Integer remaining = more && countRest ? Math.max(index - next, 1) : null;
        return new Page(items, more ? next : null, remaining, search, group, folder);
    }

    /**
     * The page shown when there is nothing to list: the empty state of the "All" view of the owner of the
     * dashboard, whichever view is the primary one. For the root of Jenkins, that is the welcome page.
     * Its empty state offers to create a job, so it is {@code null} for an owner that users cannot add items to
     * themselves, such as a multibranch project, unless its primary view is an "All" view.
     */
    @Restricted(NoExternalUse.class)
    public @CheckForNull View getEmptyStateView() {
        ViewGroup owner = (ViewGroup) getDashboard().getOwner();
        if (owner.getPrimaryView() instanceof AllView all) {
            return all;
        }
        return owner instanceof ModifiableItemGroup<?> ? new AllView(AllView.DEFAULT_VIEW_NAME, owner) : null;
    }

    /**
     * Renders one page of rows as an HTML fragment: of the list, or of the items directly inside an expanded
     * folder if {@code folder} names one, in which case the response is "404 Not Found" if it cannot be expanded.
     */
    @GET
    @Restricted(NoExternalUse.class)
    public void doItems(StaplerRequest2 req, StaplerResponse2 rsp, @QueryParameter String folder,
            @QueryParameter String start, @QueryParameter String after, @QueryParameter String search)
            throws IOException, ServletException {
        int from = Objects.requireNonNullElse(Util.tryParseNumber(start, 0), 0).intValue();
        Page page = folder == null ? getPage(from, after, search) : getChildren(folder, from, after);
        if (page == null) {
            rsp.sendError(StaplerResponse2.SC_NOT_FOUND);
            return;
        }
        rsp.setContentType("text/html;charset=UTF-8");
        req.setAttribute("page", page);
        req.getView(this, "rows.jelly").forward(req, rsp);
    }

    /**
     * One page of items.
     *
     * @param items the items on this page
     * @param nextStart the {@code start} of the next page, or {@code null} if this is the last one
     * @param remaining how many items are left after this page, or {@code null} if this is the last page or
     *     it isn't known
     * @param search whether the page holds search results
     * @param group the item group from which items are named
     * @param folder the full name of the expanded folder whose items these are, or {@code null} for the list itself
     */
    @Restricted(NoExternalUse.class)
    public record Page(@NonNull List<Item> items, @CheckForNull Integer nextStart, @CheckForNull Integer remaining,
            boolean search, @NonNull ItemGroup<?> group, @CheckForNull String folder) {}
}
