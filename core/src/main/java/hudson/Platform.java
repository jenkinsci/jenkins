package hudson;

import java.io.File;

/**
 * Strategy object that absorbs the platform differences.
 *
 * <p>
 * Do not switch/case on this enum, or do a comparison, as we may add new constants.
 *
 * @author Kohsuke Kawaguchi
 */
public enum Platform {
    WINDOWS(';'),UNIX(':');

    /**
     * The character that separates paths in environment variables like PATH and CLASSPATH. 
     * On Windows ';' and on Unix ':'.
     *
     * @see File#pathSeparator
     */
    public final char pathSeparator;

    private Platform(char pathSeparator) {
        this.pathSeparator = pathSeparator;
    }

    public static Platform current() {
        if(File.pathSeparatorChar==':') return UNIX;
        return WINDOWS;
    }
}
