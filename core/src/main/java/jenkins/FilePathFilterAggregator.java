package jenkins;

import hudson.FilePath;
import hudson.remoting.ChannelProperty;

import java.io.File;
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
    private final CopyOnWriteArrayList<FilePathFilter> all = new CopyOnWriteArrayList<FilePathFilter>();

    public void add(FilePathFilter f) {
        all.add(f);
    }

    public void remove(FilePathFilter f) {
        all.remove(f);
    }

    /**
     * If no filter cares, what to do?
     */
    protected boolean defaultAction() throws SecurityException {
        return false;
    }

    @Override
    public boolean read(File f) throws SecurityException {
        for (FilePathFilter filter : all) {
            if (filter.read(f))
                return true;
        }
        return defaultAction();
    }

    @Override
    public boolean mkdirs(File f) throws SecurityException {
        for (FilePathFilter filter : all) {
            if (filter.mkdirs(f))
                return true;
        }
        return defaultAction();
    }

    @Override
    public boolean write(File f) throws SecurityException {
        for (FilePathFilter filter : all) {
            if (filter.write(f))
                return true;
        }
        return defaultAction();
    }

    @Override
    public boolean create(File f) throws SecurityException {
        for (FilePathFilter filter : all) {
            if (filter.create(f))
                return true;
        }
        return defaultAction();
    }

    @Override
    public boolean delete(File f) throws SecurityException {
        for (FilePathFilter filter : all) {
            if (filter.delete(f))
                return true;
        }
        return defaultAction();
    }

    @Override
    public boolean stat(File f) throws SecurityException {
        for (FilePathFilter filter : all) {
            if (filter.stat(f))
                return true;
        }
        return defaultAction();
    }

    @Override public String toString() {
        return "FilePathFilterAggregator" + all;
    }

    static final ChannelProperty<FilePathFilterAggregator> KEY = new ChannelProperty<FilePathFilterAggregator>(FilePathFilterAggregator.class, "FilePathFilters");
}
