package hudson.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.Streams;
import hudson.model.queue.QueueTaskFuture;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.SortedMap;
import java.util.Spliterator;
import java.util.TreeMap;
import java.util.logging.Level;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.RunLoadCounter;
import org.jvnet.hudson.test.SleepBuilder;

public class RunMapTest {

    @Rule public JenkinsRule r = new JenkinsRule();
    @Rule public LoggerRule logs = new LoggerRule();

    /**
     * Makes sure that reloading the project while a build is in progress won't clobber that in-progress build.
     */
    @Issue("JENKNS-12318")
    @Test public void reloadWhileBuildIsInProgress() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();

        // want some completed build records
        FreeStyleBuild b1 = r.buildAndAssertSuccess(p);

        // now create a build that hangs until we signal the OneShotEvent
        p.getBuildersList().add(new SleepBuilder(Long.MAX_VALUE));
        FreeStyleBuild b2 = p.scheduleBuild2(0).waitForStart();
        assertEquals(2, b2.number);

        // now reload
        p.updateByXml((Source) new StreamSource(p.getConfigFile().getFile()));

        // we should still see the same object for #2 because that's in progress
        assertSame(p.getBuildByNumber(b2.number), b2);
        // build #1 should be reloaded
        assertNotSame(b1, p.getBuildByNumber(1));

        // and reference gets fixed up
        b1 = p.getBuildByNumber(1);
        assertSame(b1.getNextBuild(), b2);
        assertSame(b2.getPreviousBuild(), b1);
        b2.doStop();
        r.assertBuildStatus(Result.ABORTED, r.waitForCompletion(b2));
    }

    @Issue("JENKINS-27530")
    @Test public void reloadWhileBuildIsInQueue() throws Exception {
        logs.record(Queue.class, Level.FINE);
        FreeStyleProject p = r.createFreeStyleProject("p");
        p.getBuildersList().add(new SleepBuilder(Long.MAX_VALUE));
        r.jenkins.setNumExecutors(1);
        assertEquals(1, p.scheduleBuild2(0).waitForStart().number);
        p.scheduleBuild2(0);
        // Note that the bug does not reproduce simply from p.doReload(), since in that case Job identity remains intact:
        r.jenkins.reload();
        p = r.jenkins.getItemByFullName("p", FreeStyleProject.class);
        FreeStyleBuild b1 = p.getLastBuild();
        assertEquals(1, b1.getNumber());
        /* Currently fails since Run.project is final. But anyway that is not the problem:
        assertEquals(p, b1.getParent());
        */
        Queue.Item[] items = Queue.getInstance().getItems();
        assertEquals(1, items.length);
        assertEquals(p, items[0].task); // the real issue: assignBuildNumber was being called on the wrong Job
        QueueTaskFuture<Queue.Executable> b2f = items[0].getFuture();
        b1.getExecutor().interrupt();
        r.assertBuildStatus(Result.ABORTED, r.waitForCompletion(b1));
        FreeStyleBuild b2 = (FreeStyleBuild) b2f.waitForStart();
        assertEquals(2, b2.getNumber());
        assertEquals(p, b2.getParent());
        b2.getExecutor().interrupt();
        r.assertBuildStatus(Result.ABORTED, r.waitForCompletion(b2));
        FreeStyleBuild b3 = p.scheduleBuild2(0).waitForStart();
        assertEquals(3, b3.getNumber());
        assertEquals(p, b3.getParent());
        b3.getExecutor().interrupt();
        r.assertBuildStatus(Result.ABORTED, r.waitForCompletion(b3));
    }

    /**
     * Testing if the lazy loading can gracefully tolerate a RuntimeException during unmarshalling.
     */
    @Issue("JENKINS-15533")
    @Test public void runtimeExceptionInUnmarshalling() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        FreeStyleBuild b = r.buildAndAssertSuccess(p);
        b.addAction(new BombAction());
        b.save();

        p._getRuns().purgeCache();
        b = p.getBuildByNumber(b.number);
        // Original test assumed that b == null, but after JENKINS-21024 this is no longer true,
        // so this may not really be testing anything interesting:
        assertNotNull(b);
        assertNull(b.getAction(BombAction.class));
        assertTrue(bombed);
    }

    public static class BombAction extends InvisibleAction {
        private Object readResolve() {
            bombed = true;
            throw new NullPointerException();
        }
    }

    private static boolean bombed;

    @Issue("JENKINS-25788")
    @Test public void remove() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        FreeStyleBuild b1 = r.buildAndAssertSuccess(p);
        FreeStyleBuild b2 = r.buildAndAssertSuccess(p);
        RunMap<FreeStyleBuild> runs = p._getRuns();
        assertEquals(2, runs.size());
        assertTrue(runs.remove(b1));
        assertEquals(1, runs.size());
        assertFalse(runs.remove(b1));
        assertEquals(1, runs.size());
        assertTrue(runs.remove(b2));
        assertEquals(0, runs.size());
        assertFalse(runs.remove(b2));
        assertEquals(0, runs.size());
    }

    @Test
    public void stream() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        SortedMap<Integer, FreeStyleBuild> builds = new TreeMap<>(Collections.reverseOrder());
        for (int i = 0; i < 10; i++) {
            FreeStyleBuild build = r.buildAndAssertSuccess(p);
            builds.put(build.number, build);
        }
        RunMap<FreeStyleBuild> runMap = p._getRuns();

        assertEquals(builds.size(), runMap.entrySet().size());
        assertTrue(
                runMap.entrySet().stream().spliterator().hasCharacteristics(Spliterator.DISTINCT));
        assertTrue(
                runMap.entrySet().stream().spliterator().hasCharacteristics(Spliterator.ORDERED));
        assertFalse(runMap.entrySet().stream().spliterator().hasCharacteristics(Spliterator.SIZED));
        assertFalse(
                runMap.entrySet().stream().spliterator().hasCharacteristics(Spliterator.SORTED));
        assertThrows(
                IllegalStateException.class,
                () -> runMap.entrySet().stream().spliterator().getComparator());

        assertEquals(new ArrayList<>(builds.keySet()), new ArrayList<>(runMap.keySet()));
        Comparator<? super Integer> comparator =
                runMap.keySet().stream().spliterator().getComparator();
        assertNotNull(comparator);
        List<Integer> origOrder = new ArrayList<>(builds.keySet());
        List<Integer> sorted = new ArrayList<>(origOrder);
        sorted.sort(comparator);
        assertEquals(origOrder, sorted);
        assertTrue(runMap.keySet().stream().spliterator().hasCharacteristics(Spliterator.DISTINCT));
        assertTrue(runMap.keySet().stream().spliterator().hasCharacteristics(Spliterator.ORDERED));
        assertFalse(runMap.keySet().stream().spliterator().hasCharacteristics(Spliterator.SIZED));
        assertTrue(runMap.keySet().stream().spliterator().hasCharacteristics(Spliterator.SORTED));

        assertEquals(new ArrayList<>(builds.values()), new ArrayList<>(runMap.values()));
        assertTrue(runMap.values().stream().spliterator().hasCharacteristics(Spliterator.DISTINCT));
        assertTrue(runMap.values().stream().spliterator().hasCharacteristics(Spliterator.ORDERED));
        assertFalse(runMap.values().stream().spliterator().hasCharacteristics(Spliterator.SIZED));
        assertFalse(runMap.values().stream().spliterator().hasCharacteristics(Spliterator.SORTED));
        assertThrows(
                IllegalStateException.class,
                () -> runMap.values().stream().spliterator().getComparator());
    }

    @Test
    public void runLoadCounterFirst() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        for (int i = 0; i < 10; i++) {
            r.buildAndAssertSuccess(p);
        }
        assertEquals(
                10,
                RunLoadCounter.assertMaxLoads(p, 2, () -> p.getBuilds().stream().findFirst().orElse(null).number).intValue());
    }

    @Test
    public void runLoadCounterLimit() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        for (int i = 0; i < 10; i++) {
            r.buildAndAssertSuccess(p);
        }
        assertEquals(
                6,
                RunLoadCounter.assertMaxLoads(p, 6, () -> Streams.findLast(p.getBuilds().stream().limit(5)).orElse(null).number).intValue());
    }
}
