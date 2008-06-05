package hudson.util;

import java.util.Map;

/**
 * Model object used to display the error top page if
 * we find out that XStream is running in pure-java mode.
 *
 * <p>
 * <tt>index.jelly</tt> would display a nice friendly error page.
 *
 * @author Kohsuke Kawaguchi
 */
public class IncompatibleVMDetected extends ErrorObject {

    public Map getSystemProperties() {
        return System.getProperties();
    }
}
