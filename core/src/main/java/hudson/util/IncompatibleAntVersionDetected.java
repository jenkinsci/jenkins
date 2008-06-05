package hudson.util;

import hudson.remoting.Which;

import java.io.File;
import java.io.IOException;

/**
 * Model object used to display the error top page if
 * we find out that the container is picking up its own Ant and that's not 1.7.
 *
 * <p>
 * <tt>index.jelly</tt> would display a nice friendly error page.
 *
 * @author Kohsuke Kawaguchi
 */
public class IncompatibleAntVersionDetected extends ErrorObject {
    private final Class antClass;

    public IncompatibleAntVersionDetected(Class antClass) {
        this.antClass = antClass;
    }

    public File getWhereAntIsLoaded() throws IOException {
        return Which.jarFile(antClass);
    }
}
