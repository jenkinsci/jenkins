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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractItem;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.model.Label;
import hudson.model.ListView;
import hudson.model.Queue;
import hudson.model.TopLevelItem;
import hudson.model.TopLevelItemDescriptor;
import hudson.model.User;
import hudson.model.View;
import hudson.model.ViewGroup;
import hudson.security.ACL;
import hudson.security.AccessControlled;
import hudson.util.OneShotEvent;
import hudson.views.DefaultViewsTabBar;
import hudson.views.ViewsTabBar;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import jenkins.model.Jenkins;
import jenkins.model.Tab;
import jenkins.model.TransientActionFactory;
import jenkins.model.experimentalflags.UserExperimentalFlagsProperty;
import org.htmlunit.html.DomElement;
import org.htmlunit.html.HtmlAnchor;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.MockFolder;
import org.jvnet.hudson.test.TestBuilder;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.StaplerFallback;

/**
 * The dashboard of an item group other than {@link Jenkins}, using {@link MockFolder}, which hands its URL over to
 * its primary view like the folders of plugins do.
 */
@WithJenkins
class FolderDashboardTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.ADMINISTER).everywhere().to("admin")
                .grant(Jenkins.READ, Item.READ, Item.BUILD).everywhere().to("alice", "bob"));
        RecentRuns.get().reset(false);
    }

    @Test
    void folderShowsItsViewWithoutTheFlag() throws Exception {
        MockFolder folder = createFolder(j.jenkins, "team");
        folder.createProject(FreeStyleProject.class, "p");
        try (JenkinsRule.WebClient wc = j.createWebClient().login("alice")) {
            HtmlPage page = wc.goTo("job/team/");
            assertThat(page.querySelector("[data-testid=dashboard-tabs]"), nullValue());
            assertThat(page.querySelector("a[href$='job/p/']"), notNullValue());

            wc.setThrowExceptionOnFailingStatusCode(false);
            assertThat(wc.goTo("job/team/all/").getWebResponse().getStatusCode(), is(404));
        }
    }

    @Test
    void folderShowsTheDashboardWithTheFlag() throws Exception {
        enableFlag("alice");
        MockFolder folder = createFolder(j.jenkins, "team");
        folder.createProject(FreeStyleProject.class, "p");
        MockFolder nested = createFolder(folder, "nested");
        nested.createProject(FreeStyleProject.class, "inner");
        j.createFreeStyleProject("outside");
        try (JenkinsRule.WebClient wc = j.createWebClient().login("alice")) {
            HtmlPage page = wc.goTo("job/team/");
            assertThat(tabNames(page), contains("Recent", "All"));
            assertThat(currentTab(page), is("Recent"));
            assertThat(page.querySelector("h1").getTextContent().trim(), is("team"));
            assertThat(tabLinks(page), contains(j.contextPath + "/job/team/", j.contextPath + "/job/team/all/"));
            // The refresh endpoint is the folder's
            assertThat(((DomElement) page.querySelector("#dashboard-recent")).getAttribute("data-rows-url"),
                    is(j.contextPath + "/job/team/defaultTab/rows"));

            HtmlPage all = wc.goTo("job/team/all/");
            assertThat(currentTab(all), is("All"));
            assertThat(rowLabels(all), contains("nested", "p"));
            assertThat(((DomElement) all.querySelector(".app-dashboard-items")).getAttribute("data-items-url"),
                    is(j.contextPath + "/job/team/all/items"));
            // The breadcrumbs lead back through the folder
            assertThat(all.querySelector("#breadcrumbs a[href='" + j.contextPath + "/job/team/']"), notNullValue());

            // A nested folder has its own dashboard
            HtmlPage inner = wc.goTo("job/team/job/nested/all/");
            assertThat(inner.querySelector("h1").getTextContent().trim(), is("nested"));
            assertThat(rowLabels(inner), contains("inner"));
            assertThat(inner.getTitleText(), startsWith("team » nested"));

            // The root is unchanged
            assertThat(rowLabels(wc.goTo("all/")), contains("outside", "team"));
        }
    }

    @Test
    void folderShowsTheDashboardWhereverItIsReachedFrom() throws Exception {
        enableFlag("alice");
        MockFolder folder = createFolder(j.jenkins, "team");
        folder.createProject(FreeStyleProject.class, "p");
        createFolder(folder, "nested").createProject(FreeStyleProject.class, "inner");
        try (JenkinsRule.WebClient wc = j.createWebClient().login("alice")) {
            // Views link to the items in them relative to the view
            for (String url : List.of("view/all/job/team/", "view/all/job/team/job/nested/", "job/team/view/All/job/nested/")) {
                assertThat(url, tabNames(wc.goTo(url)), contains("Recent", "All"));
            }
            assertThat(wc.goTo("job/team/view/All/").querySelector("[data-testid=dashboard-tabs]"), nullValue());
        }
    }

    @Test
    void folderViewsAndOtherUrlsKeepWorkingWithTheFlag() throws Exception {
        enableFlag("admin");
        MockFolder folder = createFolder(j.jenkins, "team");
        folder.createProject(FreeStyleProject.class, "p");
        ListView mine = new ListView("mine", folder);
        folder.addView(mine);
        try (JenkinsRule.WebClient wc = j.createWebClient().login("admin")) {
            // The views' own URLs show the views, not the dashboard
            for (String view : List.of("job/team/view/All/", "job/team/view/mine/")) {
                HtmlPage page = wc.goTo(view);
                assertThat(view, page.querySelector("[data-testid=dashboard-tabs]"), nullValue());
            }
            assertThat(wc.goTo("job/team/view/All/").querySelector("a[href$='job/p/']"), notNullValue());

            // The tabs are not reachable under a view's own URL
            wc.setThrowExceptionOnFailingStatusCode(false);
            assertThat(wc.goTo("job/team/view/mine/all/").getWebResponse().getStatusCode(), is(404));
            wc.setThrowExceptionOnFailingStatusCode(true);

            // URLs served by the folder, and by the primary view through the fallback
            wc.goTo("job/team/configure");
            wc.goTo("job/team/newJob");
            wc.goTo("job/team/builds");
            wc.goTo("job/team/api/json", "application/json");
            wc.goTo("job/team/job/p/");
        }
    }

    @Test
    void appBarHasTheFolderActionsAndViews() throws Exception {
        enableFlag("admin");
        MockFolder folder = createFolder(j.jenkins, "team");
        folder.createProject(FreeStyleProject.class, "p");
        folder.addView(new ListView("mine", folder));
        try (JenkinsRule.WebClient wc = j.createWebClient().login("admin")) {
            String page = wc.goTo("job/team/").getWebResponse().getContentAsString();
            assertThat(page, containsString("New Item"));
            assertThat(page, containsString("data-href=\"" + j.contextPath + "/job/team/\""));

            // The overflow menu lists the folder's views, by their URLs from the root
            String menu = wc.goTo("job/team/contextMenu", "application/json").getWebResponse().getContentAsString();
            assertThat(menu, containsString("\"displayName\":\"Views\""));
            assertThat(menu, containsString("job/team/view/mine/"));
            assertThat(menu, containsString("\"baseUrl\":\"" + j.contextPath + "/\""));
        }
    }

    @Test
    void emptyFolderShowsTheFolderEmptyState() throws Exception {
        enableFlag("admin");
        createFolder(j.jenkins, "empty");
        try (JenkinsRule.WebClient wc = j.createWebClient().login("admin")) {
            HtmlPage all = wc.goTo("job/empty/all/");
            assertThat(all.getWebResponse().getContentAsString(), containsString("This folder is empty"));
            assertThat(all.getWebResponse().getContentAsString(), not(containsString("Welcome to Jenkins!")));
            HtmlAnchor createJob = all.getAnchorByHref(j.contextPath + "/job/empty/newJob");
            assertThat(createJob.click().getWebResponse().getStatusCode(), is(200));

            HtmlPage recent = wc.goTo("job/empty/");
            assertThat(recent.getAnchorByHref(j.contextPath + "/job/empty/all/"), notNullValue());
        }
    }

    @Test
    void recentTabListsOnlyBuildsInsideTheFolder() throws Exception {
        enableFlag("alice");
        MockFolder team = createFolder(j.jenkins, "team");
        FreeStyleProject web = team.createProject(FreeStyleProject.class, "web");
        MockFolder backend = createFolder(team, "backend");
        FreeStyleProject api = backend.createProject(FreeStyleProject.class, "api");
        // Its name starts with the name of the other folder
        MockFolder teamB = createFolder(j.jenkins, "team-b");
        FreeStyleProject mobile = teamB.createProject(FreeStyleProject.class, "mobile");
        FreeStyleProject root = j.createFreeStyleProject("root");
        for (FreeStyleProject p : List.of(web, api, mobile, root)) {
            j.buildAndAssertSuccess(p);
        }

        FreeStyleProject queuedInside = team.createProject(FreeStyleProject.class, "queued-inside");
        queuedInside.setAssignedLabel(j.jenkins.getLabel("no-such-agent"));
        queuedInside.scheduleBuild2(0);
        FreeStyleProject queuedOutside = teamB.createProject(FreeStyleProject.class, "queued-outside");
        queuedOutside.setAssignedLabel(j.jenkins.getLabel("no-such-agent"));
        queuedOutside.scheduleBuild2(0);

        FreeStyleProject soak = backend.createProject(FreeStyleProject.class, "soak");
        Blocker blocker = new Blocker();
        soak.getBuildersList().add(blocker);
        FreeStyleBuild running = soak.scheduleBuild2(0).waitForStart();
        blocker.started.block(TimeUnit.SECONDS.toMillis(30));

        try (JenkinsRule.WebClient wc = j.createWebClient().login("alice")) {
            // Jobs are named from the folder down
            HtmlPage page = wc.goTo("job/team/");
            assertThat(sectionLabels(page, "running"), contains(startsWith("backend » soak")));
            assertThat(sectionLabels(page, "queued"), contains("queued-inside"));
            assertThat(page.querySelector("[data-section=queued] .app-dashboard-section__count").getTextContent(), is("1"));
            assertThat(sectionLabels(page, "finished"), contains(startsWith("backend » api"), startsWith("web")));
            assertThat(page.querySelector("[data-testid=dashboard-tabs] .jenkins-badge").getTextContent().trim(), is("1"));

            HtmlPage nested = wc.goTo("job/team/job/backend/");
            assertThat(sectionLabels(nested, "running"), contains(startsWith("soak")));
            assertThat(sectionLabels(nested, "queued"), empty());
            assertThat(sectionLabels(nested, "finished"), contains(startsWith("api")));

            HtmlPage other = wc.goTo("job/team-b/");
            assertThat(sectionLabels(other, "running"), empty());
            assertThat(sectionLabels(other, "queued"), contains("queued-outside"));
            assertThat(sectionLabels(other, "finished"), contains(startsWith("mobile")));
            assertThat(other.querySelector("[data-testid=dashboard-tabs] .jenkins-badge"), nullValue());

            // The refresh endpoint is filtered the same way
            wc.getOptions().setJavaScriptEnabled(false);
            HtmlPage rows = (HtmlPage) wc.goTo("job/team-b/defaultTab/rows", null);
            assertThat(sectionLabels(rows, "finished"), contains(startsWith("mobile")));

            // The root lists everything
            HtmlPage home = wc.goTo("");
            assertThat(sectionLabels(home, "finished"), contains(
                    startsWith("root"), startsWith("team-b » mobile"), startsWith("team » backend » api"), startsWith("team » web")));
        } finally {
            blocker.release.signal();
            j.waitForCompletion(running);
            j.jenkins.getQueue().clear();
        }
    }

    @Test
    void emptyGroupThatUsersCannotAddItemsToShowsNoNewItemLink() throws Exception {
        enableFlag("admin");
        j.jenkins.createProject(ComputedGroup.class, "computed");
        try (JenkinsRule.WebClient wc = j.createWebClient().login("admin")) {
            HtmlPage all = wc.goTo("job/computed/all/");
            String content = all.getWebResponse().getContentAsString();
            assertThat(content, containsString("Nothing to show yet"));
            assertThat(content, not(containsString("newJob")));
            assertThat(content, not(containsString("Welcome to Jenkins!")));
        }
    }

    @Test
    void queuedPartsOfJobsAreNamedFromTheFolderDown() throws Exception {
        enableFlag("alice");
        MockFolder team = createFolder(j.jenkins, "team");
        MockFolder backend = createFolder(team, "backend");
        FreeStyleProject api = backend.createProject(FreeStyleProject.class, "api");
        MockFolder other = createFolder(j.jenkins, "other");
        j.jenkins.getQueue().schedule2(new PartOfJob(api, j.jenkins.getLabel("no-such-agent")), 0);
        try (JenkinsRule.WebClient wc = j.createWebClient().login("alice")) {
            assertThat(sectionLabels(wc.goTo("job/team/"), "queued"), contains("backend » api » part"));
            assertThat(sectionLabels(wc.goTo("job/team/job/backend/"), "queued"), contains("api » part"));
            assertThat(sectionLabels(wc.goTo("job/other/"), "queued"), empty());
            assertThat(sectionLabels(wc.goTo(""), "queued"), contains("team » backend » api » part"));
        } finally {
            j.jenkins.getQueue().clear();
        }
    }

    @Test
    void recentRunsAreScopedToTheFolder() throws Exception {
        MockFolder team = createFolder(j.jenkins, "team");
        FreeStyleProject inside = team.createProject(FreeStyleProject.class, "inside");
        FreeStyleProject outside = j.createFreeStyleProject("team-outside");
        j.buildAndAssertSuccess(inside);
        j.buildAndAssertSuccess(outside);
        assertThat(RecentRuns.get().getCompleted(10, "team/"), contains(inside.getLastBuild()));
        assertThat(RecentRuns.get().getCompleted(10, ""), contains(outside.getLastBuild(), inside.getLastBuild()));
    }

    @Test
    void allTabSearchIsScopedToTheFolder() throws Exception {
        enableFlag("bob");
        MockFolder team = createFolder(j.jenkins, "team");
        MockFolder infra = createFolder(team, "infra");
        infra.createProject(FreeStyleProject.class, "deploy-prod");
        FreeStyleProject secret = infra.createProject(FreeStyleProject.class, "deploy-secret");
        team.createProject(FreeStyleProject.class, "build");
        j.createFreeStyleProject("deploy-elsewhere");
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ).everywhere().to("bob")
                .grant(Item.READ).onItems(team, infra, infra.getItem("deploy-prod"), team.getItem("build"),
                        j.jenkins.getItem("deploy-elsewhere")).to("bob"));

        try (JenkinsRule.WebClient wc = j.createWebClient().login("bob")) {
            HtmlPage results = (HtmlPage) wc.goTo("job/team/all/items?start=0&search=deploy", null);
            // Results are named from the folder down
            assertThat(rowLabels(results), contains("infra » deploy-prod"));

            // Names are matched from the folder down, so the folder's own name does not match everything in it
            assertThat(rowLabels((HtmlPage) wc.goTo("job/team/all/items?start=0&search=team", null)), empty());
            assertThat(rowLabels((HtmlPage) wc.goTo("job/team/all/items?start=0&search=infra", null)),
                    contains("infra", "infra » deploy-prod"));

            assertThat(rowLabels(wc.goTo("job/team/all/")), contains("build", "infra"));
        }
        assertThat(secret.hasPermission2(User.getById("bob", true).impersonate2(), Item.READ), is(false));
    }

    @Test
    void recentTabHidesBuildsTheUserCannotRead() throws Exception {
        enableFlag("bob");
        MockFolder team = createFolder(j.jenkins, "team");
        FreeStyleProject visible = team.createProject(FreeStyleProject.class, "visible");
        FreeStyleProject hidden = team.createProject(FreeStyleProject.class, "hidden");
        MockFolder closed = createFolder(j.jenkins, "closed");
        closed.createProject(FreeStyleProject.class, "inside");
        j.buildAndAssertSuccess(visible);
        j.buildAndAssertSuccess(hidden);
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ).everywhere().to("bob")
                .grant(Item.READ).onItems(team, visible).to("bob"));

        try (JenkinsRule.WebClient wc = j.createWebClient().login("bob")) {
            assertThat(sectionLabels(wc.goTo("job/team/"), "finished"), contains(startsWith("visible")));
            assertThat(rowLabels(wc.goTo("job/team/all/")), contains("visible"));

            wc.setThrowExceptionOnFailingStatusCode(false);
            assertThat(wc.goTo("job/closed/").getWebResponse().getStatusCode(), is(404));
            assertThat(wc.goTo("job/closed/all/").getWebResponse().getStatusCode(), is(404));
        }
    }

    @Test
    void groupsWithoutAPrimaryViewHaveNoDashboard() throws Exception {
        enableFlag("alice");
        ComputedGroup group = j.jenkins.createProject(ComputedGroup.class, "viewless");
        group.viewless = true;
        assertThat(Dashboard.of(group), nullValue());
        // Its context menu falls back to the item's own menu, rather than the dashboard's
        try (JenkinsRule.WebClient wc = j.createWebClient().login("alice")) {
            wc.goTo("job/viewless/contextMenu", "application/json");
        }
    }

    @Test
    void pluginTabsShowOnFoldersWhenTheyOptIn() throws Exception {
        enableFlag("alice");
        MockFolder team = createFolder(j.jenkins, "team");
        team.createProject(FreeStyleProject.class, "p");
        try (JenkinsRule.WebClient wc = j.createWebClient().login("alice")) {
            assertThat(tabNames(wc.goTo("")), contains("Recent", "All", "Root only", "Everywhere"));
            assertThat(tabNames(wc.goTo("job/team/")), contains("Recent", "All", "Everywhere"));

            HtmlPage everywhere = wc.goTo("job/team/everywhere/");
            assertThat(currentTab(everywhere), is("Everywhere"));
            assertThat(rowLabels(everywhere), contains("p"));

            wc.setThrowExceptionOnFailingStatusCode(false);
            assertThat(wc.goTo("job/team/root-only/").getWebResponse().getStatusCode(), is(404));
        }
    }

    private MockFolder createFolder(ItemGroup<?> parent, String name) throws Exception {
        if (parent instanceof MockFolder folder) {
            return folder.createProject(MockFolder.class, name);
        }
        return j.createFolder(name);
    }

    private void enableFlag(String user) throws Exception {
        User.getById(user, true)
                .addProperty(new UserExperimentalFlagsProperty(Map.of("new-dashboard-page.flag", "true")));
    }

    private static List<String> tabNames(HtmlPage page) {
        return page.querySelectorAll("[data-testid=dashboard-tabs] a").stream()
                .map(n -> textWithoutBadge((DomElement) n))
                .toList();
    }

    private static List<String> tabLinks(HtmlPage page) {
        return page.querySelectorAll("[data-testid=dashboard-tabs] a").stream()
                .map(n -> ((DomElement) n).getAttribute("href"))
                .toList();
    }

    private static String currentTab(HtmlPage page) {
        return textWithoutBadge((DomElement) page.querySelector("[data-testid=dashboard-tabs] a[aria-current=page]"));
    }

    private static String textWithoutBadge(DomElement tab) {
        String text = tab.getTextContent();
        DomElement badge = tab.querySelector(".jenkins-badge");
        if (badge != null) {
            text = text.replace(badge.getTextContent(), "");
        }
        return text.trim();
    }

    private static List<String> rowLabels(HtmlPage page) {
        return page.querySelectorAll(".app-temporary-list__item__label").stream()
                .map(n -> n.getTextContent().trim().replaceAll("\\s+", " "))
                .toList();
    }

    private static List<String> sectionLabels(HtmlPage page, String section) {
        return page.querySelectorAll("[data-section=" + section + "] .app-temporary-list__item__label").stream()
                .map(n -> n.getTextContent().trim().replaceAll("\\s+", " "))
                .toList();
    }

    private static final class Blocker extends TestBuilder {
        final OneShotEvent started = new OneShotEvent();
        final OneShotEvent release = new OneShotEvent();

        @Override
        public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException {
            started.signal();
            release.block();
            return true;
        }
    }

    /**
     * A part of a job that is queued on its own, like a Pipeline {@code node} block, with the permissions of the job.
     */
    private static final class PartOfJob implements Queue.Task, AccessControlled {
        private final FreeStyleProject owner;
        private final Label label;

        PartOfJob(FreeStyleProject owner, Label label) {
            this.owner = owner;
            this.label = label;
        }

        @Override
        public Queue.Task getOwnerTask() {
            return owner;
        }

        @Override
        public Label getAssignedLabel() {
            return label;
        }

        @Override
        public String getName() {
            return "part";
        }

        @Override
        public String getDisplayName() {
            return "part";
        }

        @Override
        public String getFullDisplayName() {
            return owner.getFullDisplayName() + " » part";
        }

        @Override
        public String getUrl() {
            return owner.getUrl();
        }

        @Override
        public Queue.Executable createExecutable() {
            return null;
        }

        @NonNull
        @Override
        public ACL getACL() {
            return owner.getACL();
        }
    }

    /**
     * A group whose items users cannot add themselves, and whose primary view is not an "All" view, like a
     * multibranch project. Like a folder, it hands its URL over to its primary view, which is all it takes to show
     * the dashboard.
     */
    public static final class ComputedGroup extends AbstractItem
            implements TopLevelItem, ItemGroup<TopLevelItem>, ViewGroup, StaplerFallback {
        private transient ListView view;

        // Whether it has no views at all
        private transient boolean viewless;

        ComputedGroup(ItemGroup<?> parent, String name) {
            super(parent, name);
        }

        private synchronized ListView view() {
            if (view == null) {
                view = new ListView("branches", this);
            }
            return view;
        }

        @Override
        public Object getStaplerFallback() {
            return view();
        }

        @Override
        public Collection<TopLevelItem> getItems() {
            return List.of();
        }

        @Override
        public String getUrlChildPrefix() {
            return "job";
        }

        @Override
        public TopLevelItem getItem(String name) {
            return null;
        }

        @Override
        public File getRootDirFor(TopLevelItem child) {
            return new File(getRootDir(), child.getName());
        }

        @Override
        public void onDeleted(TopLevelItem item) {}

        @Override
        public Collection<? extends Job> getAllJobs() {
            return List.of();
        }

        @Override
        public boolean canDelete(View view) {
            return false;
        }

        @Override
        public void deleteView(View view) {}

        @Override
        public Collection<View> getViews() {
            return viewless ? List.of() : List.of(view());
        }

        @Override
        public View getView(String name) {
            return !viewless && view().getViewName().equals(name) ? view() : null;
        }

        @Override
        public View getPrimaryView() {
            return viewless ? null : view();
        }

        @Override
        public void onViewRenamed(View view, String oldName, String newName) {}

        @Override
        public ViewsTabBar getViewsTabBar() {
            return new DefaultViewsTabBar();
        }

        @Override
        public ItemGroup<? extends TopLevelItem> getItemGroup() {
            return this;
        }

        @Override
        public List<Action> getViewActions() {
            return List.of();
        }

        @Override
        public TopLevelItemDescriptor getDescriptor() {
            return (TopLevelItemDescriptor) Jenkins.get().getDescriptorOrDie(ComputedGroup.class);
        }

        @TestExtension({"emptyGroupThatUsersCannotAddItemsToShowsNoNewItemLink", "groupsWithoutAPrimaryViewHaveNoDashboard"})
        public static final class DescriptorImpl extends TopLevelItemDescriptor {
            @Override
            public TopLevelItem newInstance(ItemGroup parent, String name) {
                return new ComputedGroup(parent, name);
            }
        }
    }

    public static final class RootOnlyTab extends ItemListTab {
        RootOnlyTab(Dashboard dashboard) {
            super(dashboard);
        }

        @Override
        public String getIconFileName() {
            return "symbol-list";
        }

        @Override
        public String getDisplayName() {
            return "Root only";
        }

        @Override
        public String getUrlName() {
            return "root-only";
        }

        @NonNull
        @Override
        public Iterable<? extends Item> getItems() {
            return List.of();
        }
    }

    public static final class EverywhereTab extends ItemListTab implements Dashboard.ShownInItemGroups {
        EverywhereTab(Dashboard dashboard) {
            super(dashboard);
        }

        @Override
        public String getIconFileName() {
            return "symbol-list";
        }

        @Override
        public String getDisplayName() {
            return "Everywhere";
        }

        @Override
        public String getUrlName() {
            return "everywhere";
        }

        @NonNull
        @Override
        public Iterable<? extends Item> getItems() {
            return getDashboard().getOwner().getItems();
        }
    }

    @TestExtension("pluginTabsShowOnFoldersWhenTheyOptIn")
    public static final class PluginTabFactory extends TransientActionFactory<Dashboard> {
        @Override
        public Class<Dashboard> type() {
            return Dashboard.class;
        }

        @NonNull
        @Override
        public Collection<? extends Tab> createFor(@NonNull Dashboard target) {
            return List.of(new RootOnlyTab(target), new EverywhereTab(target));
        }
    }
}
