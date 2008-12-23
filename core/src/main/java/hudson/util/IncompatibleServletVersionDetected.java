package hudson.util;

import hudson.remoting.Which;

import java.io.File;
import java.io.IOException;
import java.net.URL;

/**
 * Model object used to display the error top page if
 * we find out that the container doesn't support servlet 2.4.
 *
 * <p>
 * <tt>index.jelly</tt> would display a nice friendly error page.
 *
 * @author Kohsuke Kawaguchi
 */
public class IncompatibleServletVersionDetected extends ErrorObject {
    private final Class servletClass;

    public IncompatibleServletVersionDetected(Class servletClass) {
        this.servletClass = servletClass;
    }
    
    public URL getWhereServletIsLoaded() throws IOException {
        return Which.jarURL(servletClass);
    }
}
