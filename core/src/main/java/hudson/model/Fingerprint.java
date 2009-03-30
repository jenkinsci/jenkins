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
package hudson.model;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.converters.collections.CollectionConverter;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import hudson.Util;
import hudson.XmlFile;
import hudson.BulkChange;
import hudson.util.HexBinaryConverter;
import hudson.util.Iterators;
import hudson.util.XStream2;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A file being tracked by Hudson.
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
    @ExportedBean(defaultVisibility=2)
    public static class BuildPtr {
        final String name;
        final int number;

        public BuildPtr(String name, int number) {
            this.name = name;
            this.number = number;
        }

        public BuildPtr(Run run) {
            this( run.getParent().getFullName(), run.getNumber() );
        }

        /**
         * Gets {@link Job#getFullName() the full name of the job}.
         * <p>
         * Such job could be since then removed,
         * so there might not be a corresponding
         * {@link Job}.
         */
        @Exported
        public String getName() {
            return name;
        }

        /**
         * Gets the {@link Job} that this pointer points to,
         * or null if such a job no longer exists.
         */
        public AbstractProject getJob() {
            return Hudson.getInstance().getItemByFullName(name,AbstractProject.class);
        }

        /**
         * Gets the project build number.
         * <p>
         * Such {@link Run} could be since then
         * discarded.
         */
        @Exported
        public int getNumber() {
            return number;
        }

        /**
         * Gets the {@link Job} that this pointer points to,
         * or null if such a job no longer exists.
         */
        public Run getRun() {
            Job j = getJob();
            if(j==null)     return null;
            return j.getBuildByNumber(number);
        }

        private boolean isAlive() {
            return getRun()!=null;
        }

        /**
         * Returns true if {@link BuildPtr} points to the given run.
         */
        public boolean is(Run r) {
            return r.getNumber()==number && r.getParent().getFullName().equals(name);
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
            Item p = Hudson.getInstance().getItemByFullName(name);
            while(p!=null) {
                if(p==job)
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
    }

    /**
     * Range of build numbers [start,end). Immutable.
     */
    @ExportedBean(defaultVisibility=4)
    public static final class Range {
        final int start;
        final int end;

        public Range(int start, int end) {
            assert start<end;
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
            return end<=i;
        }

        public boolean isBiggerThan(int i) {
            return i<start;
        }

        public boolean includes(int i) {
            return start<=i && i<end;
        }

        public Range expandRight() {
            return new Range(start,end+1);
        }

        public Range expandLeft() {
            return new Range(start-1,end);
        }

        public boolean isAdjacentTo(Range that) {
            return this.end==that.start;
        }

        public String toString() {
            return "["+start+","+end+")";
        }

        /**
         * Returns true if two {@link Range}s can't be combined into a single range.
         */
        public boolean isIndependent(Range that) {
            return this.end<that.start ||that.end<this.start;
        }

        /**
         * Returns true if this range only represents a single number.
         */
        public boolean isSingle() {
            return end-1==start;
        }

        /**
         * Returns the {@link Range} that combines two ranges.
         */
        public Range combine(Range that) {
            assert !isIndependent(that);
            return new Range(
                Math.min(this.start,that.start),
                Math.max(this.end  ,that.end  ));
        }
    }

    /**
     * Set of {@link Range}s.
     */
    @ExportedBean(defaultVisibility=3)
    public static final class RangeSet {
        // sorted
        private final List<Range> ranges;

        public RangeSet() {
            this(new ArrayList<Range>());
        }

        private RangeSet(List<Range> data) {
            this.ranges = data;
        }

        /**
         * List all numbers in this range set, in the ascending order.
         */
        public Iterable<Integer> listNumbers() {
            final List<Range> ranges = getRanges();
            return new Iterable<Integer>() {
                public Iterator<Integer> iterator() {
                    return new Iterators.FlattenIterator<Integer,Range>(ranges) {
                        protected Iterator<Integer> expand(Range range) {
                            return Iterators.sequence(range.start,range.end).iterator();
                        }
                    };
                }
            };
        }

//        /**
//         * List up builds.
//         */
//        public <J extends Job<J,R>,R extends Run<J,R>>  Iterable<R> listBuilds(final J job) {
//            return new Iterable<R>() {
//                public Iterator<R> iterator() {
//                    return new Iterators.FilterIterator<R>(new AdaptedIterator<Integer,R>(listNumbers().iterator()) {
//                        protected R adapt(Integer n) {
//                            return job.getBuildByNumber(n);
//                        }
//                    }) {
//                        protected boolean filter(R r) {
//                            return r!=null;
//                        }
//                    };
//                }
//            };
//        }

        /**
         * List all numbers in this range set in the descending order.
         */
        public Iterable<Integer> listNumbersReverse() {
            final List<Range> ranges = getRanges();
            return new Iterable<Integer>() {
                public Iterator<Integer> iterator() {
                    return new Iterators.FlattenIterator<Integer,Range>(Iterators.reverse(ranges)) {
                        protected Iterator<Integer> expand(Range range) {
                            return Iterators.reverseSequence(range.start,range.end).iterator();
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
            return new ArrayList<Range>(ranges);
        }

        /**
         * Expands the range set to include the given value.
         * If the set already includes this number, this will be a no-op.
         */
        public synchronized void add(int n) {
            for( int i=0; i<ranges.size(); i++ ) {
                Range r = ranges.get(i);
                if(r.includes(n))   return; // already included
                if(r.end==n) {
                    ranges.set(i,r.expandRight());
                    checkCollapse(i);
                    return;
                }
                if(r.start==n+1) {
                    ranges.set(i,r.expandLeft());
                    checkCollapse(i-1);
                    return;
                }
                if(r.isBiggerThan(n)) {
                    // needs to insert a single-value Range
                    ranges.add(i,new Range(n,n+1));
                    return;
                }
            }

            ranges.add(new Range(n,n+1));
        }

        private void checkCollapse(int i) {
            if(i<0 || i==ranges.size()-1)     return;
            Range lhs = ranges.get(i);
            Range rhs = ranges.get(i+1);
            if(lhs.isAdjacentTo(rhs)) {
                // collapsed
                Range r = new Range(lhs.start,rhs.end);
                ranges.set(i,r);
                ranges.remove(i+1);
            }
        }

        public synchronized boolean includes(int i) {
            for (Range r : ranges) {
                if(r.includes(i))
                    return true;
            }
            return false;
        }

        public synchronized void add(RangeSet that) {
            int lhs=0,rhs=0;
            while(lhs<this.ranges.size() && rhs<that.ranges.size()) {
                Range lr = this.ranges.get(lhs);
                Range rr = that.ranges.get(rhs);

                // no overlap
                if(lr.end<rr.start) {
                    lhs++;
                    continue;
                }
                if(rr.end<lr.start) {
                    ranges.add(lhs,rr);
                    lhs++;
                    rhs++;
                    continue;
                }

                // overlap. merge two
                Range m = lr.combine(rr);
                rhs++;

                // since ranges[lhs] is expanded, it might overlap with others in this.ranges
                while(lhs+1<this.ranges.size() && !m.isIndependent(this.ranges.get(lhs+1))) {
                    m = m.combine(this.ranges.get(lhs+1));
                    this.ranges.remove(lhs+1);
                }

                this.ranges.set(lhs,m);
            }

            // if anything is left in that.ranges, add them all
            this.ranges.addAll(that.ranges.subList(rhs,that.ranges.size()));
        }

        public synchronized String toString() {
            StringBuffer buf = new StringBuffer();
            for (Range r : ranges) {
                if(buf.length()>0)  buf.append(',');
                buf.append(r);
            }
            return buf.toString();
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
            return ranges.get(ranges.size()-1).end;
        }

        /**
         * Returns true if all the integers logically in this {@link RangeSet}
         * is smaller than the given integer. For example, {[1,3)} is smaller than 3,
         * but {[1,3),[100,105)} is not smaller than anything less than 105.
         *
         * Note that {} is smaller than any n.
         */
        public synchronized boolean isSmallerThan(int n) {
            if(ranges.isEmpty())    return true;

            return ranges.get(ranges.size() - 1).isSmallerThan(n);
        }

        static final class ConverterImpl implements Converter {
            private final Converter collectionConv; // used to convert ArrayList in it

            public ConverterImpl(Converter collectionConv) {
                this.collectionConv = collectionConv;
            }

            public boolean canConvert(Class type) {
                return type==RangeSet.class;
            }

            public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
                RangeSet src = (RangeSet) source;

                StringBuilder buf = new StringBuilder(src.ranges.size()*10);
                for (Range r : src.ranges) {
                    if(buf.length()>0)  buf.append(',');
                    if(r.isSingle())
                        buf.append(r.start);
                    else
                        buf.append(r.start).append('-').append(r.end-1);
                }
                writer.setValue(buf.toString());
            }

            public Object unmarshal(HierarchicalStreamReader reader, final UnmarshallingContext context) {
                if(reader.hasMoreChildren()) {
                    /* old format where <range> elements are nested like
                            <range>
                              <start>1337</start>
                              <end>1479</end>
                            </range>
                     */
                    return new RangeSet((List<Range>)(collectionConv.unmarshal(reader,context)));
                } else {
                    RangeSet rs = new RangeSet();
                    for (String s : Util.tokenize(reader.getValue(),",")) {
                        s = s.trim();
                        // s is either single number or range "x-y".
                        // note that the end range is inclusive in this notation, but not in the Range class
                        try {
                            if(s.contains("-")) {
                                String[] tokens = Util.tokenize(s,"-");
                                rs.ranges.add(new Range(Integer.parseInt(tokens[0]),Integer.parseInt(tokens[1])+1));
                            } else {
                                int n = Integer.parseInt(s);
                                rs.ranges.add(new Range(n,n+1));
                            }
                        } catch (NumberFormatException e) {
                            // ignore malformed text
                        }
                    }
                    return rs;
                }
            }
        }
    }

    private final Date timestamp;

    /**
     * Null if this fingerprint is for a file that's
     * apparently produced outside.
     */
    private final BuildPtr original;

    private final byte[] md5sum;

    private final String fileName;

    /**
     * Range of builds that use this file keyed by a job full name.
     */
    private final Hashtable<String,RangeSet> usages = new Hashtable<String,RangeSet>();

    public Fingerprint(Run build, String fileName, byte[] md5sum) throws IOException {
        this.original = build==null ? null : new BuildPtr(build);
        this.md5sum = md5sum;
        this.fileName = fileName;
        this.timestamp = new Date();
        save();
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
     *      if the file is apparently created outside Hudson.
     */
    @Exported
    public BuildPtr getOriginal() {
        return original;
    }

    public String getDisplayName() {
        return fileName;
    }

    /**
     * The file name (like "foo.jar" without path).
     */
    @Exported
    public String getFileName() {
        return fileName;
    }

    /**
     * Gets the MD5 hash string.
     */
    @Exported(name="hash")
    public String getHashString() {
        return Util.toHexString(md5sum);
    }

    /**
     * Gets the timestamp when this record is created.
     */
    @Exported
    public Date getTimestamp() {
        return timestamp;
    }

    /**
     * Gets the string that says how long since this build has scheduled.
     *
     * @return
     *      string like "3 minutes" "1 day" etc.
     */
    public String getTimestampString() {
        long duration = System.currentTimeMillis()-timestamp.getTime();
        return Util.getPastTimeString(duration);
    }

    /**
     * Gets the build range set for the given job name.
     *
     * <p>
     * These builds of this job has used this file.
     */
    public RangeSet getRangeSet(String jobFullName) {
        RangeSet r = usages.get(jobFullName);
        if(r==null) r = new RangeSet();
        return r;
    }

    public RangeSet getRangeSet(Job job) {
        return getRangeSet(job.getFullName());
    }

    /**
     * Gets the sorted list of job names where this jar is used.
     */
    public List<String> getJobs() {
        List<String> r = new ArrayList<String>();
        r.addAll(usages.keySet());
        Collections.sort(r);
        return r;
    }

    public Hashtable<String,RangeSet> getUsages() {
        return usages;
    }

    @ExportedBean(defaultVisibility=2)
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
    @Exported(name="usage")
    public List<RangeItem> _getUsages() {
        List<RangeItem> r = new ArrayList<RangeItem>();
        for (Entry<String, RangeSet> e : usages.entrySet())
            r.add(new RangeItem(e.getKey(),e.getValue()));
        return r;
    }

    public synchronized void add(AbstractBuild b) throws IOException {
        add(b.getParent().getFullName(),b.getNumber());
    }

    /**
     * Records that a build of a job has used this file.
     */
    public synchronized void add(String jobFullName, int n) throws IOException {
        synchronized(usages) {
            RangeSet r = usages.get(jobFullName);
            if(r==null) {
                r = new RangeSet();
                usages.put(jobFullName,r);
            }
            r.add(n);
        }
        save();
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
        if(original!=null && original.isAlive())
            return true;

        for (Entry<String,RangeSet> e : usages.entrySet()) {
            Job j = Hudson.getInstance().getItemByFullName(e.getKey(),Job.class);
            if(j==null)
                continue;

            int oldest = j.getFirstBuild().getNumber();
            if(!e.getValue().isSmallerThan(oldest))
                return true;
        }
        return false;
    }

    /**
     * Save the settings to a file.
     */
    public synchronized void save() throws IOException {
        if(BulkChange.contains(this))   return;

        long start=0;
        if(logger.isLoggable(Level.FINE))
            start = System.currentTimeMillis();

        File file = getFingerprintFile(md5sum);
        getConfigFile(file).write(this);

        if(logger.isLoggable(Level.FINE))
            logger.fine("Saving fingerprint "+file+" took "+(System.currentTimeMillis()-start)+"ms");
    }

    public Api getApi() {
        return new Api(this);
    }

    /**
     * The file we save our configuration.
     */
    private static XmlFile getConfigFile(File file) {
        return new XmlFile(XSTREAM,file);
    }

    /**
     * Determines the file name from md5sum.
     */
    private static File getFingerprintFile(byte[] md5sum) {
        assert md5sum.length==16;
        return new File( Hudson.getInstance().getRootDir(),
            "fingerprints/"+ Util.toHexString(md5sum,0,1)+'/'+Util.toHexString(md5sum,1,1)+'/'+Util.toHexString(md5sum,2,md5sum.length-2)+".xml");
    }

    /**
     * Loads a {@link Fingerprint} from a file in the image.
     */
    /*package*/ static Fingerprint load(byte[] md5sum) throws IOException {
        return load(getFingerprintFile(md5sum));
    }
    /*package*/ static Fingerprint load(File file) throws IOException {
        XmlFile configFile = getConfigFile(file);
        if(!configFile.exists())
            return null;

        long start=0;
        if(logger.isLoggable(Level.FINE))
            start = System.currentTimeMillis();

        try {
            Fingerprint f = (Fingerprint) configFile.read();
            if(logger.isLoggable(Level.FINE))
                logger.fine("Loading fingerprint "+file+" took "+(System.currentTimeMillis()-start)+"ms");
            return f;
        } catch (IOException e) {
            if(file.exists() && file.length()==0) {
                // Despite the use of AtomicFile, there are reports indicating that people often see
                // empty XML file, presumably either due to file system corruption (perhaps by sudden
                // power loss, etc.) or abnormal program termination.
                // generally we don't want to wipe out user data just because we can't load it,
                // but if the file size is 0, which is what's reported in HUDSON-2012, then it seems
                // like recovering it silently by deleting the file is not a bad idea.
                logger.log(Level.WARNING, "Size zero fingerprint. Disk corruption? "+configFile,e);
                file.delete();
                return null;
            }
            logger.log(Level.WARNING, "Failed to load "+configFile,e);
            throw e;
        }
    }

    private static final XStream XSTREAM = new XStream2();
    static {
        XSTREAM.alias("fingerprint",Fingerprint.class);
        XSTREAM.alias("range",Range.class);
        XSTREAM.alias("ranges",RangeSet.class);
        XSTREAM.registerConverter(new HexBinaryConverter(),10);
        XSTREAM.registerConverter(new RangeSet.ConverterImpl(
            new CollectionConverter(XSTREAM.getClassMapper()) {
                protected Object createCollection(Class type) {
                    return new ArrayList();
                }
            }
        ),10);
    }

    private static final Logger logger = Logger.getLogger(Fingerprint.class.getName());
}
