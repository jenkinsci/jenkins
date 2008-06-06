package hudson.util;

import hudson.Functions;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Model object used to display the error top page if
 * there appears to be no temporary directory.
 *
 * <p>
 * <tt>index.jelly</tt> would display a nice friendly error page.
 *
 * @author Kohsuke Kawaguchi
 */
public class NoTempDir extends ErrorObject {
    public final IOException exception;

    public NoTempDir(IOException exception) {
        this.exception = exception;
    }

    public String getStackTrace() {
        return Functions.printThrowable(exception);
    }

    public String getTempDir() {
        return System.getProperty("java.io.tmpdir");
    }
}
