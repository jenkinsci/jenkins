/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Geoff Cummings
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

import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.model.AbstractBuild;
import hudson.model.Job;
import hudson.model.Node;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TopLevelItem;
import hudson.model.View;
import hudson.util.Iterators.CountingPredicate;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Spliterator;
import java.util.function.Predicate;

/**
 * {@link List} of {@link Run}s, sorted in the descending date order.
 *
 * @author Kohsuke Kawaguchi
 */
public class RunList<R extends Run> extends AbstractList<R> {

    private Iterable<R> base;

    private R first;
    private Integer size;

    public RunList() {
        base = Collections.emptyList();
    }

    public RunList(Job j) {
        base = j.getBuilds();
    }

    public RunList(View view) { // this is a type unsafe operation
        Set<Job> jobs = new HashSet<>();
        for (TopLevelItem item : view.getItems())
            jobs.addAll(item.getAllJobs());

        List<Iterable<R>> runLists = new ArrayList<>();
        for (Job job : jobs) {
            runLists.add(job.getBuilds());
        }
        this.base = combine(runLists);
    }

    public RunList(Collection<? extends Job> jobs) {
        List<Iterable<R>> runLists = new ArrayList<>();
        for (Job j : jobs)
            runLists.add(j.getBuilds());
        this.base = combine(runLists);
    }

    /**
     * Creates a a {@link RunList} combining all the runs of the supplied jobs.
     *
     * @param jobs the supplied jobs.
     * @param <J> the base class of job.
     * @param <R> the base class of run.
     * @return the run list.
     * @since 2.37
     */
    public static <J extends Job<J, R>, R extends Run<J, R>> RunList<R> fromJobs(Iterable<? extends J> jobs) {
        List<Iterable<R>> runLists = new ArrayList<>();
        for (Job j : jobs)
            runLists.add(j.getBuilds());
        return new RunList<>(combine(runLists));
    }

    private static <R extends Run> Iterable<R> combine(Iterable<Iterable<R>> runLists) {
        return Iterables.mergeSorted(runLists, (o1, o2) -> {
            long lhs = o1.getTimeInMillis();
            long rhs = o2.getTimeInMillis();
            return Long.compare(rhs, lhs);
        });
    }

    private RunList(Iterable<R> c) {
        base = c;
    }

    @Override
    public Iterator<R> iterator() {
        return base.iterator();
    }

    /**
     * @deprecated as of 1.485
     *      {@link RunList}, despite its name, should be really used as {@link Iterable}, not as {@link List}.
     */
    @Override
    @Deprecated
    public int size() {
        if (size == null) {
            int sz = 0;
            for (R r : this) {
                first = r;
                sz++;
            }
            size = sz;
        }
        return size;
    }

    /**
     * @deprecated as of 1.485
     *      {@link RunList}, despite its name, should be really used as {@link Iterable}, not as {@link List}.
     */
    @Override
    @Deprecated
    public R get(int index) {
        return Iterators.get(iterator(), index);
    }

    /**
     * {@link AbstractList#subList(int, int)} isn't very efficient on our {@link Iterable} based implementation.
     * In fact the range check alone would require us to iterate all the elements,
     * so we'd be better off just copying into ArrayList.
     */
    @Override
    public List<R> subList(int fromIndex, int toIndex) {
        int sublistSize = toIndex < fromIndex ? 0 : toIndex - fromIndex;
        List<R> r = new ArrayList<>(sublistSize);
        Iterator<R> itr = iterator();
        hudson.util.Iterators.skip(itr, fromIndex);
        for (int i = toIndex - fromIndex; i > 0; i--) {
            r.add(itr.next());
        }
        return r;
    }

    @Override
    public Spliterator<R> spliterator() {
        return base.spliterator();
    }

    @Override
    public int indexOf(Object o) {
        int index = 0;
        for (R r : this) {
            if (r.equals(o))
                return index;
            index++;
        }
        return -1;
    }

    @Override
    public int lastIndexOf(Object o) {
        int a = -1;
        int index = 0;
        for (R r : this) {
            if (r.equals(o))
                a = index;
            index++;
        }
        return a;
    }

    @Override
    public boolean isEmpty() {
        return !iterator().hasNext();
    }

    /** @deprecated see {@link #size()} for why this violates lazy-loading */
    @Deprecated
    public R getFirstBuild() {
        size();
        return first;
    }

    public R getLastBuild() {
        Iterator<R> itr = iterator();
        return itr.hasNext() ? itr.next() : null;
    }

    public static <R extends Run>
    RunList<R> fromRuns(Collection<? extends R> runs) {
        return new RunList<R>((Iterable) runs);
    }

    /**
     * Returns elements that satisfy the given predicate.
     * <em>Warning:</em> this method mutates the original list and then returns it.
     * @since 2.279
     */
    public RunList<R> filter(Predicate<R> predicate) {
        return filter(new PredicateAdapter(predicate));
    }

    private static class PredicateAdapter<T> implements com.google.common.base.Predicate<T> {
        private final Predicate<T> predicate;

        PredicateAdapter(Predicate<T> predicate) {
            this.predicate = predicate;
        }

        @Override
        public boolean apply(@Nullable T r) {
            return predicate.test(r);
        }
    }

    /**
     * Returns elements that satisfy the given predicate.
     * <em>Warning:</em> this method mutates the original list and then returns it.
     * @since 1.544
     * @deprecated use {@link #filter(Predicate)}
     */
    @Deprecated
    public RunList<R> filter(com.google.common.base.Predicate<R> predicate) {
        size = null;
        first = null;
        base = Iterables.filter(base, predicate);
        return this;
    }

    /**
     * Returns the first streak of the elements that satisfy the given predicate.
     *
     * For example, {@code filter([1,2,3,4],odd)==[1,3]} but {@code limit([1,2,3,4],odd)==[1]}.
     */
    private RunList<R> limit(final CountingPredicate<R> predicate) {
        size = null;
        first = null;
        final Iterable<R> nested = base;
        base = new Iterable<>() {
            @Override
            public Iterator<R> iterator() {
                return hudson.util.Iterators.limit(nested.iterator(), predicate);
            }

            @Override
            public String toString() {
                return Iterables.toString(this);
            }
        };
        return this;
    }

    /**
     * Return only the most recent builds.
     * <em>Warning:</em> this method mutates the original list and then returns it.
     * @param n a count
     * @return the n most recent builds
     * @since 1.507
     */
    public RunList<R> limit(final int n) {
        return limit((index, input) -> index < n);
    }

    /**
     * Filter the list to non-successful builds only.
     * <em>Warning:</em> this method mutates the original list and then returns it.
     */
    public RunList<R> failureOnly() {
        return filter((Predicate<R>) r -> r.getResult() != Result.SUCCESS);
    }

    /**
     * Filter the list to builds above threshold.
     * <em>Warning:</em> this method mutates the original list and then returns it.
     * @since 1.517
     */
    public RunList<R> overThresholdOnly(final Result threshold) {
        return filter((Predicate<R>) r -> r.getResult() != null && r.getResult().isBetterOrEqualTo(threshold));
    }

    /**
     * Filter the list to completed builds.
     * <em>Warning:</em> this method mutates the original list and then returns it.
     * @since 1.561
     */
    public RunList<R> completedOnly() {
        return filter((Predicate<R>) r -> !r.isBuilding());
    }

    /**
     * Filter the list to builds on a single node only
     * <em>Warning:</em> this method mutates the original list and then returns it.
     */
    public RunList<R> node(final Node node) {
        return filter((Predicate<R>) r -> r instanceof AbstractBuild && ((AbstractBuild) r).getBuiltOn() == node);
    }

    /**
     * Filter the list to regression builds only.
     * <em>Warning:</em> this method mutates the original list and then returns it.
     */
    public RunList<R> regressionOnly() {
        return filter((Predicate<R>) r -> r.getBuildStatusSummary().isWorse);
    }

    /**
     * Filter the list by timestamp.
     *
     * {@code s&lt=;e}.
     * <em>Warning:</em> this method mutates the original list and then returns it.
     */
    public RunList<R> byTimestamp(final long start, final long end) {
        return
        limit((index, r) -> start <= r.getTimeInMillis()).filter((Predicate<R>) r -> r.getTimeInMillis() < end);
    }

    /**
     * Reduce the size of the list by only leaving relatively new ones.
     * This also removes on-going builds, as RSS cannot be used to publish information
     * if it changes.
     * <em>Warning:</em> this method mutates the original list and then returns it.
     */
    public RunList<R> newBuilds() {
        GregorianCalendar cal = new GregorianCalendar();
        cal.add(Calendar.DAY_OF_YEAR, -7);
        final long t = cal.getTimeInMillis();

        // can't publish on-going builds
        return filter((Predicate<R>) r -> !r.isBuilding())
        // put at least 10 builds, but otherwise ignore old builds
        .limit((index, r) -> index < 10 || r.getTimeInMillis() >= t);
    }
}
