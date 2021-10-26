package jenkins;

import edu.umd.cs.findbugs.annotations.Nullable;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.FilePath;
import jenkins.util.SystemProperties;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import java.io.File;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Variant of {@link FilePathFilter} that assumes it is the sole actor.
 *
 * It throws {@link SecurityException} instead of returning false. This makes it the
 * convenient final wrapper for the caller.
 *
 * @author Kohsuke Kawaguchi
 */
public final class SoloFilePathFilter extends FilePathFilter {

    private static final Logger LOGGER = Logger.getLogger(SoloFilePathFilter.class.getName());

    @SuppressFBWarnings("MS_SHOULD_BE_FINAL")
    @Restricted(NoExternalUse.class)
    public static /* non-final for Groovy */ boolean REDACT_ERRORS = SystemProperties.getBoolean(SoloFilePathFilter.class.getName() + ".redactErrors", true);

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
        if (!b) {
            final String detailedMessage = "Agent may not '" + op + "' at '" + f + "'. See https://www.jenkins.io/redirect/security-144 for more information.";
            if (REDACT_ERRORS) {
                // We may end up trying to access file paths indirectly, e.g. FilePath#listFiles starts in an allowed dir but follows symlinks outside, so do not disclose paths in error message
                UUID uuid = UUID.randomUUID();
                LOGGER.log(Level.WARNING, () -> uuid + ": " + detailedMessage);
                throw new SecurityException("Agent may not access a file path. See the system log for more details about the error ID '" + uuid + "' and https://www.jenkins.io/redirect/security-144 for more information.");
            } else {
                throw new SecurityException(detailedMessage);
            }
        }
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
        return noFalse("symlink",f,base.symlink(normalize(f)));
    }

    @Override
    public boolean mkdirs(File f) throws SecurityException {
        // mkdirs is special because it could operate on parents of the specified path
        File reference = normalize(f);
        while (reference != null && !reference.exists()) {
            noFalse("mkdirs", f, base.mkdirs(reference)); // Pass f as reference into the error to be vague
            reference = reference.getParentFile();
        }
        return true;
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
