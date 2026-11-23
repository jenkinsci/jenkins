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
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.Items;
import hudson.model.ListView;
import hudson.model.User;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;
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
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class DashboardTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.ADMINISTER).everywhere().to("admin")
                .grant(Jenkins.READ, Item.READ, Item.BUILD).everywhere().to("alice", "bob"));
    }

    @Test
    void rootShowsThePrimaryViewWithoutTheFlag() throws Exception {
        j.createFreeStyleProject("p");
        try (JenkinsRule.WebClient wc = j.createWebClient().login("alice")) {
            HtmlPage page = wc.goTo("");
            assertThat(page.querySelector("[data-testid=dashboard-tabs]"), nullValue());
            assertThat(page.getWebResponse().getContentAsString(), containsString("job/p/"));

            wc.setThrowExceptionOnFailingStatusCode(false);
            assertThat(wc.goTo("all/").getWebResponse().getStatusCode(), is(404));
        }
    }

    @Test
    void anonymousUsersSeeThePrimaryView() throws Exception {
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(Jenkins.READ).everywhere().toEveryone());
        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            assertThat(wc.goTo("").querySelector("[data-testid=dashboard-tabs]"), nullValue());
        }
    }

    @Test
    void rootShowsTheDashboardWithTheFlag() throws Exception {
        enableFlag("alice");
        j.createFreeStyleProject("p");
        try (JenkinsRule.WebClient wc = j.createWebClient().login("alice")) {
            // All is the only built-in tab, so it is also the default
            HtmlPage page = wc.goTo("");
            assertThat(tabNames(page), contains("All"));
            assertThat(currentTab(page), is("All"));
            assertThat(rowLabels(page), contains("p"));

            HtmlPage all = wc.goTo("all/");
            assertThat(currentTab(all), is("All"));
            assertThat(rowLabels(all), contains("p"));
        }
    }

    @Test
    void primaryViewUrlsKeepWorkingWithTheFlag() throws Exception {
        enableFlag("admin");
        j.createFreeStyleProject("p");
        try (JenkinsRule.WebClient wc = j.createWebClient().login("admin")) {
            // The view's own URL shows the view, not the dashboard
            HtmlPage view = wc.goTo("view/all/");
            assertThat(view.querySelector("[data-testid=dashboard-tabs]"), nullValue());
            assertThat(view.getWebResponse().getContentAsString(), containsString("job/p/"));

            // URLs served by the primary view through the root fallback
            wc.goTo("newJob");
            wc.goTo("builds");
            wc.goTo("api/json", "application/json");
        }
    }

    @Test
    void emptyStateLinksWorkFromTheAllTabUrl() throws Exception {
        enableFlag("admin");
        try (JenkinsRule.WebClient wc = j.createWebClient().login("admin")) {
            HtmlPage all = wc.goTo("all/");
            HtmlAnchor createJob = all.getAnchorByHref(j.contextPath + "/newJob");
            assertThat(createJob.click().getWebResponse().getStatusCode(), is(200));
            assertThat(all.getAnchorByHref(j.contextPath + "/computer/new"), notNullValue());
        }
    }

    @Test
    void allTabLoadsItemsAPageAtATime() throws Exception {
        enableFlag("alice");
        for (int i = 0; i < 120; i++) {
            j.createFreeStyleProject(String.format("job-%03d", i));
        }
        try (JenkinsRule.WebClient wc = j.createWebClient().login("alice")) {
            HtmlPage all = wc.goTo("all/");
            assertThat(rowLabels(all), hasSize(ItemListTab.PAGE_SIZE));
            assertThat(nextStart(all), is("50"));

            HtmlPage second = (HtmlPage) wc.goTo("all/items?start=50", null);
            assertThat(rowLabels(second), hasSize(ItemListTab.PAGE_SIZE));
            assertThat(rowLabels(second).get(0), is("job-050"));
            // Fragments are rendered with the page variables icons need
            assertThat(second.querySelector(".app-build-item__icon svg"), notNullValue());

            HtmlPage last = (HtmlPage) wc.goTo("all/items?start=100", null);
            assertThat(rowLabels(last), hasSize(20));
            assertThat(nextStart(last), is(""));
        }
    }

    @Test
    void allTabContinuesAfterTheLastItemShownWhenEarlierItemsAreDeleted() throws Exception {
        enableFlag("alice");
        for (int i = 0; i < 120; i++) {
            j.createFreeStyleProject(String.format("job-%03d", i));
        }
        try (JenkinsRule.WebClient wc = j.createWebClient().login("alice")) {
            // The first page showed job-000 to job-049, then job-000 was deleted
            j.jenkins.getItem("job-000").delete();
            HtmlPage second = (HtmlPage) wc.goTo("all/items?start=50&after=job-049", null);
            assertThat(rowLabels(second).get(0), is("job-050"));
            assertThat(nextStart(second), is("99"));

            // If the last item shown is gone too, the page starts at the position
            j.jenkins.getItem("job-049").delete();
            assertThat(rowLabels((HtmlPage) wc.goTo("all/items?start=48&after=job-049", null)).get(0), is("job-050"));
        }
    }

    @Test
    void emptyAllTabShowsTheWelcomePageWhenAListViewIsPrimary() throws Exception {
        enableFlag("admin");
        ListView primary = new ListView("mine", j.jenkins);
        j.jenkins.addView(primary);
        j.jenkins.setPrimaryView(primary);
        try (JenkinsRule.WebClient wc = j.createWebClient().login("admin")) {
            HtmlPage all = wc.goTo("all/");
            assertThat(all.getAnchorByHref(j.contextPath + "/newJob"), notNullValue());
            assertThat(all.getWebResponse().getContentAsString(), containsString("Welcome to Jenkins!"));
        }
    }

    @Test
    void allTabSearchLooksInsideFoldersAndRespectsPermissions() throws Exception {
        enableFlag("bob");
        MockFolder team = j.createFolder("team");
        MockFolder infra = team.createProject(MockFolder.class, "infra");
        infra.createProject(FreeStyleProject.class, "deploy-prod");
        FreeStyleProject secret = infra.createProject(FreeStyleProject.class, "deploy-secret");
        j.createFreeStyleProject("unrelated");
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ).everywhere().to("bob")
                .grant(Item.READ).onItems(team, infra, infra.getItem("deploy-prod"), j.jenkins.getItem("unrelated")).to("bob"));

        try (JenkinsRule.WebClient wc = j.createWebClient().login("bob")) {
            HtmlPage results = (HtmlPage) wc.goTo("all/items?start=0&search=deploy", null);
            assertThat(rowLabels(results), contains("team » infra » deploy-prod"));

            // The top level lists only what bob can read, and nothing from inside folders
            assertThat(rowLabels(wc.goTo("all/")), contains("team", "unrelated"));
        }
        assertThat(secret.hasPermission2(User.getById("bob", true).impersonate2(), Item.READ), is(false));
    }

    @Test
    void pluginsCanContributeTabs() throws Exception {
        enableFlag("alice");
        j.createFreeStyleProject("starred");
        j.createFreeStyleProject("plain");
        j.createFolder("team").createProject(FreeStyleProject.class, "starred");
        try (JenkinsRule.WebClient wc = j.createWebClient().login("alice")) {
            assertThat(tabNames(wc.goTo("")), contains("All", "Starred"));

            // Items from inside folders are shown with their full names
            HtmlPage starred = wc.goTo("starred/");
            assertThat(currentTab(starred), is("Starred"));
            assertThat(rowLabels(starred), containsInAnyOrder("starred", "team » starred"));
            assertThat(
                    rowLabels((HtmlPage) wc.goTo("starred/items?start=0", null)),
                    containsInAnyOrder("starred", "team » starred"));
        }
    }

    @Test
    void appBarIncludesTheViewsMenu() throws Exception {
        enableFlag("admin");
        try (JenkinsRule.WebClient wc = j.createWebClient().login("admin")) {
            assertThat(wc.goTo("").getWebResponse().getContentAsString(), containsString("New Item"));

            // The overflow menu lists the views
            String menu = wc.goTo("contextMenu", "application/json").getWebResponse().getContentAsString();
            assertThat(menu, containsString("\"displayName\":\"Views\""));
            assertThat(menu, containsString("view/all/"));
        }
    }

    private void enableFlag(String user) throws Exception {
        User.getById(user, true)
                .addProperty(new UserExperimentalFlagsProperty(Map.of("new-dashboard-page.flag", "true")));
    }

    private static List<String> tabNames(HtmlPage page) {
        return page.querySelectorAll("[data-testid=dashboard-tabs] a").stream()
                .map(n -> ((DomElement) n).getFirstChild() == null ? "" : textWithoutBadge((DomElement) n))
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

    private static String nextStart(HtmlPage page) {
        List<DomElement> pages = page.querySelectorAll(".app-dashboard-items__page").stream().map(DomElement.class::cast).toList();
        return pages.get(pages.size() - 1).getAttribute("data-next-start");
    }

    public static final class StarredTab extends ItemListTab {
        StarredTab(Dashboard dashboard) {
            super(dashboard);
        }

        @Override
        public String getIconFileName() {
            return "symbol-list";
        }

        @Override
        public String getDisplayName() {
            return "Starred";
        }

        @Override
        public String getUrlName() {
            return "starred";
        }

        @NonNull
        @Override
        public Iterable<? extends Item> getItems() {
            return StreamSupport.stream(Items.allItems(Jenkins.get(), Item.class).spliterator(), false)
                    .filter(i -> i.getName().equals("starred"))
                    .toList();
        }
    }

    @TestExtension("pluginsCanContributeTabs")
    public static final class StarredTabFactory extends TransientActionFactory<Dashboard> {
        @Override
        public Class<Dashboard> type() {
            return Dashboard.class;
        }

        @NonNull
        @Override
        public Collection<? extends Tab> createFor(@NonNull Dashboard target) {
            return List.of(new StarredTab(target));
        }
    }
}
