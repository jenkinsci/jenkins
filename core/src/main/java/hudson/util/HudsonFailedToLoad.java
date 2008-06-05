package hudson.util;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Model object used to display the generic error when Hudson start up fails fatally during initialization.
 *
 * <p>
 * <tt>index.jelly</tt> would display a nice friendly error page.
 *
 * @author Kohsuke Kawaguchi
 */
public class HudsonFailedToLoad extends ErrorObject {
    public final Throwable exception;

    public HudsonFailedToLoad(Throwable exception) {
        this.exception = exception;
    }

    public String getStackTrace() {
        StringWriter sw = new StringWriter();
        exception.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
