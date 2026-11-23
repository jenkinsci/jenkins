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
import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.Action;
import hudson.model.Actionable;
import hudson.model.View;
import hudson.search.Search;
import hudson.search.SearchIndexBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import jenkins.model.Jenkins;
import jenkins.model.ModelObjectWithContextMenu;
import jenkins.model.Tab;
import jenkins.model.experimentalflags.NewDashboardPageUserExperimentalFlag;
import jenkins.model.menu.Group;
import jenkins.model.menu.event.DropdownEvent;
import jenkins.model.menu.event.Event;
import jenkins.model.menu.event.LinkEvent;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;

/**
 * The home page of the experimental dashboard, shown at the root URL to users who have enabled
 * {@link NewDashboardPageUserExperimentalFlag}.
 *
 * <p>The page is made of {@link Tab}s. Plugins can add a tab by implementing
 * {@link jenkins.model.TransientActionFactory} for this type, typically returning an {@link ItemListTab}.
 * Actions added with {@link #addAction(Action)} are not saved and do not survive a restart.
 *
 * <p>The root URL belongs to the {@linkplain Jenkins#getPrimaryView() primary view}, whose page hands over to
 * this one when the flag is on. Tabs are reachable at {@code /<urlName>/} through {@link Jenkins#getDynamic(String)},
 * which consults them only after its own actions, so a tab cannot hide a root action.
 *
 * @since TODO
 */
@Extension
@Restricted(Beta.class)
public final class Dashboard extends Actionable {

    public static @NonNull Dashboard get() {
        return ExtensionList.lookupSingleton(Dashboard.class);
    }

    /**
     * Whether the current user sees this page at the root URL.
     */
    public static boolean isEnabled() {
        return new NewDashboardPageUserExperimentalFlag().getFlagValue();
    }

    @Override
    public String getDisplayName() {
        return Messages.Dashboard_DisplayName();
    }

    @Override
    public String getSearchUrl() {
        return "";
    }

    public String getUrl() {
        return "";
    }

    @Override
    public Search getSearch() {
        return Jenkins.get().getSearch();
    }

    @Override
    protected SearchIndexBuilder makeSearchIndex() {
        return new SearchIndexBuilder();
    }

    /**
     * The tabs to show, in extension order.
     */
    public @NonNull List<Tab> getDashboardTabs() {
        return getActions(Tab.class).stream().filter(e -> e.getIconFileName() != null).toList();
    }

    /**
     * The tab shown at the root URL: the first one without a URL name, or else the first tab.
     * It is also reachable at {@code /defaultTab/}, so that the endpoints of a tab without a URL name have a URL.
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
     * The same actions as the primary view, plus a menu of the configured views.
     */
    @Override
    @Restricted(NoExternalUse.class)
    public List<Action> getAppBarActions() {
        View primaryView = Jenkins.get().getPrimaryView();
        List<Action> actions = new ArrayList<>(primaryView.getAppBarActions());
        actions.add(new ViewsAction());
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
     * Whether a request is for the root URL, rather than for a view's own URL such as {@code /view/all/}.
     */
    @Restricted(NoExternalUse.class)
    public static boolean isRootRequest(@NonNull StaplerRequest2 request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        // Ignore path parameters such as ;jsessionid=…
        int parameters = path.indexOf(';');
        if (parameters >= 0) {
            path = path.substring(0, parameters);
        }
        return path.isEmpty() || path.equals("/");
    }

    /**
     * Lists the views of the Jenkins root, so they are one click away from the home page.
     */
    @Restricted(NoExternalUse.class)
    static final class ViewsAction implements Action {

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
            List<Action> views = Jenkins.get().getViews().stream()
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
