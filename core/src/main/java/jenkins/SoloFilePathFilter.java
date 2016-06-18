package jenkins;

import javax.annotation.Nullable;
import java.io.File;

/**
 * Variant of {@link FilePathFilter} that assumes it is the sole actor.
 *
 * It throws {@link SecurityException} instead of returning false. This makes it the
 * convenient final wrapper for the caller.
 *
 * @author Kohsuke Kawaguchi
 */
public final class SoloFilePathFilter extends FilePathFilter {
    private final FilePathFilter base;

    private SoloFilePathFilter(FilePathFilter base) {
        this.base = base;
    }

    /**
     * Null-safe constructor.
     */
    public static @Nullable SoloFilePathFilter wrap(@Nullable FilePathFilter base) {
        if (base==null)     return null;
        return new SoloFilePathFilter(base);
    }

    private boolean noFalse(String op, File f, boolean b) {
        if (!b)
            throw new SecurityException("agent may not " + op + " " + f+"\nSee http://jenkins-ci.org/security-144 for more details");
        return true;
    }

    @Override
    public boolean read(File f) throws SecurityException {
        return noFalse("read",f,base.read(f));
    }

    @Override
    public boolean write(File f) throws SecurityException {
        return noFalse("write",f,base.write(f));
    }

    @Override
    public boolean symlink(File f) throws SecurityException {
        return noFalse("symlink",f,base.write(f));
    }

    @Override
    public boolean mkdirs(File f) throws SecurityException {
        return noFalse("mkdirs",f,base.mkdirs(f));
    }

    @Override
    public boolean create(File f) throws SecurityException {
        return noFalse("create",f,base.create(f));
    }

    @Override
    public boolean delete(File f) throws SecurityException {
        return noFalse("delete",f,base.delete(f));
    }

    @Override
    public boolean stat(File f) throws SecurityException {
        return noFalse("stat",f,base.stat(f));
    }
}
