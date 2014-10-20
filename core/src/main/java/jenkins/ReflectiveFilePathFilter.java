package jenkins;

import java.io.File;

/**
 * Convenient adapter for {@link FilePathFilter} that allows you to handle all
 * operations as a single string argument.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.THU
 */
public abstract class ReflectiveFilePathFilter extends FilePathFilter {
    /**
     * @param name
     *      Name of the operation.
     */
    protected abstract boolean op(String name, File path) throws SecurityException;

    @Override
    public boolean read(File f) throws SecurityException {
        return op("read", f);
    }

    @Override
    public boolean write(File f) throws SecurityException {
        return op("write", f);
    }

    @Override
    public boolean symlink(File f) throws SecurityException {
        return op("symlink",f);
    }

    @Override
    public boolean mkdirs(File f) throws SecurityException {
        return op("mkdirs", f);
    }

    @Override
    public boolean create(File f) throws SecurityException {
        return op("create", f);
    }

    @Override
    public boolean delete(File f) throws SecurityException {
        return op("delete", f);
    }

    @Override
    public boolean stat(File f) throws SecurityException {
        return op("stat", f);
    }
}
