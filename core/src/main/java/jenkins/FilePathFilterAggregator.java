package jenkins;

import hudson.Extension;
import hudson.FilePath;
import hudson.remoting.ChannelProperty;

import java.io.File;
import java.util.Collections;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Maintains a bundle of {@link FilePathFilter} and implement a hook that broadcasts to all the filters.
 *
 * Accessible as channel property.
 *
 * @author Kohsuke Kawaguchi
 * @see FilePath
 * @since 1.THU
 */
class FilePathFilterAggregator extends FilePathFilter {
    private final CopyOnWriteArrayList<Entry> all = new CopyOnWriteArrayList<Entry>();

    private class Entry implements Comparable<Entry> {
        final FilePathFilter filter;
        final double ordinal;

        private Entry(FilePathFilter filter, double ordinal) {
            this.filter = filter;
            this.ordinal = ordinal;
        }

        @Override
        public int compareTo(Entry that) {
            double d = this.ordinal - that.ordinal;
            if (d<0)    return -1;
            if (d>0)    return 1;

            // to create predictable order that doesn't depend on the insertion order, use class name
            // to break a tie
            return this.filter.getClass().getName().compareTo(that.filter.getClass().getName());
        }
    }

    public final void add(FilePathFilter f) {
        add(f, DEFAULT_ORDINAL);
    }

    /**
     *
     * @param ordinal
     *      Crude ordering control among {@link FilePathFilter} ala {@link Extension#ordinal()}.
     *      A filter with a bigger value will get precedence. Defaults to 0.
     */
    public void add(FilePathFilter f, double ordinal) {
        Entry e = new Entry(f, ordinal);
        int i = Collections.binarySearch(all, e, Collections.reverseOrder());
        if (i>=0)   all.add(i,e);
        else        all.add(-i-1,e);
    }

    public void remove(FilePathFilter f) {
        for (Entry e : all) {
            if (e.filter==f)
                all.remove(e);
        }
    }

    /**
     * If no filter cares, what to do?
     */
    protected boolean defaultAction() throws SecurityException {
        return false;
    }

    @Override
    public boolean read(File f) throws SecurityException {
        for (Entry e : all) {
            if (e.filter.read(f))
                return true;
        }
        return defaultAction();
    }

    @Override
    public boolean mkdirs(File f) throws SecurityException {
        for (Entry e : all) {
            if (e.filter.mkdirs(f))
                return true;
        }
        return defaultAction();
    }

    @Override
    public boolean write(File f) throws SecurityException {
        for (Entry e : all) {
            if (e.filter.write(f))
                return true;
        }
        return defaultAction();
    }

    @Override
    public boolean symlink(File f) throws SecurityException {
        for (Entry e : all) {
            if (e.filter.symlink(f))
                return true;
        }
        return defaultAction();
    }

    @Override
    public boolean create(File f) throws SecurityException {
        for (Entry e : all) {
            if (e.filter.create(f))
                return true;
        }
        return defaultAction();
    }

    @Override
    public boolean delete(File f) throws SecurityException {
        for (Entry e : all) {
            if (e.filter.delete(f))
                return true;
        }
        return defaultAction();
    }

    @Override
    public boolean stat(File f) throws SecurityException {
        for (Entry e : all) {
            if (e.filter.stat(f))
                return true;
        }
        return defaultAction();
    }

    @Override public String toString() {
        return "FilePathFilterAggregator" + all;
    }

    static final ChannelProperty<FilePathFilterAggregator> KEY = new ChannelProperty<FilePathFilterAggregator>(FilePathFilterAggregator.class, "FilePathFilters");

    public static final int DEFAULT_ORDINAL = 0;
}
