package hudson.util;

import java.io.File;

/**
 * Model object used to display the error top page if
 * we couldn't create the home directory.
 *
 * <p>
 * <tt>index.jelly</tt> would display a nice friendly error page.
 *
 * @author Kohsuke Kawaguchi
 */
public class NoHomeDir extends ErrorObject {
    public final File home;

    public NoHomeDir(File home) {
        this.home = home;
    }
}
