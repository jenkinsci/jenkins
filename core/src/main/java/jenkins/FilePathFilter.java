package jenkins;

import hudson.FilePath;
import hudson.remoting.Channel;
import hudson.remoting.ChannelBuilder;
import jenkins.security.ChannelConfigurator;

import javax.annotation.CheckForNull;
import java.io.File;

/**
 * Inspects {@link FilePath} access from remote channels.
 *
 * <p>
 * Returning {@code true} indicates that the access is accepted. No other {@link FilePathFilter}
 * will be consulted to reject the execution, and the access will go through. Returning {@link false}
 * indicates "I don't know". Other {@link FilePathFilter}s get to inspect the access, and they might
 * accept/reject access. And finally, throwing {@link SecurityException} is to reject the access.
 *
 * <p>
 * To insert a custom {@link FilePathFilter} into a connection,
 * see {@link ChannelConfigurator#onChannelBuilding(ChannelBuilder, Object)}
 *
 * @author Kohsuke Kawaguchi
 * @see FilePath
 * @since 1.THU
 */
public abstract class FilePathFilter {
    /**
     * Checks if the given file/directory can be read.
     *
     * On POSIX, this corresponds to the 'r' permission of the file/directory itself.
     */
    public boolean read(File f) throws SecurityException { return false; }

    /**
     * Checks if the given file can be written.
     *
     * On POSIX, this corresponds to the 'w' permission of the file itself.
     */
    public boolean write(File f) throws SecurityException { return false; }

    /**
     * Checks if a symlink can be created at 'f'
     *
     * On POSIX, this corresponds to the 'w' permission of the file itself.
     */
    public boolean symlink(File f) throws SecurityException { return false; }

    /**
     * Checks if the given directory can be created.
     *
     * On POSIX, this corresponds to the 'w' permission of the parent directory.
     */
    public boolean mkdirs(File f) throws SecurityException { return false; }

    /**
     * Checks if the given file can be created.
     *
     * On POSIX, this corresponds to the 'w' permission of the parent directory.
     */
    public boolean create(File f) throws SecurityException { return false; }

    /**
     * Checks if the given file/directory can be deleted.
     *
     * On POSIX, this corresponds to the 'w' permission of the parent directory.
     */
    public boolean delete(File f) throws SecurityException { return false; }

    /**
     * Checks if the metadata of the given file/directory (as opposed to the content) can be accessed.
     *
     * On POSIX, this corresponds to the 'r' permission of the parent directory.
     */
    public boolean stat(File f) throws SecurityException { return false; }


    public final void installTo(ChannelBuilder cb) {
        installTo(cb,FilePathFilterAggregator.DEFAULT_ORDINAL);
    }

    public final void installTo(ChannelBuilder cb, double d) {
        synchronized (cb) {
            FilePathFilterAggregator filters = (FilePathFilterAggregator) cb.getProperties().get(FilePathFilterAggregator.KEY);
            if (filters==null) {
                filters = new FilePathFilterAggregator();
                cb.withProperty(FilePathFilterAggregator.KEY,filters);
            }
            filters.add(this,d);
        }
    }

    public final void uninstallFrom(Channel ch) {
        synchronized (ch) {
            FilePathFilterAggregator filters = ch.getProperty(FilePathFilterAggregator.KEY);
            if (filters!=null) {
                filters.remove(this);
            }
        }
    }

    /**
     * Returns an {@link FilePathFilter} object that represents all the in-scope filters,
     * or null if none is needed.
     */
    public static @CheckForNull FilePathFilter current() {
        Channel ch = Channel.current();
        if (ch==null)   return null;

        return ch.getProperty(FilePathFilterAggregator.KEY);
    }

    /**
     * Immutable instance that represents the filter that allows everything.
     */
    public static final FilePathFilter UNRESTRICTED = new FilePathFilterAggregator() {
        @Override
        protected boolean defaultAction() throws SecurityException {
            return true;
        }

        @Override
        public void add(FilePathFilter f, double d) {
            // noop because we are immutable
        }

        @Override
        public String toString() {
            return "Unrestricted";
        }
    };
}
