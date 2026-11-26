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
import hudson.model.View;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Predicate;
import jenkins.model.Jenkins;
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
 * <p>Subclasses only provide the items. For example, a tab listing the current user's favourite jobs:
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
     * The longest search query, as each search looks at the name of every item.
     */
    static final int MAX_QUERY_LENGTH = 200;

    protected ItemListTab(@NonNull Dashboard dashboard) {
        super(dashboard);
    }

    /**
     * The items to list, in display order, already filtered to those the current user can see.
     * Only the requested page is read from the returned {@link Iterable}, so it may be lazy.
     */
    public abstract @NonNull Iterable<? extends Item> getItems();

    /**
     * The items matching a search query. By default, the items from {@link #getItems()} whose name or full display
     * name contains the query, as ranked by {@link #rankedByName}.
     *
     * @param query a non-blank query
     */
    public @NonNull Iterable<? extends Item> search(@NonNull String query) {
        return rankedByName(query, Jenkins.get(), getItems());
    }

    /**
     * The items of {@code items} whose name or display name relative to a group contains a query, ignoring case,
     * those whose own name or display name matches first, in their order, then those that only match through the
     * name of a folder between the group and the item, so that searching for a folder's name finds the folder
     * before everything in it. Goes through {@code items} once, keeping the second kind until the first runs out.
     * {@code items} may also contain items that don't match, but filtering them out first with
     * {@link #matchingName}, as {@link Items#allItems(ItemGroup, Class, Predicate)} does before checking permissions,
     * saves checking the permissions of every item when searching a large tree.
     */
    protected static @NonNull Iterable<Item> rankedByName(@NonNull String query, @NonNull ItemGroup<?> base,
            @NonNull Iterable<? extends Item> items) {
        Predicate<Item> ownName = matchingOwnName(query);
        return () -> new Iterator<>() {
            private final Predicate<Item> name = matchingName(query, base);
            private final Iterator<? extends Item> source = items.iterator();
            private final Deque<Item> byFolderName = new ArrayDeque<>();
            private Item next;

            @Override
            public boolean hasNext() {
                while (next == null && source.hasNext()) {
                    Item item = source.next();
                    if (ownName.test(item)) {
                        next = item;
                    } else if (name.test(item)) {
                        byFolderName.add(item);
                    }
                }
                if (next == null) {
                    next = byFolderName.poll();
                }
                return next != null;
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
     * Items the current user cannot read are left out, whatever the subclass returns.
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
        String anchor = Util.fixEmpty(after);
        Iterable<? extends Item> source = query == null ? getItems() : search(query);
        int from = Math.max(start, 0);
        // The page at the offset, used if the anchor isn't listed
        List<Item> atOffset = new ArrayList<>(PAGE_SIZE + 1);
        // The page after the anchor, once it has been seen
        List<Item> afterAnchor = null;
        int anchorIndex = -1;
        int index = 0;
        for (Item item : source) {
            if (!item.hasPermission(Item.READ)) {
                continue;
            }
            int i = index++;
            if (afterAnchor != null) {
                afterAnchor.add(item);
                if (afterAnchor.size() > PAGE_SIZE) {
                    break;
                }
            } else if (anchor != null && anchor.equals(item.getFullName())) {
                afterAnchor = new ArrayList<>(PAGE_SIZE + 1);
                anchorIndex = i;
            } else if (i >= from && atOffset.size() <= PAGE_SIZE) {
                atOffset.add(item);
                if (anchor == null && atOffset.size() > PAGE_SIZE) {
                    break;
                }
            }
        }
        List<Item> items = afterAnchor != null ? afterAnchor : atOffset;
        int first = afterAnchor != null ? anchorIndex + 1 : from;
        boolean more = items.size() > PAGE_SIZE;
        if (more) {
            items = items.subList(0, PAGE_SIZE);
        }
        return new Page(items, more ? first + items.size() : null, query != null);
    }

    /**
     * Reads one page of items, by position only.
     *
     * @see #getPage(int, String, String)
     */
    @Restricted(NoExternalUse.class)
    public @NonNull Page getPage(int start, @CheckForNull String search) {
        return getPage(start, null, search);
    }

    /**
     * The page shown when there is nothing to list: the welcome page of the root of Jenkins, whichever
     * view is the primary one.
     */
    @Restricted(NoExternalUse.class)
    public @NonNull View getEmptyStateView() {
        Jenkins jenkins = Jenkins.get();
        return jenkins.getPrimaryView() instanceof AllView all ? all : new AllView(AllView.DEFAULT_VIEW_NAME, jenkins);
    }

    /**
     * Renders one page of rows as an HTML fragment.
     */
    @GET
    @Restricted(NoExternalUse.class)
    public void doItems(StaplerRequest2 req, StaplerResponse2 rsp, @QueryParameter String start,
            @QueryParameter String after, @QueryParameter String search)
            throws IOException, ServletException {
        rsp.setContentType("text/html;charset=UTF-8");
        req.setAttribute("page", getPage(Objects.requireNonNullElse(Util.tryParseNumber(start, 0), 0).intValue(), after, search));
        req.getView(this, "rows.jelly").forward(req, rsp);
    }

    /**
     * One page of items.
     *
     * @param items the items on this page
     * @param nextStart the {@code start} of the next page, or {@code null} if this is the last one
     * @param search whether the page holds search results, which are shown with their full names
     */
    @Restricted(NoExternalUse.class)
    public record Page(@NonNull List<Item> items, @CheckForNull Integer nextStart, boolean search) {}
}
