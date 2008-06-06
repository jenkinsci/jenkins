package hudson.util;

import hudson.Functions;

import java.io.StringWriter;
import java.io.PrintWriter;

/**
 * Model object used to display the error top page if
 * we find that we don't have enough permissions to run.
 *
 * <p>
 * <tt>index.jelly</tt> would display a nice friendly error page.
 *
 * @author Kohsuke Kawaguchi
 */
public class InsufficientPermissionDetected extends ErrorObject {
    public final SecurityException exception;

    public InsufficientPermissionDetected(SecurityException e) {
        this.exception = e;
    }

    public String getExceptionTrace() {
        return Functions.printThrowable(exception);
    }
}
