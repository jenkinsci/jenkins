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
import hudson.Util;
import hudson.model.AllView;
import hudson.model.Item;
import hudson.model.View;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.StreamSupport;
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
 * @since 2.592
 */
@Restricted(Beta.class)
public abstract class ItemListTab extends Tab {

    /**
     * How many items each request returns.
     */
    static /* non-final for Groovy */ int PAGE_SIZE = SystemProperties.getInteger(ItemListTab.class.getName() + ".pageSize", 50);

    protected ItemListTab(@NonNull Dashboard dashboard) {
        super(dashboard);
    }

    /**
     * The items to list, in display order, already filtered to those the current user can see.
     * Only the requested page is read from the returned {@link Iterable}, so it may be lazy.
     */
    public abstract @NonNull Iterable<? extends Item> getItems();

    /**
     * The items matching a search query. By default, the items from {@link #getItems()} accepted by
     * {@link #matching(String)}.
     *
     * @param query a non-blank query
     */
    public @NonNull Iterable<? extends Item> search(@NonNull String query) {
        Predicate<Item> matches = matching(query);
        return () -> StreamSupport.stream(getItems().spliterator(), false)
                .filter(matches)
                .map(Item.class::cast)
                .iterator();
    }

    /**
     * Accepts items whose name or full display name contains a query, ignoring case. The full display
     * name includes the names of the folders containing the item, so a folder's name matches everything
     * in it.
     */
    protected static @NonNull Predicate<Item> matching(@NonNull String query) {
        String q = query.toLowerCase(Locale.ROOT);
        return item -> item.getName().toLowerCase(Locale.ROOT).contains(q)
                || item.getFullDisplayName().toLowerCase(Locale.ROOT).contains(q);
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
