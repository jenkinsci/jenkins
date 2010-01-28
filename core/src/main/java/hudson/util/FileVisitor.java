package hudson.util;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.Serializable;

/**
 * Visits files in a directory recursively.

 * @since 1.343
 * @see DirScanner
 */
public abstract class FileVisitor {
    /**
     * Called for each file and directory that matches the criteria implied by {@link DirScanner}
     *
     * @param f
     *      Either a file or a directory.
     * @param relativePath
     *      The file/directory name in question
     */
    public abstract void visit(File f, String relativePath) throws IOException;

    /**
     * Decorates a visitor by a given filter.
     */
    public final FileVisitor with(FileFilter f) {
        if(f==null) return this;
        return new FilterFileVisitor(f,this);
    }

    private static final class FilterFileVisitor extends FileVisitor implements Serializable {
        private final FileFilter filter;
        private final FileVisitor visitor;

        private FilterFileVisitor(FileFilter filter, FileVisitor visitor) {
            this.filter = filter!=null ? filter : PASS_THROUGH;
            this.visitor = visitor;
        }

        public void visit(File f, String relativePath) throws IOException {
            if(f.isDirectory() || filter.accept(f))
                visitor.visit(f,relativePath);
        }

        private static final FileFilter PASS_THROUGH = new FileFilter() {
            public boolean accept(File pathname) {
                return true;
            }
        };

        private static final long serialVersionUID = 1L;
    }
}
