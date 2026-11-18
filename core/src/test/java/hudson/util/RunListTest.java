/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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

package hudson.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import hudson.model.Job;
import hudson.model.Run;
import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;

/**
 * @author Ignacio Albors
 */
class RunListTest {

    @Test
    void sizeUsesOverriddenIteratorAfterFilteringAndLimiting() {
        Run newest = mock(Run.class);
        Run oldest = mock(Run.class);
        Job job = mock(Job.class);
        when(job.getBuilds()).thenReturn(RunList.fromRuns(List.of(newest, oldest)));
        RunList<Run> runs = new RunList<>(job) {
            @Override
            public Iterator<Run> iterator() {
                return com.google.common.collect.Iterators.limit(super.iterator(), 1);
            }
        };

        runs.filter((Predicate<Run>) run -> true);
        assertEquals(1, runs.size());
        assertSame(newest, runs.getCachedFirstBuild());
        runs.limit(2);
        assertEquals(1, runs.size());
        assertSame(newest, runs.getCachedFirstBuild());
    }

    @Test
    void filteredParallelStreamPreservesEncounterOrder() {
        Run newest = mock(Run.class);
        Run oldest = mock(Run.class);
        List<Run> orderedRuns = new ArrayList<>();
        orderedRuns.add(newest);
        orderedRuns.addAll(Collections.nCopies(4096, oldest));
        RunList<Run> runs = RunList.fromRuns(orderedRuns);

        for (int i = 0; i < 2; i++) {
            runs.filter((Predicate<Run>) run -> true);
            assertTrue(runs.spliterator().hasCharacteristics(Spliterator.ORDERED));
            assertSame(newest, runs.parallelStream().findFirst().orElseThrow());
        }
    }

    @Test
    void sizeAndOldestBuildAreCachedOnlyAfterCompletion() {
        Run newest = mock(Run.class);
        Run oldest = mock(Run.class);
        AtomicInteger visited = new AtomicInteger();
        RunList<Run> runList = RunList.fromRuns(List.of(newest, oldest)).filter((Predicate<Run>) run -> {
            if (visited.incrementAndGet() == 2) {
                Thread.currentThread().interrupt();
            }
            return true;
        });
        List<Run> runs = runList;
        try {
            assertThrows(RunListInterruptedException.class, runs::size);
            assertTrue(Thread.currentThread().isInterrupted());
            assertNull(runList.getCachedFirstBuild());
        } finally {
            Thread.interrupted();
        }
        assertEquals(2, runs.size());
        assertSame(oldest, runList.getCachedFirstBuild());
        assertEquals(4, visited.get());
        assertEquals(2, runs.size());
        assertEquals(4, visited.get());
    }

    @Test
    void sizeHonorsAnExistingInterrupt() {
        List<Run> runs = RunList.fromRuns(List.of(mock(Run.class)));
        try {
            Thread.currentThread().interrupt();
            assertThrows(RunListInterruptedException.class, runs::size);
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
        assertEquals(1, runs.size());
    }

    @Test
    void sizeStopsWhileSkippingRejectedBuilds() {
        Run run = mock(Run.class);
        AtomicInteger fetched = new AtomicInteger();
        AtomicInteger visited = new AtomicInteger();
        List<Run> runs = RunList.fromRuns(new AbstractCollection<Run>() {
            @Override
            public Iterator<Run> iterator() {
                return List.of(run, run, run).stream().peek(candidate -> fetched.incrementAndGet()).iterator();
            }

            @Override
            public int size() {
                return 3;
            }
        }).filter((Predicate<Run>) candidate -> {
            if (visited.incrementAndGet() == 1) {
                Thread.currentThread().interrupt();
            }
            return false;
        }).limit(3);
        try {
            assertThrows(RunListInterruptedException.class, runs::size);
            assertTrue(Thread.currentThread().isInterrupted());
            assertEquals(1, visited.get());
            assertEquals(1, fetched.get());
        } finally {
            Thread.interrupted();
        }
        assertEquals(0, runs.size());
        assertEquals(4, visited.get());
    }

    @Test
    void sizeStopsInsideNestedRejectingFilters() {
        for (boolean outerFilter : List.of(false, true)) {
            AtomicInteger visited = new AtomicInteger();
            Run run = mock(Run.class);
            RunList<Run> inner = RunList.fromRuns(List.of(run, run, run)).filter((Predicate<Run>) candidate -> {
                visited.incrementAndGet();
                Thread.currentThread().interrupt();
                return false;
            });
            RunList<Run> outer = RunList.fromRuns(inner);
            if (outerFilter) {
                outer.filter((Predicate<Run>) candidate -> true);
            }
            try {
                assertThrows(RunListInterruptedException.class, outer::size);
                assertTrue(Thread.currentThread().isInterrupted());
                assertEquals(1, visited.get());
                assertNull(inner.getCachedFirstBuild());
                assertNull(outer.getCachedFirstBuild());
                // The failed size() must not leave ordinary iteration interruptible.
                assertFalse(outer.iterator().hasNext());
                assertEquals(4, visited.get());
            } finally {
                Thread.interrupted();
            }
        }
    }

    @Test
    void nestedSizeRestoresOuterInterruptionScope() {
        RunList<Run> runs = RunList.fromRuns(List.of(mock(Run.class))).filter((Predicate<Run>) run -> {
            assertEquals(0, new RunList<>().size());
            Thread.currentThread().interrupt();
            return false;
        });
        try {
            assertThrows(RunListInterruptedException.class, runs::size);
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void completedSizeRestoresOrdinaryIteration() {
        Run run = mock(Run.class);
        RunList<Run> runs = RunList.fromRuns(List.of(run)).filter((Predicate<Run>) candidate -> true);
        assertEquals(1, runs.size());
        try {
            Thread.currentThread().interrupt();
            assertSame(run, runs.iterator().next());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void sizeChecksInterruptionAfterLastRejectedBuild() {
        Run first = mock(Run.class);
        Run last = mock(Run.class);
        RunList<Run> runList = RunList.fromRuns(List.of(first, last)).filter((Predicate<Run>) run -> {
            if (run == last) {
                Thread.currentThread().interrupt();
                return false;
            }
            return true;
        });
        List<Run> runs = runList;
        try {
            assertThrows(RunListInterruptedException.class, runs::size);
            assertNull(runList.getCachedFirstBuild());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void emptySize() {
        List<Run> runs = new RunList<>();
        assertEquals(0, runs.size());
    }

    // RunList for byTimestamp tests
    private RunList rlist;

    // RunList<Run> is ordered from most to least recent
    private void setUpByTimestampRuns() {
        Run r1 = mock(Run.class);
        Run r2 = mock(Run.class);

        when(r1.getNumber()).thenReturn(1);
        when(r2.getNumber()).thenReturn(2);

        when(r1.getTimeInMillis()).thenReturn(200L);
        when(r2.getTimeInMillis()).thenReturn(300L);

        ArrayList<Run> list = new ArrayList<>();
        list.add(r2);
        list.add(r1);

        rlist = RunList.fromRuns(list);
    }

    @Test
    void byTimestampAllRuns() {
        setUpByTimestampRuns();

        RunList<Run> tested = rlist.byTimestamp(0, 400);
        assertEquals(2, tested.toArray().length);
    }

    @Issue("JENKINS-21159")
    @Test
    @SuppressWarnings("deprecation")
    void byTimestampFirstRun() {
        setUpByTimestampRuns();
        // Only r1
        RunList<Run> tested = rlist.byTimestamp(150, 250);
        assertEquals(1, tested.toArray().length);
        assertEquals(1, tested.getFirstBuild().getNumber());
    }

    @Test
    @SuppressWarnings("deprecation")
    void byTimestampLastRun() {
        setUpByTimestampRuns();
        // Only r2
        RunList<Run> tested = rlist.byTimestamp(250, 350);
        assertEquals(1, tested.toArray().length);
        assertEquals(2, tested.getFirstBuild().getNumber());
    }
}
