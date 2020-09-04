package jenkins;

import hudson.FilePath;

import edu.umd.cs.findbugs.annotations.Nullable;
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
            throw new SecurityException("agent may not " + op + " " + f+"\nSee https://jenkins.io/redirect/security-144 for more details");
        return true;
    }
    
    private File normalize(File file){
        return new File(FilePath.normalize(file.getAbsolutePath()));
    }

    @Override
    public boolean read(File f) throws SecurityException {
        return noFalse("read",f,base.read(normalize(f)));
    }

    @Override
    public boolean write(File f) throws SecurityException {
        return noFalse("write",f,base.write(normalize(f)));
    }

    @Override
    public boolean symlink(File f) throws SecurityException {
        return noFalse("symlink",f,base.write(normalize(f)));
    }

    @Override
    public boolean mkdirs(File f) throws SecurityException {
        return noFalse("mkdirs",f,base.mkdirs(normalize(f)));
    }

    @Override
    public boolean create(File f) throws SecurityException {
        return noFalse("create",f,base.create(normalize(f)));
    }

    @Override
    public boolean delete(File f) throws SecurityException {
        return noFalse("delete",f,base.delete(normalize(f)));
    }

    @Override
    public boolean stat(File f) throws SecurityException {
        return noFalse("stat",f,base.stat(normalize(f)));
    }
}
