package hudson.model;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.Launcher;
import hudson.model.Cause.UserIdCause;
import hudson.model.Queue.BlockedItem;
import hudson.model.Queue.BuildableItem;
import hudson.model.Queue.LeftItem;
import hudson.model.Queue.WaitingItem;
import hudson.model.queue.QueueListener;
import hudson.model.queue.ScheduleResult;
import hudson.util.OneShotEvent;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestBuilder;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
public class QueueFSMTest {

    private JenkinsRule r;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        r = rule;
    }

    // STATE COVERAGE: visit all 5 states in one test
    @Test
    void stateCoverage_visitAllStates() throws Exception {
        final OneShotEvent buildStarted = new OneShotEvent();
        final OneShotEvent buildRelease = new OneShotEvent();

        FreeStyleProject p = r.createFreeStyleProject();
        p.getBuildersList().add(new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher,
                    BuildListener listener) throws InterruptedException {
                buildStarted.signal();
                buildRelease.block();
                return true;
            }
        });

        // S1: Waiting
        ScheduleResult result = r.jenkins.getQueue().schedule2(p, 10);
        assertTrue(result.isCreated());
        assertThat(result.getCreateItem(), instanceOf(WaitingItem.class));
        r.jenkins.getQueue().cancel(p);

        // S2: Blocked (second build while first is running)
        p.scheduleBuild2(0).waitForStart();
        buildStarted.block();
        p.scheduleBuild2(0, new UserIdCause());
        r.jenkins.getQueue().scheduleMaintenance().get();
        Queue.Item item = r.jenkins.getQueue().getItem(p);
        assertThat(item, instanceOf(BlockedItem.class));
        r.jenkins.getQueue().cancel(item);
        buildRelease.signal();
        r.waitUntilNoActivity();

        // S3: Buildable (no matching agent)
        p.setAssignedLabel(Label.get("no-such-agent"));
        p.scheduleBuild2(0, new UserIdCause());
        await().atMost(Duration.ofSeconds(10)).until(() -> {
            Queue.Item qi = r.jenkins.getQueue().getItem(p);
            return qi instanceof BuildableItem;
        });
        assertThat(r.jenkins.getQueue().getItem(p), instanceOf(BuildableItem.class));
        r.jenkins.getQueue().cancel(p);

        // S4 + S5: Pending then Left (normal build)
        p.setAssignedLabel(null);
        p.getBuildersList().clear();
        FreeStyleBuild build = r.buildAndAssertSuccess(p);
        assertNotNull(build);
        assertThat(r.jenkins.getQueue().getItem(p), is(nullValue()));
    }

    // TRANSITION COVERAGE: T1->T3->T4->T6->T7
    @Test
    void transitionCoverage_blockedThenUnblocked() throws Exception {
        final OneShotEvent buildStarted = new OneShotEvent();
        final OneShotEvent buildRelease = new OneShotEvent();

        FreeStyleProject p = r.createFreeStyleProject();
        p.getBuildersList().add(new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher,
                    BuildListener listener) throws InterruptedException {
                buildStarted.signal();
                buildRelease.block();
                return true;
            }
        });

        // T1: schedule -> Waiting, quiet period expires
        p.scheduleBuild2(0).waitForStart();
        buildStarted.block();

        // T3: Waiting -> Blocked (same project already running)
        Future<FreeStyleBuild> secondBuild = p.scheduleBuild2(0, new UserIdCause());
        r.jenkins.getQueue().scheduleMaintenance().get();
        assertThat(r.jenkins.getQueue().getItem(p), instanceOf(BlockedItem.class));

        // T4->T6->T7: Blocked -> Buildable -> Pending -> Left
        buildRelease.signal();
        FreeStyleBuild build2 = secondBuild.get(30, TimeUnit.SECONDS);
        assertNotNull(build2);
        r.waitUntilNoActivity();
        assertTrue(r.jenkins.getQueue().isEmpty());
    }

    // TRANSITION-PAIR COVERAGE: T1->T2->T11 (schedule -> Waiting -> Buildable ->
    // cancel)
    @Test
    void transitionPairCoverage_scheduleThenCancel() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        p.setAssignedLabel(Label.get("no-such-agent"));

        // T1: schedule -> Waiting
        ScheduleResult result = r.jenkins.getQueue().schedule2(p, 0);
        assertTrue(result.isCreated());

        // T2: Waiting -> Buildable (quiet period expires, no agent to run it)
        await().atMost(Duration.ofSeconds(10)).until(() -> {
            Queue.Item qi = r.jenkins.getQueue().getItem(p);
            return qi instanceof BuildableItem;
        });
        assertThat(r.jenkins.getQueue().getItem(p), instanceOf(BuildableItem.class));

        // T11: Buildable -> Left (cancel)
        assertTrue(r.jenkins.getQueue().cancel(p));
        assertThat(r.jenkins.getQueue().getItem(p), is(nullValue()));
    }

    // QUEUE LISTENER CALLBACK: verify callbacks fire during a build
    @Test
    void queueListenerCallbacks_happyPath() throws Exception {
        FSMTrackingListener.reset();

        FreeStyleProject p = r.createFreeStyleProject();
        r.buildAndAssertSuccess(p);

        List<String> events = FSMTrackingListener.getEvents();
        assertTrue(events.contains("onEnterWaiting"), "Missing onEnterWaiting: " + events);
        assertTrue(events.contains("onLeaveWaiting"), "Missing onLeaveWaiting: " + events);
        assertTrue(events.contains("onEnterBuildable"), "Missing onEnterBuildable: " + events);
        assertTrue(events.contains("onLeaveBuildable"), "Missing onLeaveBuildable: " + events);
        assertTrue(events.contains("onLeft"), "Missing onLeft: " + events);
    }

    @TestExtension
    public static class FSMTrackingListener extends QueueListener {
        private static final List<String> events = Collections.synchronizedList(new ArrayList<>());

        public static void reset() {
            events.clear();
        }

        public static List<String> getEvents() {
            return new ArrayList<>(events);
        }

        @Override
        public void onEnterWaiting(WaitingItem wi) {
            events.add("onEnterWaiting");
        }

        @Override
        public void onLeaveWaiting(WaitingItem wi) {
            events.add("onLeaveWaiting");
        }

        @Override
        public void onEnterBlocked(BlockedItem bi) {
            events.add("onEnterBlocked");
        }

        @Override
        public void onLeaveBlocked(BlockedItem bi) {
            events.add("onLeaveBlocked");
        }

        @Override
        public void onEnterBuildable(BuildableItem bi) {
            events.add("onEnterBuildable");
        }

        @Override
        public void onLeaveBuildable(BuildableItem bi) {
            events.add("onLeaveBuildable");
        }

        @Override
        public void onLeft(LeftItem li) {
            events.add("onLeft");
        }
    }
}
