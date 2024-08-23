/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Yahoo! Inc.
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

package hudson.model;

import com.infradna.tool.bridge_method_injector.WithBridgeMethods;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.converters.basic.DateConverter;
import com.thoughtworks.xstream.converters.collections.CollectionConverter;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.BulkChange;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.Util;
import hudson.model.listeners.ItemListener;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.util.HexBinaryConverter;
import hudson.util.Iterators;
import hudson.util.PersistedList;
import hudson.util.RunList;
import hudson.util.XStream2;
import java.io.File;
import java.io.IOException;
import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.fingerprints.FileFingerprintStorage;
import jenkins.fingerprints.FingerprintStorage;
import jenkins.model.FingerprintFacet;
import jenkins.model.Jenkins;
import jenkins.model.TransientFingerprintFacetFactory;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

/**
 * A file being tracked by Jenkins.
 *
 * <p>
 * Lifecycle is managed by {@link FingerprintMap}.
 *
 * @author Kohsuke Kawaguchi
 * @see FingerprintMap
 */
@ExportedBean
public class Fingerprint implements ModelObject, Saveable {
    /**
     * Pointer to a {@link Build}.
     */
    @ExportedBean(defaultVisibility = 2)
    public static class BuildPtr {
        String name;
        final int number;

        public BuildPtr(String name, int number) {
            this.name = name;
            this.number = number;
        }

        public BuildPtr(Run run) {
            this(run.getParent().getFullName(), run.getNumber());
        }

        /**
         * Gets {@link Job#getFullName() the full name of the job}.
         * Such job could be since then removed, so there might not be a corresponding {@link Job}.
         *
         * @return A name of the job
         */
        @Exported
        @NonNull
        public String getName() {
            return name;
        }

        /**
         * Checks if the current user has permission to see this pointer.
         * @return {@code true} if the job exists and user has {@link Item#READ} permissions
         *      or if the current user has {@link Jenkins#ADMINISTER} permissions.
         *      If the job exists, but the current user has no permission to discover it,
         *      {@code false}  will be returned.
         *      If the job has been deleted and the user has no {@link Jenkins#ADMINISTER} permissions,
         *      it also returns {@code false}   in order to avoid the job existence fact exposure.
         */
        private boolean hasPermissionToDiscoverBuild() {
            // We expose the data to Jenkins administrators in order to
            // let them manage the data for deleted jobs (also works for SYSTEM2)
            final Jenkins instance = Jenkins.get();
            if (instance.hasPermission(Jenkins.ADMINISTER)) {
                return true;
            }

            return canDiscoverItem(name);
        }



        void setName(String newName) {
            name = newName;
        }

        /**
         * Gets the {@link Job} that this pointer points to,
         * or null if such a job no longer exists.
         */
        @WithBridgeMethods(value = AbstractProject.class, castRequired = true)
        public Job<?, ?> getJob() {
            return Jenkins.get().getItemByFullName(name, Job.class);
        }

        /**
         * Gets the project build number.
         * <p>
         * Such {@link Run} could be since then discarded.
         * @return A build number
         */
        @Exported
        @NonNull
        public int getNumber() {
            return number;
        }

        /**
         * Gets the {@link Job} that this pointer points to,
         * or null if such a job no longer exists.
         */
        public Run getRun() {
            Job j = getJob();
            if (j == null)     return null;
            return j.getBuildByNumber(number);
        }

        private boolean isAlive() {
            return getRun() != null;
        }

        /**
         * Returns true if {@link BuildPtr} points to the given run.
         */
        public boolean is(Run r) {
            return r.getNumber() == number && r.getParent().getFullName().equals(name);
        }

        /**
         * Returns true if {@link BuildPtr} points to the given job.
         */
        public boolean is(Job job) {
            return job.getFullName().equals(name);
        }

        /**
         * Returns true if {@link BuildPtr} points to the given job
         * or one of its subordinates.
         *
         * <p>
         * This is useful to check if an artifact in MavenModule
         * belongs to MavenModuleSet.
         */
        public boolean belongsTo(Job job) {
            Item p = Jenkins.get().getItemByFullName(name);
            while (p != null) {
                if (p == job)
                    return true;

                // go up the chain while we
                ItemGroup<? extends Item> parent = p.getParent();
                if (!(parent instanceof Item)) {
                    return false;
                }

                p = (Item) parent;
            }

            return false;
        }

        @Override public String toString() {
            return name + " #" + number;
        }
    }

    /**
     * Range of build numbers [start,end). Immutable.
     */
    @ExportedBean(defaultVisibility = 4)
    public static final class Range {
        final int start;
        final int end;

        public Range(int start, int end) {
            assert start < end;
            this.start = start;
            this.end = end;
        }

        @Exported
        public int getStart() {
            return start;
        }

        @Exported
        public int getEnd() {
            return end;
        }

        public boolean isSmallerThan(int i) {
            return end <= i;
        }

        public boolean isBiggerThan(int i) {
            return i < start;
        }

        public boolean includes(int i) {
            return start <= i && i < end;
        }

        public Range expandRight() {
            return new Range(start, end + 1);
        }

        public Range expandLeft() {
            return new Range(start - 1, end);
        }

        public boolean isAdjacentTo(Range that) {
            return this.end == that.start;
        }

        @Override
        public String toString() {
            return "[" + start + "," + end + ")";
        }

        /**
         * Returns true if two {@link Range}s can't be combined into a single range.
         */
        public boolean isIndependent(Range that) {
            return this.end < that.start || that.end < this.start;
        }

        /**
         * Returns true if two {@link Range}s do not share any common integer.
         */
        public boolean isDisjoint(Range that) {
            return this.end <= that.start || that.end <= this.start;
        }

        /**
         * Returns true if this range only represents a single number.
         */
        public boolean isSingle() {
            return end - 1 == start;
        }

        /**
         * If this range contains every int that's in the other range, return true
         */
        public boolean contains(Range that) {
            return this.start <= that.start && that.end <= this.end;
        }

        /**
         * Returns the {@link Range} that combines two ranges.
         */
        public Range combine(Range that) {
            assert !isIndependent(that);
            return new Range(
                Math.min(this.start, that.start),
                Math.max(this.end, that.end));
        }

        /**
         * Returns the {@link Range} that represents the intersection of the two.
         */
        public Range intersect(Range that) {
            assert !isDisjoint(that);
            return new Range(
                Math.max(this.start, that.start),
                Math.min(this.end, that.end));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Range that = (Range) o;
            return start == that.start && end == that.end;

        }

        @Override
        public int hashCode() {
            return 31 * start + end;
        }
    }

    /**
     * Set of {@link Range}s. Mutable.
     */
    @ExportedBean(defaultVisibility = 3)
    public static final class RangeSet {
        // sorted
        private final List<Range> ranges;

        public RangeSet() {
            this(new ArrayList<>());
        }

        private RangeSet(List<Range> data) {
            this.ranges = data;
        }

        private RangeSet(Range initial) {
            this();
            ranges.add(initial);
        }

        /**
         * List all numbers in this range set, in the ascending order.
         */
        public Iterable<Integer> listNumbers() {
            final List<Range> ranges = getRanges();
            return new Iterable<>() {
                @Override
                public Iterator<Integer> iterator() {
                    return new Iterators.FlattenIterator<>(ranges) {
                        @Override
                        protected Iterator<Integer> expand(Range range) {
                            return Iterators.sequence(range.start, range.end).iterator();
                        }
                    };
                }
            };
        }

        /**
         * List all numbers in this range set in the descending order.
         */
        public Iterable<Integer> listNumbersReverse() {
            final List<Range> ranges = getRanges();
            return new Iterable<>() {
                @Override
                public Iterator<Integer> iterator() {
                    return new Iterators.FlattenIterator<>(Iterators.reverse(ranges)) {
                        @Override
                        protected Iterator<Integer> expand(Range range) {
                            return Iterators.reverseSequence(range.start, range.end).iterator();
                        }
                    };
                }
            };
        }

        /**
         * Gets all the ranges.
         */
        @Exported
        public synchronized List<Range> getRanges() {
            return new ArrayList<>(ranges);
        }

        /**
         * Expands the range set to include the given value.
         * If the set already includes this number, this will be a no-op.
         */
        public synchronized void add(int n) {
            for (int i = 0; i < ranges.size(); i++) {
                Range r = ranges.get(i);
                if (r.includes(n))   return; // already included
                if (r.end == n) {
                    ranges.set(i, r.expandRight());
                    checkCollapse(i);
                    return;
                }
                if (r.start == n + 1) {
                    ranges.set(i, r.expandLeft());
                    checkCollapse(i - 1);
                    return;
                }
                if (r.isBiggerThan(n)) {
                    // needs to insert a single-value Range
                    ranges.add(i, new Range(n, n + 1));
                    return;
                }
            }

            ranges.add(new Range(n, n + 1));
        }

        public synchronized void addAll(int... n) {
            for (int i : n)
                add(i);
        }


        private void checkCollapse(int i) {
            if (i < 0 || i == ranges.size() - 1)     return;
            Range lhs = ranges.get(i);
            Range rhs = ranges.get(i + 1);
            if (lhs.isAdjacentTo(rhs)) {
                // collapsed
                Range r = new Range(lhs.start, rhs.end);
                ranges.set(i, r);
                ranges.remove(i + 1);
            }
        }

        public synchronized boolean includes(int i) {
            for (Range r : ranges) {
                if (r.includes(i))
                    return true;
            }
            return false;
        }

        public synchronized void add(RangeSet that) {
            int lhs = 0, rhs = 0;
            while (lhs < this.ranges.size() && rhs < that.ranges.size()) {
                Range lr = this.ranges.get(lhs);
                Range rr = that.ranges.get(rhs);

                // no overlap
                if (lr.end < rr.start) {
                    lhs++;
                    continue;
                }
                if (rr.end < lr.start) {
                    ranges.add(lhs, rr);
                    lhs++;
                    rhs++;
                    continue;
                }

                // overlap. merge two
                Range m = lr.combine(rr);
                rhs++;

                // since ranges[lhs] is expanded, it might overlap with others in this.ranges
                while (lhs + 1 < this.ranges.size() && !m.isIndependent(this.ranges.get(lhs + 1))) {
                    m = m.combine(this.ranges.get(lhs + 1));
                    this.ranges.remove(lhs + 1);
                }

                this.ranges.set(lhs, m);
            }

            // if anything is left in that.ranges, add them all
            this.ranges.addAll(that.ranges.subList(rhs, that.ranges.size()));
        }

        /**
         * Updates this range set by the intersection of this range and the given range.
         *
         * @return true if this range set was modified as a result.
         */
        public synchronized boolean retainAll(RangeSet that) {
            List<Range> intersection = new ArrayList<>();

            int lhs = 0, rhs = 0;
            while (lhs < this.ranges.size() && rhs < that.ranges.size()) {
                Range lr = this.ranges.get(lhs);
                Range rr = that.ranges.get(rhs);

                if (lr.end <= rr.start) { // lr has no overlap with that.ranges
                    lhs++;
                    continue;
                }
                if (rr.end <= lr.start) { // rr has no overlap with this.ranges
                    rhs++;
                    continue;
                }

                // overlap. figure out the intersection
                Range v = lr.intersect(rr);
                intersection.add(v);

                // move on to the next pair
                if (lr.end < rr.end) {
                    lhs++;
                } else {
                    rhs++;
                }
            }

            boolean same = this.ranges.equals(intersection);

            if (!same) {
                this.ranges.clear();
                this.ranges.addAll(intersection);
                return true;
            } else {
                return false;
            }
        }

        /**
         * Updates this range set by removing all the values in the given range set.
         *
         * @return true if this range set was modified as a result.
         */
        public synchronized boolean removeAll(RangeSet that) {
            boolean modified = false;
            List<Range> sub = new ArrayList<>();

            int lhs = 0, rhs = 0;
            while (lhs < this.ranges.size() && rhs < that.ranges.size()) {
                Range lr = this.ranges.get(lhs);
                Range rr = that.ranges.get(rhs);

                if (lr.end <= rr.start) { // lr has no overlap with that.ranges. lr stays
                    sub.add(lr);
                    lhs++;
                    continue;
                }
                if (rr.end <= lr.start) { // rr has no overlap with this.ranges
                    rhs++;
                    continue;
                }

                // some overlap between lr and rr
                assert !lr.isDisjoint(rr);
                modified = true;

                if (rr.contains(lr)) {
                    // lr completely removed by rr
                    lhs++;
                    continue;
                }

                // we want to look at A and B below, if they are non-null.
                // |------------| lr
                //     |-----|    rr
                //   A         B
                //
                // note that lr and rr could be something like or the other way around
                // |------------| lr
                //         |------------| rr
                //     A             (no B)

                if (lr.start < rr.start) { // if A is non-empty, that will stay
                    Range a = new Range(lr.start, rr.start);
                    sub.add(a);
                }

                if (rr.end < lr.end) { // if B is non-empty
                    // we still need to check that with that.ranges, so keep it in the place of lr.
                    // how much of them will eventually stay is up to the remainder of that.ranges
                    this.ranges.set(lhs, new Range(rr.end, lr.end));
                    rhs++;
                } else {
                    // if B is empty, we are done considering lr
                    lhs++;
                }
            }

            if (!modified)  return false;   // no changes

            // whatever that remains in lhs will survive
            sub.addAll(this.ranges.subList(lhs, this.ranges.size()));

            this.ranges.clear();
            this.ranges.addAll(sub);
            return true;
        }

        @Override
        public synchronized String toString() {
            StringBuilder buf = new StringBuilder();
            for (Range r : ranges) {
                if (!buf.isEmpty())  buf.append(',');
                buf.append(r);
            }
            return buf.toString();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            return ranges.equals(((RangeSet) o).ranges);

        }

        @Override
        public int hashCode() {
            return ranges.hashCode();
        }

        public synchronized boolean isEmpty() {
            return ranges.isEmpty();
        }

        /**
         * Returns the smallest value in this range.
         * <p>
         * If this range is empty, this method throws an exception.
         */
        public synchronized int min() {
            return ranges.get(0).start;
        }

        /**
         * Returns the largest value in this range.
         * <p>
         * If this range is empty, this method throws an exception.
         */
        public synchronized int max() {
            return ranges.get(ranges.size() - 1).end;
        }

        /**
         * Returns true if all the integers logically in this {@link RangeSet}
         * is smaller than the given integer. For example, {[1,3)} is smaller than 3,
         * but {[1,3),[100,105)} is not smaller than anything less than 105.
         *
         * Note that {} is smaller than any n.
         */
        public synchronized boolean isSmallerThan(int n) {
            if (ranges.isEmpty())    return true;

            return ranges.get(ranges.size() - 1).isSmallerThan(n);
        }

        /**
         * Parses a {@link RangeSet} from a string like "1-3,5,7-9"
         */
        public static RangeSet fromString(String list, boolean skipError) {
            RangeSet rs = new RangeSet();

            // Reject malformed ranges like "1---10", "1,,,,3" etc.
            if (list.contains("--") || list.contains(",,")) {
                if (!skipError) {
                    throw new IllegalArgumentException(
                            String.format("Unable to parse '%s', expected correct notation M,N or M-N", list));
                }
                // ignore malformed notation
                return rs;
            }

            String[] items = Util.tokenize(list, ",");
            if (items.length > 1 && items.length <= list.chars().filter(c -> c == ',').count()) {
                if (!skipError) {
                    throw new IllegalArgumentException(
                            String.format("Unable to parse '%s', expected correct notation M,N or M-N", list));
                }
                // ignore malformed notation like ",1,2" or "1,2,"
                return rs;
            }

            for (String s : items) {
                s = s.trim();
                // s is either single number or range "x-y".
                // note that the end range is inclusive in this notation, but not in the Range class
                try {
                    if (s.isEmpty()) {
                        if (!skipError) {
                            throw new IllegalArgumentException(
                                    String.format("Unable to parse '%s', expected number", list));                        }
                        // ignore "" element
                        continue;
                    }

                    if (s.contains("-")) {
                        if (s.chars().filter(c -> c == '-').count() > 1) {
                            if (!skipError) {
                                throw new IllegalArgumentException(String.format(
                                        "Unable to parse '%s', expected correct notation M,N or M-N", list));
                            }
                            // ignore malformed ranges like "-5-2" or "2-5-"
                            continue;
                        }
                        String[] tokens = Util.tokenize(s, "-");
                        if (tokens.length == 2) {
                            int left = Integer.parseInt(tokens[0]);
                            int right = Integer.parseInt(tokens[1]);
                            if (left < 0 || right < 0) {
                                if (!skipError) {
                                    throw new IllegalArgumentException(
                                            String.format("Unable to parse '%s', expected number above zero", list));
                                }
                                // ignore a range which starts or ends under zero like "-5-3"
                                continue;
                            }
                            if (left > right) {
                                if (!skipError) {
                                    throw new IllegalArgumentException(String.format(
                                            "Unable to parse '%s', expected string with a range M-N where M<N", list));
                                }
                                // ignore inverse range like "10-5"
                                continue;
                            }
                            rs.ranges.add(new Range(left, right + 1));
                        } else {
                            if (!skipError) {
                                throw new IllegalArgumentException(
                                        String.format("Unable to parse '%s', expected string with a range M-N", list));
                            }
                            // ignore malformed text like "1-10-50"
                            continue;
                        }
                    } else {
                        int n = Integer.parseInt(s);
                        rs.ranges.add(new Range(n, n + 1));
                    }
                } catch (NumberFormatException e) {
                    if (!skipError)
                        throw new IllegalArgumentException(
                                String.format("Unable to parse '%s', expected number", list), e);
                    // ignore malformed text
                }
            }
            return rs;
        }

        /**
         * Converter Implementation for RangeSet.
         *
         * @since 2.253
         */
        public static final class ConverterImpl implements Converter {
            private final Converter collectionConv; // used to convert ArrayList in it

            public ConverterImpl(Converter collectionConv) {
                this.collectionConv = collectionConv;
            }

            /**
             * Check if the given class can be converted (i.e. check if it is of type RangeSet).
             */
            @Override
            public boolean canConvert(Class type) {
                return type == RangeSet.class;
            }

            @Override
            public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
                RangeSet src = (RangeSet) source;
                writer.setValue(serialize(src));
            }

            /**
             * Used to serialize the range sets (builds) of the fingerprint using commas and dashes.
             * For e.g., if used in builds 1,2,3,5, it will be serialized to 1-3,5
             */
            public static String serialize(RangeSet src) {
                StringBuilder buf = new StringBuilder(src.ranges.size() * 10);
                for (Range r : src.ranges) {
                    if (!buf.isEmpty())  buf.append(',');
                    if (r.isSingle())
                        buf.append(r.start);
                    else
                        buf.append(r.start).append('-').append(r.end - 1);
                }
                return buf.toString();
            }

            @Override
            public Object unmarshal(HierarchicalStreamReader reader, final UnmarshallingContext context) {
                if (reader.hasMoreChildren()) {
                    /* old format where <range> elements are nested like
                            <range>
                              <start>1337</start>
                              <end>1479</end>
                            </range>
                     */
                    return new RangeSet((List<Range>) collectionConv.unmarshal(reader, context));
                } else {
                    return RangeSet.fromString(reader.getValue(), true);
                }
            }
        }
    }

    @Extension
    public static final class ProjectRenameListener extends ItemListener {
        @Override
        public void onLocationChanged(final Item item, final String oldName, final String newName) {
            try (ACLContext acl = ACL.as2(ACL.SYSTEM2)) {
                locationChanged(item, oldName, newName);
            }
        }

        private void locationChanged(Item item, String oldName, String newName) {
            if (item instanceof Job) {
                Job p = Jenkins.get().getItemByFullName(newName, Job.class);
                if (p != null) {
                    RunList<? extends Run> builds = p.getBuilds();
                    for (Run build : builds) {
                        Collection<Fingerprint> fingerprints = build.getBuildFingerprints();
                        for (Fingerprint f : fingerprints) {
                            try {
                                f.rename(oldName, newName);
                            } catch (IOException e) {
                                logger.log(Level.WARNING, "Failed to update fingerprint record " + f.getFileName() + " when " + oldName + " was renamed to " + newName, e);
                            }
                        }
                    }
                }
            }
        }
    }

    private static final DateConverter DATE_CONVERTER = new DateConverter();

    /**
     * Time when the fingerprint has been captured.
     */
    private final @NonNull Date timestamp;

    /**
     * Null if this fingerprint is for a file that's
     * apparently produced outside.
     */
    private final @CheckForNull BuildPtr original;

    private final byte[] md5sum;

    private final String fileName;

    /**
     * Range of builds that use this file keyed by a job full name.
     */
    private Hashtable<String, RangeSet> usages = new Hashtable<>();

    PersistedList<FingerprintFacet> facets = new PersistedList<>(this);

    /**
     * Lazily computed immutable {@link FingerprintFacet}s created from {@link TransientFingerprintFacetFactory}.
     */
    private transient volatile List<FingerprintFacet> transientFacets = null;

    public Fingerprint(@CheckForNull Run build, @NonNull String fileName, @NonNull byte[] md5sum) throws IOException {
        this(build == null ? null : new BuildPtr(build), fileName, md5sum);
        save();
    }

    Fingerprint(@CheckForNull BuildPtr original, @NonNull String fileName, @NonNull byte[] md5sum) {
        this.original = original;
        this.md5sum = md5sum;
        this.fileName = fileName;
        this.timestamp = new Date();
    }

    /**
     * The first build in which this file showed up,
     * if the file looked like it's created there.
     * <p>
     * This is considered as the "source" of this file,
     * or the owner, in the sense that this project "owns"
     * this file.
     *
     * @return null
     *      if the file is apparently created outside Hudson or if the current
     *      user has no permission to discover the job.
     */
    @Exported
    public @CheckForNull BuildPtr getOriginal() {
        if (original != null && original.hasPermissionToDiscoverBuild()) {
            return original;
        }
        return null;
    }

    @Override
    public @NonNull String getDisplayName() {
        return fileName;
    }

    /**
     * The file name (like "foo.jar" without path).
     */
    @Exported
    public @NonNull String getFileName() {
        return fileName;
    }

    /**
     * Gets the MD5 hash string.
     */
    @Exported(name = "hash")
    public @NonNull String getHashString() {
        return Util.toHexString(md5sum);
    }

    /**
     * Gets the timestamp when this record is created.
     */
    @Exported
    public @NonNull Date getTimestamp() {
        return timestamp;
    }

    /**
     * Gets the string that says how long since this build has scheduled.
     *
     * @return
     *      string like "3 minutes" "1 day" etc.
     */
    public @NonNull String getTimestampString() {
        long duration = System.currentTimeMillis() - timestamp.getTime();
        return Util.getTimeSpanString(duration);
    }

    /**
     * Gets the build range set for the given job name.
     *
     * <p>
     * These builds of this job has used this file.
     * @return may be empty but not null.
     */
    public @NonNull RangeSet getRangeSet(String jobFullName) {
        RangeSet r = usages.get(jobFullName);
        if (r == null) r = new RangeSet();
        return r;
    }

    public RangeSet getRangeSet(Job job) {
        return getRangeSet(job.getFullName());
    }

    /**
     * Gets the sorted list of job names where this jar is used.
     */
    @NonNull
    public synchronized List<String> getJobs() {
        List<String> r = new ArrayList<>(usages.keySet());
        Collections.sort(r);
        return r;
    }

    public @CheckForNull Hashtable<String, RangeSet> getUsages() {
        return usages;
    }

    @ExportedBean(defaultVisibility = 2)
    public static final class RangeItem {
        @Exported
        public final String name;
        @Exported
        public final RangeSet ranges;

        public RangeItem(String name, RangeSet ranges) {
            this.name = name;
            this.ranges = ranges;
        }
    }

    // this is for remote API
    @Exported(name = "usage")
    public @NonNull List<RangeItem> _getUsages() {
        List<RangeItem> r = new ArrayList<>();
        final Jenkins instance = Jenkins.get();
        for (Map.Entry<String, RangeSet> e : usages.entrySet()) {
            final String itemName = e.getKey();
            if (instance.hasPermission(Jenkins.ADMINISTER) || canDiscoverItem(itemName)) {
                r.add(new RangeItem(itemName, e.getValue()));
            }
        }
        return r;
    }

    /**
     * @deprecated Use {@link #addFor(hudson.model.Run)}
     */
    @Deprecated
    public void add(@NonNull AbstractBuild b) throws IOException {
        addFor(b);
    }

    /**
     * Adds a usage reference to the build.
     * @param b {@link Run} to be referenced in {@link #usages}
     * @since 1.577
     */
    public void addFor(@NonNull Run b) throws IOException {
        add(b.getParent().getFullName(), b.getNumber());
    }

    /**
     * Records that a build of a job has used this file.
     */
    public synchronized void add(@NonNull String jobFullName, int n) throws IOException {
        addWithoutSaving(jobFullName, n);
        save();
    }

    // JENKINS-49588
    @SuppressFBWarnings(value = "IS2_INCONSISTENT_SYNC", justification = "nothing should be competing with XStream during deserialization")
    protected Object readResolve() {
        if (usages == null) {
            usages = new Hashtable<>();
        }
        return this;
    }

    void addWithoutSaving(@NonNull String jobFullName, int n) {
        synchronized (usages) { // TODO why not synchronized (this) like some, though not all, other accesses?
            RangeSet r = usages.get(jobFullName);
            if (r == null) {
                r = new RangeSet();
                usages.put(jobFullName, r);
            }
            r.add(n);
        }
    }

    /**
     * Returns true if any of the builds recorded in this fingerprint
     * is still retained.
     *
     * <p>
     * This is used to find out old fingerprint records that can be removed
     * without losing too much information.
     */
    public synchronized boolean isAlive() {
        if (original != null && original.isAlive())
            return true;

        for (Map.Entry<String, RangeSet> e : usages.entrySet()) {
            Job j = Jenkins.get().getItemByFullName(e.getKey(), Job.class);
            if (j == null)
                continue;

            Run firstBuild = j.getFirstBuild();
            if (firstBuild == null)
                continue;

            int oldest = firstBuild.getNumber();
            if (!e.getValue().isSmallerThan(oldest))
                return true;
        }
        return false;
    }

    /**
     * Trim off references to non-existent builds and jobs, thereby making the fingerprint smaller.
     *
     * @return true
     *      if this record was modified.
     *
     * @throws IOException Save failure
     */
    public synchronized boolean trim() throws IOException {
        boolean modified = false;

        for (Map.Entry<String, RangeSet> e : new Hashtable<>(usages).entrySet()) { // copy because we mutate
            Job j = Jenkins.get().getItemByFullName(e.getKey(), Job.class);
            if (j == null) { // no such job any more. recycle the record
                modified = true;
                usages.remove(e.getKey());
                continue;
            }

            Run firstBuild = j.getFirstBuild();
            if (firstBuild == null) { // no builds. recycle the whole record
                modified = true;
                usages.remove(e.getKey());
                continue;
            }

            RangeSet cur = e.getValue();

            // builds that are around without the keepLog flag on are normally clustered together (in terms of build #)
            // so our basic strategy is to discard everything up to the first ephemeral build, except those builds
            // that are marked as kept
            RangeSet kept = new RangeSet();
            Run r = firstBuild;
            while (r != null && r.isKeepLog()) {
                kept.add(r.getNumber());
                r = r.getNextBuild();
            }

            if (r == null) {
                // all the build records are permanently kept ones, so we'll just have to keep 'kept' out of whatever currently in 'cur'
                modified |= cur.retainAll(kept);
            } else {
                // otherwise we are ready to discard [0,r.number) except those marked as 'kept'
                RangeSet discarding =  new RangeSet(new Range(-1, r.getNumber()));
                discarding.removeAll(kept);
                modified |= cur.removeAll(discarding);
            }

            if (cur.isEmpty()) {
                usages.remove(e.getKey());
                modified = true;
            }
        }

        if (modified) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "Saving trimmed Fingerprint ", md5sum);
            }
            save();
        }

        return modified;
    }


    /**
     * Gets the associated {@link FingerprintFacet}s.
     *
     * <p>
     * This method always return a non-empty collection, which is a synthetic collection.
     * It contains persisted {@link FingerprintFacet}s (those that are added explicitly, like
     * {@code fingerprint.getFacets().add(x)}), as well those {@linkplain TransientFingerprintFacetFactory that are transient}.
     *
     * <p>
     * Mutation to this collection will manipulate persisted set of {@link FingerprintFacet}s, and therefore regardless
     * of what you do, this collection will always contain a set of {@link FingerprintFacet}s that are added
     * by {@link TransientFingerprintFacetFactory}s.
     *
     * @since 1.421
     */
    public @NonNull Collection<FingerprintFacet> getFacets() {
        if (transientFacets == null) {
            List<FingerprintFacet> transientFacets = new ArrayList<>();
            for (TransientFingerprintFacetFactory fff : TransientFingerprintFacetFactory.all()) {
                fff.createFor(this, transientFacets);
            }
            this.transientFacets = Collections.unmodifiableList(transientFacets);
        }

        return new AbstractCollection<>() {
            @Override
            public Iterator<FingerprintFacet> iterator() {
                return Iterators.sequence(facets.iterator(), transientFacets.iterator());
            }

            @Override
            public boolean add(FingerprintFacet e) {
                facets.add(e);
                return true;
            }

            @Override
            public boolean remove(Object o) {
                return facets.remove(o);
            }

            @Override
            public boolean contains(Object o) {
                return facets.contains(o) || transientFacets.contains(o);
            }

            @Override
            public int size() {
                return facets.size() + transientFacets.size();
            }
        };
    }

    /**
     * Returns the persisted facets.
     *
     * @since 2.242
     */
    public final @NonNull PersistedList<FingerprintFacet> getPersistedFacets() {
        return facets;
    }

    /**
     * Sorts {@link FingerprintFacet}s by their timestamps.
     * @return Sorted list of {@link FingerprintFacet}s
     */
    public @NonNull Collection<FingerprintFacet> getSortedFacets() {
        List<FingerprintFacet> r = new ArrayList<>(getFacets());
        r.sort(new Comparator<>() {
            @Override
            public int compare(FingerprintFacet o1, FingerprintFacet o2) {
                long a = o1.getTimestamp();
                long b = o2.getTimestamp();
                return Long.compare(a, b);
            }
        });
        return r;
    }

    /**
     * Finds a facet of the specific type (including subtypes.)
     * @param <T> Class of the {@link FingerprintFacet}
     * @return First matching facet of the specified class
     * @since 1.556
     */
    public @CheckForNull <T extends FingerprintFacet> T getFacet(Class<T> type) {
        for (FingerprintFacet f : getFacets()) {
            if (type.isInstance(f))
                return type.cast(f);
        }
        return null;
    }

    /**
     * Returns the actions contributed from {@link #getFacets()}
     */
    public @NonNull List<Action> getActions() {
        List<Action> r = new ArrayList<>();
        for (FingerprintFacet ff : getFacets())
            ff.createActions(r);
        return Collections.unmodifiableList(r);
    }

    /**
     * Save the Fingerprint in the Fingerprint Storage
     * @throws IOException Save error
     */
    @Override
    public synchronized void save() throws IOException {
        if (BulkChange.contains(this)) {
            return;
        }

        long start = 0;
        if (logger.isLoggable(Level.FINE))
            start = System.currentTimeMillis();

        FingerprintStorage configuredFingerprintStorage = FingerprintStorage.get();
        FingerprintStorage fileFingerprintStorage = ExtensionList.lookupSingleton(FileFingerprintStorage.class);

        // Implementations are expected to invoke SaveableListener on their own if relevant
        // TODO: Consider improving Saveable Listener API: https://issues.jenkins.io/browse/JENKINS-62543
        configuredFingerprintStorage.save(this);

        // In the case that external fingerprint storage is configured, there may be some fingerprints in memory that
        // get saved before a load call (because they are already in memory). This ensures that they get deleted from
        // the file fingerprint storage.
        // TODO: Consider improving KeyedDataStorage so it provides an API for clearing the fingerprints in memory.
        if (!(configuredFingerprintStorage instanceof FileFingerprintStorage) && fileFingerprintStorage.isReady()) {
            fileFingerprintStorage.delete(this.getHashString());
        }

        if (logger.isLoggable(Level.FINE))
            logger.fine("Saving fingerprint " + getHashString() + " took " + (System.currentTimeMillis() - start) + "ms");
    }

    /**
     * Save the Fingerprint in the given file locally
     * @throws IOException Save error
     * @deprecated as of 2.242. Use {@link #save()} instead.
     */
    @Deprecated
    void save(File file) throws IOException {
        FileFingerprintStorage.save(this, file);
    }

    /**
     * Returns a facet that blocks the deletion of the fingerprint.
     * Returns null if no such facet.
     * @since 2.223
     */
    public @CheckForNull FingerprintFacet getFacetBlockingDeletion() {
        for (FingerprintFacet facet : facets) {
            if (facet.isFingerprintDeletionBlocked())
                return facet;
        }
        return null;
    }

    /**
     * Update references to a renamed job in the fingerprint
     */
    public synchronized void rename(String oldName, String newName) throws IOException {
        boolean touched = false;
        if (original != null) {
            if (original.getName().equals(oldName)) {
                original.setName(newName);
                touched = true;
            }
        }

        if (usages != null) {
            RangeSet r = usages.get(oldName);
            if (r != null) {
                usages.put(newName, r);
                usages.remove(oldName);
                touched = true;
            }
        }

        if (touched) {
            save();
        }
    }

    public Api getApi() {
        return new Api(this);
    }

    /**
     * Loads a {@link Fingerprint} from the Storage with the given unique id.
     * @return Loaded {@link Fingerprint}. {@code null} if the config file does not exist or
     * malformed.
     *
     * In case an external storage is configured on top of a file system based storage:
     * 1. External storage is polled to retrieve the fingerprint
     * 2. If not found, then the local storage is polled to retrieve the fingerprint
     */
    public static @CheckForNull Fingerprint load(@NonNull String id) throws IOException {
        long start = 0;
        if (logger.isLoggable(Level.FINE)) {
            start = System.currentTimeMillis();
        }

        FingerprintStorage configuredFingerprintStorage = FingerprintStorage.get();
        FingerprintStorage fileFingerprintStorage = ExtensionList.lookupSingleton(FileFingerprintStorage.class);

        Fingerprint loaded = configuredFingerprintStorage.load(id);

        if (loaded == null && !(configuredFingerprintStorage instanceof FileFingerprintStorage) &&
                fileFingerprintStorage.isReady()) {
            loaded = fileFingerprintStorage.load(id);
            if (loaded != null) {
                initFacets(loaded);
                configuredFingerprintStorage.save(loaded);
                fileFingerprintStorage.delete(id);
            }
        } else {
            initFacets(loaded);
        }

        if (logger.isLoggable(Level.FINE)) {
            logger.fine("Loading fingerprint took " + (System.currentTimeMillis() - start) + "ms");
        }

        return loaded;
    }

    /**
     * Determines the file name from md5sum.
     * @deprecated as of 2.242. Use {@link #load(String)} instead.
     */
    @Deprecated
    /*package*/ static @CheckForNull Fingerprint load(@NonNull byte[] md5sum) throws IOException {
        return load(Util.toHexString(md5sum));
    }

    /**
     * Loads a {@link Fingerprint} from a file in the image.
     * @return Loaded {@link Fingerprint}. Null if the config file does not exist or
     * malformed.
     * @deprecated as of 2.242. Use {@link #load(String)} instead.
     */
    @Deprecated
    /*package*/ static @CheckForNull Fingerprint load(@NonNull File file) throws IOException {
        Fingerprint fingerprint = FileFingerprintStorage.load(file);
        initFacets(fingerprint);

        return fingerprint;
    }

    /**
     * Deletes the {@link Fingerprint} in the configured storage with the given unique id.
     *
     * @since 2.242
     */
    public static void delete(@NonNull String id) throws IOException {
        FingerprintStorage configuredFingerprintStorage = FingerprintStorage.get();
        FingerprintStorage fileFingerprintStorage = ExtensionList.lookupSingleton(FileFingerprintStorage.class);

        configuredFingerprintStorage.delete(id);

        if (!(configuredFingerprintStorage instanceof FileFingerprintStorage) && fileFingerprintStorage.isReady()) {
            fileFingerprintStorage.delete(id);
        }
    }

    /**
     * Performs Initialization of facets on a newly loaded Fingerprint.
     */
    private static void initFacets(@CheckForNull Fingerprint fingerprint) {
        if (fingerprint == null) {
            return;
        }

        for (FingerprintFacet facet : fingerprint.facets) {
            facet._setOwner(fingerprint);
        }
    }

    @Override public String toString() {
        return "Fingerprint[original="
                + original
                + ",hash="
                + getHashString()
                + ",fileName="
                + fileName
                + ",timestamp="
                + DATE_CONVERTER.toString(timestamp)
                + ",usages="
                + (usages == null ? "null" : new TreeMap<>(getUsages()))
                + ",facets="
                + facets
                + "]";
    }

    /**
     * Checks if the current user can Discover the item.
     * If yes, it may be displayed as a text in Fingerprint UIs.
     * @param fullName Full name of the job
     * @return {@code true} if the user can discover the item
     */
    private static boolean canDiscoverItem(@NonNull final String fullName) {
        final Jenkins jenkins = Jenkins.get();

        // Fast check to avoid security context switches
        Item item = null;
        try {
            item = jenkins.getItemByFullName(fullName);
        } catch (AccessDeniedException ex) {
            // ignore, we will fall-back later
        }
        if (item != null) {
            return true;
        }

        // Probably it failed due to the missing Item.DISCOVER
        // We try to retrieve the job using SYSTEM2 user and to check permissions manually.
        final Authentication userAuth = Jenkins.getAuthentication2();
        try (ACLContext acl = ACL.as2(ACL.SYSTEM2)) {
            final Item itemBySystemUser = jenkins.getItemByFullName(fullName);
            if (itemBySystemUser == null) {
                return false;
            }

            // To get the item existence fact, a user needs Item.DISCOVER for the item
            // and Item.READ for all container folders.
            boolean canDiscoverTheItem = itemBySystemUser.hasPermission2(userAuth, Item.DISCOVER);
            if (canDiscoverTheItem) {
                ItemGroup<?> current = itemBySystemUser.getParent();
                do {
                    if (current instanceof Item) {
                        final Item i = (Item) current;
                        current = i.getParent();
                        if (!i.hasPermission2(userAuth, Item.READ)) {
                            canDiscoverTheItem = false;
                        }
                    } else {
                        current = null;
                    }
                } while (canDiscoverTheItem && current != null);
            }
            return canDiscoverTheItem;
        }
    }

    private static final XStream2 XSTREAM = new XStream2();

    /**
     * Provides the XStream instance this class is using for serialization.
     *
     * @return the XStream instance
     * @since 1.655
     */
    @NonNull
    public static XStream2 getXStream() {
        return XSTREAM;
    }

    static {
        XSTREAM.alias("fingerprint", Fingerprint.class);
        XSTREAM.alias("range", Range.class);
        XSTREAM.alias("ranges", RangeSet.class);
        XSTREAM.registerConverter(new HexBinaryConverter(), 10);
        XSTREAM.registerConverter(new RangeSet.ConverterImpl(
            new CollectionConverter(XSTREAM.getMapper()) {
                @Override
                protected Object createCollection(Class type) {
                    return new ArrayList();
                }
            }
        ), 10);
    }

    private static final Logger logger = Logger.getLogger(Fingerprint.class.getName());
}
