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

    public void read(File f) throws SecurityException {
        for (FilePathFilter filter : all) {
            filter.read(f);
        }
    }

    @Override
    public void mkdirs(File f) throws SecurityException {
        for (FilePathFilter filter : all) {
            filter.mkdirs(f);
        }
    }

    public void write(File f) throws SecurityException {
        for (FilePathFilter filter : all) {
            filter.write(f);
        }
    }
    public void create(File f) throws SecurityException {
        for (FilePathFilter filter : all) {
            filter.create(f);
        }
    }
    public void delete(File f) throws SecurityException {
        for (FilePathFilter filter : all) {
            filter.delete(f);
        }
    }
    public void stat(File f) throws SecurityException {
        for (FilePathFilter filter : all) {
            filter.stat(f);
        }
    }

    @Override public String toString() {
        return "FilePathFilterAggregator" + all;
    }

    static final ChannelProperty<FilePathFilterAggregator> KEY = new ChannelProperty<FilePathFilterAggregator>(FilePathFilterAggregator.class, "FilePathFilters");
}
