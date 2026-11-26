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
import hudson.model.Action;
import hudson.model.Actionable;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.View;
import hudson.model.ViewGroup;
import hudson.search.Search;
import hudson.search.SearchIndexBuilder;
import hudson.search.SearchableModelObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import jenkins.model.Jenkins;
import jenkins.model.ModelObjectWithContextMenu;
import jenkins.model.Tab;
import jenkins.model.experimentalflags.NewDashboardPageUserExperimentalFlag;
import jenkins.model.menu.Group;
import jenkins.model.menu.action.BaseUrlAction;
import jenkins.model.menu.event.DropdownEvent;
import jenkins.model.menu.event.Event;
import jenkins.model.menu.event.LinkEvent;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.Ancestor;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;

/**
 * The home page of the experimental dashboard, shown to users who have enabled
 * {@link NewDashboardPageUserExperimentalFlag} at the root URL, and at the URL of each item group with views whose
 * page is its {@linkplain ViewGroup#getPrimaryView() primary view}.
 *
 * <p>The page is made of {@link Tab}s. Plugins can add a tab by implementing
 * {@link jenkins.model.TransientActionFactory} for this type, typically returning an {@link ItemListTab}.
 * A tab shows on the root dashboard only, unless it implements {@link ShownInItemGroups}, which means that it limits
 * what it shows to {@link #getOwner()}.
 * A dashboard is created for each request, so actions added with {@link #addAction(Action)} are lost; contribute
 * them with a {@link jenkins.model.TransientActionFactory} instead.
 *
 * <p>The root URL belongs to the primary view of Jenkins, whose page hands over to this one when the flag is on.
 * Tabs are reachable at {@code /<urlName>/} through {@link Jenkins#getDynamic(String)}, which consults them only
 * after its own actions, so a tab cannot hide a root action. Another item group works the same way under its own
 * URL, if it hands the URL over to its primary view, typically through
 * {@link org.kohsuke.stapler.StaplerFallback}: its page is the view's, and its tabs are reachable through
 * {@link View#getDynamic(String)}, after its own actions and the view's.
 *
 * @since TODO
 */
@Restricted(Beta.class)
public final class Dashboard extends Actionable {

    /**
     * The item group whose dashboard this is: {@link Jenkins} or another item group with views.
     */
    private final @NonNull ItemGroup<?> owner;

    private Dashboard(@NonNull ItemGroup<?> owner) {
        this.owner = owner;
    }

    /**
     * The dashboard of an item group: the root dashboard for {@link Jenkins}, a dashboard limited to the group for
     * another item group with views, otherwise {@code null}.
     */
    public static @CheckForNull Dashboard of(@NonNull ItemGroup<?> group) {
        return group instanceof ViewGroup ? new Dashboard(group) : null;
    }

    /**
     * Whether the current user sees the dashboard at the root URL and at the URLs of other item groups.
     */
    public static boolean isEnabled() {
        return new NewDashboardPageUserExperimentalFlag().getFlagValue();
    }

    /**
     * Finds a tab of the dashboard of an item group for a URL token, if the current user sees the dashboard.
     *
     * @return the tab, or {@code null} if there is none, or the current user does not see the dashboard
     * @see #getTab(String)
     */
    public static @CheckForNull Tab findTab(@NonNull ItemGroup<?> group, @NonNull String token) {
        if (!isEnabled()) {
            return null;
        }
        Dashboard dashboard = of(group);
        return dashboard == null ? null : dashboard.getTab(token);
    }

    /**
     * Finds a tab of the dashboard of a view's owner for a URL token, when the current request reached the view
     * through a URL of its owner, as the owner's page is then the view's.
     *
     * @return the tab, or {@code null} if there is none, the view was reached through its own URL, or the current
     *     user does not see the dashboard
     */
    @Restricted(NoExternalUse.class)
    public static @CheckForNull Tab findTab(@NonNull View view, @NonNull String token) {
        StaplerRequest2 request = Stapler.getCurrentRequest2();
        if (request == null || !(view.getOwner() instanceof ItemGroup<?> group) || !isShownAtOwnerUrl(view, request)) {
            return null;
        }
        return findTab(group, token);
    }

    /**
     * The item group whose items this dashboard shows: {@link Jenkins} or another item group with views.
     */
    public @NonNull ItemGroup<?> getOwner() {
        return owner;
    }

    /**
     * Whether this is the dashboard of the root of Jenkins.
     */
    public boolean isRoot() {
        return owner instanceof Jenkins;
    }

    /**
     * The prefix of the full names of the items inside {@link #getOwner()}, such as {@code team/}, or
     * empty for the root, which contains every item.
     */
    @Restricted(NoExternalUse.class)
    public @NonNull String getItemPrefix() {
        return isRoot() ? "" : getOwner().getFullName() + '/';
    }

    /**
     * Whether an item is inside {@link #getOwner()}, at any depth.
     */
    public boolean contains(@NonNull Item item) {
        return item.getFullName().startsWith(getItemPrefix());
    }

    /**
     * Finds an item that the current user can see, along with every folder containing it, as needed to open its
     * page. Not every item group checks the permission of the items it returns, so the item's own is checked too.
     *
     * @return the item, or {@code null} if there is none of that type or the current user cannot see it
     */
    static <T extends Item> @CheckForNull T visible(@NonNull String fullName, @NonNull Class<T> type) {
        // Null when the current user cannot read a folder containing the item
        T item = Jenkins.get().getItemByFullNameOrNull(fullName, type);
        return item != null && item.hasPermission(Item.READ) ? item : null;
    }

    private @NonNull ViewGroup getViewGroup() {
        return (ViewGroup) getOwner();
    }

    @Override
    public String getDisplayName() {
        return isRoot() ? Messages.Dashboard_DisplayName() : getOwner().getDisplayName();
    }

    /**
     * The page title: the display name, including the item groups containing the owner.
     */
    @Restricted(NoExternalUse.class)
    public String getFullDisplayName() {
        return isRoot() ? Messages.Dashboard_DisplayName() : getOwner().getFullDisplayName();
    }

    @Override
    public String getSearchUrl() {
        return getUrl();
    }

    /**
     * The URL of the page, relative to the root URL: empty for the root, otherwise the URL of the owner,
     * such as {@code job/team/}.
     */
    public String getUrl() {
        return isRoot() ? "" : getOwner().getUrl();
    }

    @Override
    public Search getSearch() {
        return getOwner() instanceof SearchableModelObject searchable ? searchable.getSearch() : Jenkins.get().getSearch();
    }

    @Override
    protected SearchIndexBuilder makeSearchIndex() {
        return new SearchIndexBuilder();
    }

    /**
     * The tabs to show, in extension order.
     */
    public @NonNull List<Tab> getDashboardTabs() {
        boolean root = isRoot();
        return getActions(Tab.class).stream()
                .filter(e -> e.getIconFileName() != null)
                .filter(e -> root || e instanceof ShownInItemGroups)
                .toList();
    }

    /**
     * The tab shown at the URL of the dashboard: the first one without a URL name, or else the first tab.
     * It is also reachable at {@code defaultTab/}, so that the endpoints of a tab without a URL name have a URL.
     */
    @Restricted(NoExternalUse.class)
    public @CheckForNull Tab getDefaultTab() {
        List<Tab> tabs = getDashboardTabs();
        return tabs.stream()
                .filter(t -> t.getUrlName() == null)
                .findFirst()
                .orElse(tabs.isEmpty() ? null : tabs.get(0));
    }

    /**
     * The same actions as the primary view of the owner, plus a menu of the owner's views.
     */
    @Override
    @Restricted(NoExternalUse.class)
    public List<Action> getAppBarActions() {
        View primaryView = getViewGroup().getPrimaryView();
        List<Action> actions = new ArrayList<>(primaryView.getAppBarActions());
        // View URLs are relative to the root URL, wherever the menu is opened
        actions.add(new BaseUrlAction(new ViewsAction(getViewGroup()), ""));
        actions.sort(Comparator.comparingInt((Action a) -> a.getGroup().getOrder())
                .thenComparing(a -> Objects.requireNonNullElse(a.getDisplayName(), "")));
        return actions;
    }

    @Override
    public ModelObjectWithContextMenu.ContextMenu doContextMenu(StaplerRequest2 request, StaplerResponse2 response) {
        return new ModelObjectWithContextMenu.ContextMenu().addAppBarActions(getAppBarActions(), request);
    }

    /**
     * Finds the tab for a URL token: a tab's URL name, or {@code defaultTab} for {@link #getDefaultTab()}.
     */
    @Restricted(NoExternalUse.class)
    public @CheckForNull Tab getTab(@NonNull String token) {
        if ("defaultTab".equals(token)) {
            return getDefaultTab();
        }
        for (Tab tab : getDashboardTabs()) {
            if (token.equals(tab.getUrlName())) {
                return tab;
            }
        }
        return null;
    }

    /**
     * The dashboard to show on a view's page: the dashboard of the view's owner, when the request is for a URL of
     * the owner itself, such as the root URL or {@code /view/all/job/team/}, which the owner serves through its
     * primary view. Otherwise {@code null}, such as for the view's own URL, {@code /view/all/} or
     * {@code /job/team/view/all/}.
     */
    @Restricted(NoExternalUse.class)
    public static @CheckForNull Dashboard forViewPage(@NonNull View view, @NonNull StaplerRequest2 request) {
        if (!(view.getOwner() instanceof ItemGroup<?> group)) {
            return null;
        }
        Dashboard dashboard = of(group);
        return dashboard != null && isShownAtOwnerUrl(view, request) ? dashboard : null;
    }

    /**
     * Whether the request reached a view through a URL of its owner, which hands over to its primary view without
     * adding to the URL, rather than through the view's own URL.
     */
    private static boolean isShownAtOwnerUrl(@NonNull View view, @NonNull StaplerRequest2 request) {
        List<Ancestor> ancestors = request.getAncestors();
        for (int i = ancestors.size() - 1; i > 0; i--) {
            if (ancestors.get(i).getObject() == view) {
                Ancestor owner = ancestors.get(i - 1);
                return owner.getObject() == view.getOwner() && owner.getUrl().equals(ancestors.get(i).getUrl());
            }
        }
        return false;
    }

    /**
     * Marks a tab that also shows on the dashboards of item groups other than {@link Jenkins}. Other tabs show on
     * the root dashboard only, so a tab that lists things from across Jenkins doesn't show them in an item group.
     *
     * <p>Such a tab must show only what is inside {@link Dashboard#getOwner()} of the dashboard it was created for,
     * for example by checking each item with {@link Dashboard#contains(Item)}, and must check that the current user
     * can see each item and the item groups containing it.
     */
    public interface ShownInItemGroups {}

    /**
     * Lists the views of the owner, so they are one click away from the dashboard.
     */
    @Restricted(NoExternalUse.class)
    static final class ViewsAction implements Action {

        private final ViewGroup owner;

        ViewsAction(@NonNull ViewGroup owner) {
            this.owner = owner;
        }

        @Override
        public String getIconFileName() {
            return "symbol-list";
        }

        @Override
        public String getDisplayName() {
            return Messages.Dashboard_Views();
        }

        @Override
        public String getUrlName() {
            return null;
        }

        @Override
        public Group getGroup() {
            // A submenu of the overflow menu, as the app bar does not show dropdowns as top-level buttons
            return Group.FIRST_IN_MENU;
        }

        @Override
        public Event getEvent() {
            List<Action> views = owner.getViews().stream()
                    .<Action>map(ViewLinkAction::new)
                    .toList();
            return DropdownEvent.of(views);
        }
    }

    private record ViewLinkAction(View view) implements Action {

        @Override
        public String getIconFileName() {
            return view.getIconFileName() == null ? "symbol-list" : view.getIconFileName();
        }

        @Override
        public String getDisplayName() {
            return view.getDisplayName();
        }

        @Override
        public String getUrlName() {
            return view.getViewUrl();
        }

        @Override
        public Event getEvent() {
            return LinkEvent.of(view.getViewUrl());
        }
    }
}
