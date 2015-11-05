package hudson.util;

import hudson.Util;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.types.FileSet;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.Serializable;

import static hudson.Util.fixEmpty;

/**
 * Visits a directory and its contents and pass them to the {@link FileVisitor}.
 *
 * A {@link DirScanner} encapsulates the logic of how it filters files in the directory. It is also remotable.
 *
 * @since 1.343
 * @see FileVisitor
 */
public abstract class DirScanner implements Serializable {
    /**
     * Scans the given directory and pass files onto the given visitor.
     */
    public abstract void scan(File dir, FileVisitor visitor) throws IOException;

    /**
     * @since 1.532
     */
    protected final void scanSingle(File f, String relative, FileVisitor visitor) throws IOException {
        if (visitor.understandsSymlink()) {
            try {
                String target;
                try {
                    target = Util.resolveSymlink(f);
                } catch (IOException x) { // JENKINS-13202
                    target = null;
                }
                if (target != null) {
                    visitor.visitSymlink(f, target, relative);
                    return;
                }
            } catch (InterruptedException e) {
                throw (IOException) new InterruptedIOException().initCause(e);
            }
        }
        visitor.visit(f, relative);
    }

    /**
     * Scans everything recursively.
     * <p>Note that all file paths are prefixed by the name of the root directory.
     * For example, when scanning a directory {@code /tmp/dir} containing a file {@code file},
     * the {@code relativePath} sent to the {@link FileVisitor} will be {@code dir/file}.
     */
    public static class Full extends DirScanner {
        private void scan(File f, String path, FileVisitor visitor) throws IOException {
            if (f.canRead()) {
                scanSingle(f, path + f.getName(), visitor);
                if(f.isDirectory()) {
                    for( File child : f.listFiles() )
                        scan(child,path+f.getName()+'/',visitor);
                }
            }
        }

        public void scan(File dir, FileVisitor visitor) throws IOException {
            scan(dir,"",visitor);
        }

        private static final long serialVersionUID = 1L;
    }

    /**
     * Scans by filtering things out from {@link FileFilter}.
     * <p>An initial basename is prepended as with {@link Full}.
     */
    public static class Filter extends Full {
        private final FileFilter filter;

        public Filter(FileFilter filter) {
            this.filter = filter;
        }

        @Override
        public void scan(File dir, FileVisitor visitor) throws IOException {
            super.scan(dir,visitor.with(filter));
        }

        private static final long serialVersionUID = 1L;
    }

    /**
     * Scans by using Ant GLOB syntax.
     * <p>An initial basename is prepended as with {@link Full} <strong>if the includes and excludes are blank</strong>.
     * Otherwise there is no prepended path. So for example when scanning a directory {@code /tmp/dir} containing a file {@code file},
     * the {@code relativePath} sent to the {@link FileVisitor} will be {@code dir/file} if {@code includes} is blank
     * but {@code file} if it is {@code **}. (This anomaly is historical.)
     */
    public static class Glob extends DirScanner {
        private final String includes, excludes;

        private boolean useDefaultExcludes = true;

        public Glob(String includes, String excludes) {
            this.includes = includes;
            this.excludes = excludes;
        }

        public Glob(String includes, String excludes, boolean useDefaultExcludes) {
            this(includes, excludes);
            this.useDefaultExcludes = useDefaultExcludes;
        }

        public void scan(File dir, FileVisitor visitor) throws IOException {
            if(fixEmpty(includes)==null && excludes==null) {
                // optimization
                new Full().scan(dir,visitor);
                return;
            }

            FileSet fs = Util.createFileSet(dir,includes,excludes);
            fs.setDefaultexcludes(useDefaultExcludes);

            if(dir.exists()) {
                DirectoryScanner ds = fs.getDirectoryScanner(new org.apache.tools.ant.Project());
                for( String f : ds.getIncludedFiles()) {
                    File file = new File(dir, f);
                    scanSingle(file, f, visitor);
                }
            }
        }

        private static final long serialVersionUID = 1L;
    }

    private static final long serialVersionUID = 1L;
}
