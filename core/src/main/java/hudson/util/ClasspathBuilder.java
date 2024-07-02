package hudson.util;

import hudson.FilePath;
import hudson.remoting.Which;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Used to build up an argument in the classpath format.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.300
 */
public class ClasspathBuilder implements Serializable {
    private static final long serialVersionUID = 1L;

    private final List<String> args = new ArrayList<>();

    /**
     * Adds a single directory or a jar file.
     */
    public ClasspathBuilder add(File f) {
        return add(f.getAbsolutePath());
    }

    /**
     * Adds a single directory or a jar file.
     */
    public ClasspathBuilder add(FilePath f) {
        return add(f.getRemote());
    }

    /**
     * Adds a single directory or a jar file.
     */
    public ClasspathBuilder add(String path) {
        args.add(path);
        return this;
    }

    /**
     * Adds a jar file that contains the given class.
     * @since 1.361
     */
    public ClasspathBuilder addJarOf(Class c) throws IOException {
        return add(Which.jarFile(c));
    }

    /**
     * Adds all the files that matches the given glob in the directory.
     *
     * @see FilePath#list(String)
     */
    public ClasspathBuilder addAll(FilePath base, String glob) throws IOException, InterruptedException {
        for (FilePath item : base.list(glob))
            add(item);
        return this;
    }

    /**
     * Returns the string representation of the classpath.
     */
    @Override
    public String toString() {
        return String.join(File.pathSeparator, args);
    }
}
