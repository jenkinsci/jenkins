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
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.Items;
import hudson.model.ListView;
import hudson.model.Queue;
import hudson.model.User;
import hudson.util.OneShotEvent;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.StreamSupport;
import jenkins.model.Jenkins;
import jenkins.model.Tab;
import jenkins.model.TransientActionFactory;
import jenkins.model.experimentalflags.UserExperimentalFlagsProperty;
import org.htmlunit.html.DomElement;
import org.htmlunit.html.HtmlAnchor;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.html.HtmlTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.FakeChangeLogSCM;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.MockFolder;
import org.jvnet.hudson.test.TestBuilder;
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
        // Tests that need seeding from existing builds ask for it
        RecentRuns.get().reset(false);
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
            HtmlPage page = wc.goTo("");
            assertThat(tabNames(page), contains("Recent", "All"));
            assertThat(currentTab(page), is("Recent"));

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
    void recentTabShowsRunningQueuedAndFinishedBuilds() throws Exception {
        enableFlag("alice", "new-build-page.flag");
        enableFlag("admin", "new-build-page.flag");
        FreeStyleProject finished = j.createFreeStyleProject("finished");
        j.buildAndAssertSuccess(finished);

        FreeStyleProject queued = j.createFreeStyleProject("queued");
        queued.setAssignedLabel(j.jenkins.getLabel("no-such-agent"));
        queued.scheduleBuild2(0);

        FreeStyleProject running = j.createFreeStyleProject("running");
        Blocker blocker = new Blocker();
        running.getBuildersList().add(blocker);
        FreeStyleBuild runningBuild = running.scheduleBuild2(0).waitForStart();
        blocker.started.block(TimeUnit.SECONDS.toMillis(30));

        try {
            try (JenkinsRule.WebClient wc = j.createWebClient().login("alice")) {
                HtmlPage page = wc.goTo("");
                assertThat(sectionLabels(page, "running"), contains(startsWith("running")));
                assertThat(sectionLabels(page, "queued"), contains("queued"));
                assertThat(sectionLabels(page, "finished"), contains(startsWith("finished")));
                assertThat(page.querySelector("[data-testid=dashboard-tabs] .jenkins-badge").getTextContent().trim(), is("1"));

                // Rows show the run's app bar actions, and alice may not cancel
                assertThat(rowActions(page, "running", "job/running/1/"), not(containsString("Cancel")));
                assertThat(page.querySelector("[data-section=queued] .stop-button-link"), nullValue());
                assertThat(rowActions(page, "finished", "job/finished/1/"), notNullValue());

                // The refresh endpoint serves the same content. It is a fragment, whose scripts only run on the page.
                wc.getOptions().setJavaScriptEnabled(false);
                HtmlPage rows = (HtmlPage) wc.goTo("defaultTab/rows", null);
                assertThat(sectionLabels(rows, "running"), contains(startsWith("running")));
                assertThat(rows.querySelector("[data-section=finished] .app-build-item__icon svg"), notNullValue());
                // It also carries the tab's badge, so the tab bar can be updated
                String fragment = rows.getWebResponse().getContentAsString();
                assertThat(fragment, containsString("app-dashboard-recent__badge"));
                assertThat(fragment, containsString("1 build running"));
            }
            try (JenkinsRule.WebClient wc = j.createWebClient().login("admin")) {
                HtmlPage page = wc.goTo("");
                assertThat(rowActions(page, "running", "job/running/1/"), containsString("\"displayName\":\"Cancel\""));
                DomElement cancel = page.querySelector("[data-section=queued] .stop-button-link");
                assertThat(cancel.getAttribute("href"), containsString("queue/cancelItem?id="));
            }
        } finally {
            blocker.release.signal();
            j.waitForCompletion(runningBuild);
            Queue.Item item = j.jenkins.getQueue().getItem(queued);
            if (item != null) {
                j.jenkins.getQueue().cancel(item);
            }
        }
    }

    @Test
    void recentTabShowsTheFirstFewQueuedBuildsUntilExpanded() throws Exception {
        enableFlag("alice");
        for (int i = 0; i < 5; i++) {
            FreeStyleProject p = j.createFreeStyleProject("queued-" + i);
            p.setAssignedLabel(j.jenkins.getLabel("no-such-agent"));
            p.scheduleBuild2(0);
        }
        try (JenkinsRule.WebClient wc = j.createWebClient().login("alice")) {
            HtmlPage page = wc.goTo("");
            assertThat(sectionLabels(page, "queued"), hasSize(5));
            assertThat(page.querySelectorAll("[data-section=queued] .app-dashboard-section__extra"), hasSize(5 - RecentTab.COLLAPSED_QUEUE_SIZE));
            DomElement toggle = page.querySelector("[data-section=queued] .app-dashboard-section__toggle");
            assertThat(toggle.getTextContent().trim(), is("Show all 5"));
        } finally {
            j.jenkins.getQueue().clear();
        }
    }

    @Test
    void recentTabCountsEveryQueuedBuildAndListsTheUsersFirst() throws Exception {
        enableFlag("alice");
        int total = RecentTab.LIMIT + 5;
        for (int i = 0; i < total; i++) {
            FreeStyleProject p = j.createFreeStyleProject("queued-" + i);
            p.setAssignedLabel(j.jenkins.getLabel("no-such-agent"));
            // The last one is alice's, so it would fall past the limit if it were not ranked first
            Cause cause = i == total - 1 ? new Cause.UserIdCause("alice") : new Cause.UserIdCause("bob");
            p.scheduleBuild2(0, new CauseAction(cause));
        }
        try (JenkinsRule.WebClient wc = j.createWebClient().login("alice")) {
            HtmlPage page = wc.goTo("");
            List<String> labels = sectionLabels(page, "queued");
            assertThat(labels, hasSize(RecentTab.LIMIT));
            assertThat(labels.get(0), is("queued-" + (total - 1)));
            assertThat(page.querySelector("[data-section=queued] .app-dashboard-section__count").getTextContent(), is(Integer.toString(total)));
            assertThat(page.querySelector("[data-section=queued] .app-dashboard-section__toggle").getTextContent().trim(),
                    is("Show the first " + RecentTab.LIMIT));
        } finally {
            j.jenkins.getQueue().clear();
        }
    }

    @Test
    void recentTabListsBuildsTheUserStartedFirst() throws Exception {
        enableFlag("alice");
        enableFlag("bob");
        FreeStyleProject mine = j.createFreeStyleProject("mine");
        FreeStyleProject other = j.createFreeStyleProject("other");
        j.assertBuildStatusSuccess(mine.scheduleBuild2(0, new CauseAction(new Cause.UserIdCause("alice"))));
        j.buildAndAssertSuccess(other);

        try (JenkinsRule.WebClient wc = j.createWebClient().login("alice")) {
            HtmlPage page = wc.goTo("");
            assertThat(sectionLabels(page, "finished"), contains(startsWith("mine"), startsWith("other")));
            assertThat(page.getWebResponse().getContentAsString(), containsString("Started by you"));
        }
        try (JenkinsRule.WebClient wc = j.createWebClient().login("bob")) {
            HtmlPage page = wc.goTo("");
            assertThat(sectionLabels(page, "finished"), contains(startsWith("other"), startsWith("mine")));
            assertThat(page.getWebResponse().getContentAsString(), not(containsString("Started by you")));
        }
    }

    @Test
    void recentTabRanksTheUsersFinishedBuildsBeforeTheLimit() throws Exception {
        enableFlag("alice");
        // alice's build is the oldest, so it would fall past the limit if it were not ranked first
        FreeStyleProject mine = j.createFreeStyleProject("mine");
        j.assertBuildStatusSuccess(mine.scheduleBuild2(0, new CauseAction(new Cause.UserIdCause("alice"))));
        for (int i = 0; i < RecentTab.LIMIT; i++) {
            j.buildAndAssertSuccess(j.createFreeStyleProject("other-" + i));
        }

        try (JenkinsRule.WebClient wc = j.createWebClient().login("alice")) {
            List<String> finished = sectionLabels(wc.goTo(""), "finished");
            assertThat(finished, hasSize(RecentTab.LIMIT));
            assertThat(finished.get(0), startsWith("mine"));
        }
    }

    @Test
    void recentTabListsBuildsWithTheUsersChangesFirst() throws Exception {
        enableFlag("alice");
        enableFlag("bob");
        FreeStyleProject withChanges = j.createFreeStyleProject("with-changes");
        FakeChangeLogSCM scm = new FakeChangeLogSCM();
        scm.addChange().withAuthor("alice").withMsg("Fix the thing");
        withChanges.setScm(scm);
        j.buildAndAssertSuccess(withChanges);
        j.buildAndAssertSuccess(j.createFreeStyleProject("other"));

        try (JenkinsRule.WebClient wc = j.createWebClient().login("alice")) {
            HtmlPage page = wc.goTo("");
            assertThat(sectionLabels(page, "finished"), contains(startsWith("with-changes"), startsWith("other")));
            assertThat(page.getWebResponse().getContentAsString(), containsString("Includes your changes"));
        }
        try (JenkinsRule.WebClient wc = j.createWebClient().login("bob")) {
            HtmlPage page = wc.goTo("");
            assertThat(sectionLabels(page, "finished"), contains(startsWith("other"), startsWith("with-changes")));
            assertThat(page.getWebResponse().getContentAsString(), not(containsString("Includes your changes")));
        }
    }

    @Test
    void recentTabHidesBuildsOfJobsTheUserCannotRead() throws Exception {
        enableFlag("bob");
        FreeStyleProject visible = j.createFreeStyleProject("visible");
        FreeStyleProject hidden = j.createFreeStyleProject("hidden");
        j.buildAndAssertSuccess(visible);
        j.buildAndAssertSuccess(hidden);
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ).everywhere().to("bob")
                .grant(Item.READ).onItems(visible).to("bob"));

        try (JenkinsRule.WebClient wc = j.createWebClient().login("bob")) {
            assertThat(sectionLabels(wc.goTo(""), "finished"), contains(startsWith("visible")));
        }
    }

    @Test
    void recentTabLoadsForUsersWhoCanOnlyDiscoverSomeJobs() throws Exception {
        enableFlag("bob");
        FreeStyleProject visible = j.createFreeStyleProject("visible");
        FreeStyleProject discoverable = j.createFreeStyleProject("discoverable");
        j.buildAndAssertSuccess(visible);
        j.buildAndAssertSuccess(discoverable);
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ).everywhere().to("bob")
                .grant(Item.READ).onItems(visible).to("bob")
                .grant(Item.DISCOVER).onItems(discoverable).to("bob"));

        try (JenkinsRule.WebClient wc = j.createWebClient().login("bob")) {
            assertThat(sectionLabels(wc.goTo(""), "finished"), contains(startsWith("visible")));
        }
    }

    @Test
    void recentRunsKeepOneEntryPerJob() throws Exception {
        FreeStyleProject quiet = j.createFreeStyleProject("quiet");
        FreeStyleProject busy = j.createFreeStyleProject("busy");
        j.buildAndAssertSuccess(quiet);
        for (int i = 0; i < 3; i++) {
            j.buildAndAssertSuccess(busy);
        }
        assertThat(RecentRuns.get().getCompleted(10), contains(busy.getLastBuild(), quiet.getLastBuild()));

        // Deleting the remembered build falls back to the one before it
        busy.getLastBuild().delete();
        assertThat(RecentRuns.get().getCompleted(10), contains(busy.getLastBuild(), quiet.getLastBuild()));
    }

    @Test
    void recentTabShowsATeamsBuildsWhenOtherTeamsBuiltMoreRecently() throws Exception {
        enableFlag("bob");
        MockFolder mine = j.createFolder("mine");
        MockFolder others = j.createFolder("others");
        FreeStyleProject own = mine.createProject(FreeStyleProject.class, "own");
        j.buildAndAssertSuccess(own);
        int candidates = RecentTab.FINISHED_CANDIDATES;
        RecentTab.FINISHED_CANDIDATES = 2;
        try {
            // More builds elsewhere since, than the tab ranks
            for (int i = 0; i < 3; i++) {
                j.buildAndAssertSuccess(others.createProject(FreeStyleProject.class, "other-" + i));
            }
            j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                    .grant(Jenkins.READ).everywhere().to("bob")
                    .grant(Item.READ).onItems(mine, own).to("bob"));

            try (JenkinsRule.WebClient wc = j.createWebClient().login("bob")) {
                assertThat(sectionLabels(wc.goTo(""), "finished"), contains(startsWith("mine » own")));

                // Also after a restart, when the index is filled from existing builds
                RecentRuns.get().reset(true);
                RecentRuns.get().getCompleted(10);
                long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(30);
                while (RecentRuns.get().getCompleted(10).size() < 4 && System.currentTimeMillis() < deadline) {
                    Thread.sleep(100);
                }
                assertThat(sectionLabels(wc.goTo(""), "finished"), contains(startsWith("mine » own")));
            }
        } finally {
            RecentTab.FINISHED_CANDIDATES = candidates;
        }
    }

    @Test
    void recentRunsKeepTheHighestNumberedBuildWhenBuildsCompleteOutOfOrder() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject("p");
        FreeStyleBuild first = j.buildAndAssertSuccess(p);
        FreeStyleBuild second = j.buildAndAssertSuccess(p);

        // As if #1 completed after #2
        RecentRuns.get().add(new RecentRuns.Ref("p", first.getNumber(), System.currentTimeMillis() + 60_000));
        assertThat(RecentRuns.get().getCompleted(10), contains(second));

        // Deleting it falls back to the build before it, as after a restart
        second.delete();
        assertThat(RecentRuns.get().getCompleted(10), contains(first));
    }

    @Test
    void recentRunsKeepTheHighestNumberedBuildWhenAJobIsRenamed() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject("before");
        j.buildAndAssertSuccess(p);
        FreeStyleBuild second = j.buildAndAssertSuccess(p);

        // As if #2 was recorded under the new name before the rename was
        RecentRuns.get().reset(false);
        RecentRuns.get().add(new RecentRuns.Ref("before", 1, System.currentTimeMillis()));
        RecentRuns.get().add(new RecentRuns.Ref("after", second.getNumber(), System.currentTimeMillis()));
        p.renameTo("after");
        assertThat(RecentRuns.get().getCompleted(10), contains(second));
    }

    @Test
    void recentRunsRecoverWhenTheRememberedBuildIsGone() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject("p");
        FreeStyleBuild build = j.buildAndAssertSuccess(p);

        // As if the first load remembered a build that was deleted before it was added
        RecentRuns.get().add(new RecentRuns.Ref("p", build.getNumber() + 1, System.currentTimeMillis()));
        RecentRuns.get().getCompleted(10);
        assertThat(RecentRuns.get().getCompleted(10), contains(build));
    }

    @Test
    void recentRunsFollowRenamedAndDeletedJobs() throws Exception {
        MockFolder folder = j.createFolder("folder");
        FreeStyleProject inFolder = folder.createProject(FreeStyleProject.class, "p");
        FreeStyleProject renamed = j.createFreeStyleProject("before");
        j.buildAndAssertSuccess(inFolder);
        j.buildAndAssertSuccess(renamed);

        renamed.renameTo("after");
        folder.renameTo("moved");
        assertThat(RecentRuns.get().getCompleted(10), contains(renamed.getLastBuild(), inFolder.getLastBuild()));

        folder.delete();
        assertThat(RecentRuns.get().getCompleted(10), contains(renamed.getLastBuild()));
    }

    @Test
    void recentTabChecksEachJobInAFolder() throws Exception {
        enableFlag("bob");
        MockFolder shared = j.createFolder("shared");
        FreeStyleProject granted = shared.createProject(FreeStyleProject.class, "granted");
        FreeStyleProject sibling = shared.createProject(FreeStyleProject.class, "sibling");
        MockFolder closed = j.createFolder("closed");
        FreeStyleProject inside = closed.createProject(FreeStyleProject.class, "inside");
        for (FreeStyleProject p : List.of(granted, sibling, inside)) {
            j.buildAndAssertSuccess(p);
        }
        // As with a pattern-based strategy granting single jobs: bob can read one job in each folder, but only
        // the first folder
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ).everywhere().to("bob")
                .grant(Item.READ).onItems(shared, granted, inside).to("bob"));

        try (JenkinsRule.WebClient wc = j.createWebClient().login("bob")) {
            assertThat(sectionLabels(wc.goTo(""), "finished"), contains(startsWith("shared » granted")));
            // A job in a folder bob cannot read is not reachable anywhere else either
            wc.setThrowExceptionOnFailingStatusCode(false);
            assertThat(wc.goTo("job/closed/job/inside/").getWebResponse().getStatusCode(), is(404));
        }
    }

    @Test
    void recentTabHidesRunningAndQueuedBuildsInFoldersTheUserCannotRead() throws Exception {
        enableFlag("bob");
        MockFolder open = j.createFolder("open");
        FreeStyleProject openRunning = open.createProject(FreeStyleProject.class, "running");
        FreeStyleProject openQueued = open.createProject(FreeStyleProject.class, "queued");
        MockFolder closed = j.createFolder("closed");
        FreeStyleProject closedRunning = closed.createProject(FreeStyleProject.class, "running");
        FreeStyleProject closedQueued = closed.createProject(FreeStyleProject.class, "queued");
        // bob can read every job, but not the folder "closed", so its jobs' pages are out of reach
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ).everywhere().to("bob")
                .grant(Item.READ).onItems(open, openRunning, openQueued, closedRunning, closedQueued).to("bob"));

        for (FreeStyleProject p : List.of(openQueued, closedQueued)) {
            p.setAssignedLabel(j.jenkins.getLabel("no-such-agent"));
            p.scheduleBuild2(0);
        }
        Blocker openBlocker = new Blocker();
        Blocker closedBlocker = new Blocker();
        openRunning.getBuildersList().add(openBlocker);
        closedRunning.getBuildersList().add(closedBlocker);
        FreeStyleBuild openBuild = openRunning.scheduleBuild2(0).waitForStart();
        FreeStyleBuild closedBuild = closedRunning.scheduleBuild2(0).waitForStart();
        openBlocker.started.block(TimeUnit.SECONDS.toMillis(30));
        closedBlocker.started.block(TimeUnit.SECONDS.toMillis(30));

        try (JenkinsRule.WebClient wc = j.createWebClient().login("bob")) {
            HtmlPage page = wc.goTo("");
            assertThat(sectionLabels(page, "running"), contains(startsWith("open » running")));
            assertThat(sectionLabels(page, "queued"), contains("open » queued"));
            assertThat(page.querySelector("[data-testid=dashboard-tabs] .jenkins-badge").getTextContent().trim(), is("1"));
        } finally {
            openBlocker.release.signal();
            closedBlocker.release.signal();
            j.waitForCompletion(openBuild);
            j.waitForCompletion(closedBuild);
            for (FreeStyleProject p : List.of(openQueued, closedQueued)) {
                Queue.Item item = j.jenkins.getQueue().getItem(p);
                if (item != null) {
                    j.jenkins.getQueue().cancel(item);
                }
            }
        }
    }

    @Test
    void recentRunsForgetDeletedBuilds() throws Exception {
        FreeStyleProject[] projects = new FreeStyleProject[3];
        for (int i = 0; i < projects.length; i++) {
            projects[i] = j.createFreeStyleProject("p" + i);
            j.buildAndAssertSuccess(projects[i]);
        }
        List<String> names = RecentRuns.get().getCompleted(10).stream().map(r -> r.getParent().getName()).toList();
        assertThat(names, contains("p2", "p1", "p0"));

        projects[2].getLastBuild().delete();
        names = RecentRuns.get().getCompleted(10).stream().map(r -> r.getParent().getName()).toList();
        assertThat(names, contains("p1", "p0"));
    }

    @Test
    void recentRunsSeedFromExistingBuilds() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject("p");
        j.buildAndAssertSuccess(p);
        // As after a restart: the first read starts filling the index in the background
        RecentRuns.get().reset(true);
        RecentRuns.get().getCompleted(10);
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(30);
        while (RecentRuns.get().getCompleted(10).isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }
        assertThat(RecentRuns.get().getCompleted(10), contains(p.getLastBuild()));
    }

    @Test
    void pluginsCanContributeTabs() throws Exception {
        enableFlag("alice");
        j.createFreeStyleProject("starred");
        j.createFreeStyleProject("plain");
        j.createFolder("team").createProject(FreeStyleProject.class, "starred");
        try (JenkinsRule.WebClient wc = j.createWebClient().login("alice")) {
            assertThat(tabNames(wc.goTo("")), contains("Recent", "All", "Starred"));

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

    private void enableFlag(String user, String... otherFlags) throws Exception {
        Map<String, String> flags = new HashMap<>(Map.of("new-dashboard-page.flag", "true"));
        for (String flag : otherFlags) {
            flags.put(flag, "true");
        }
        User.getById(user, true).addProperty(new UserExperimentalFlagsProperty(flags));
    }

    /**
     * The app bar actions of the run in a row of the given section, as sent to the overflow menu.
     */
    private static String rowActions(HtmlPage page, String section, String runUrl) {
        DomElement overflow = page.querySelector(
                "[data-section=" + section + "] [data-type=auto-overflow][data-href$='" + runUrl + "']");
        assertThat(overflow, notNullValue());
        return ((HtmlTemplate) overflow.getNextElementSibling()).getContent().getTextContent();
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

    private static List<String> sectionLabels(HtmlPage page, String section) {
        return page.querySelectorAll("[data-section=" + section + "] .app-temporary-list__item__label").stream()
                .map(n -> n.getTextContent().trim().replaceAll("\\s+", " "))
                .toList();
    }

    private static String nextStart(HtmlPage page) {
        List<DomElement> pages = page.querySelectorAll(".app-dashboard-items__page").stream().map(DomElement.class::cast).toList();
        return pages.get(pages.size() - 1).getAttribute("data-next-start");
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
