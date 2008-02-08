package hudson.util;

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
public class NoTempDir {
    public final IOException exception;

    public NoTempDir(IOException exception) {
        this.exception = exception;
    }

    public String getStackTrace() {
        StringWriter sw = new StringWriter();
        exception.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    public String getTempDir() {
        return System.getProperty("java.io.tmpdir");
    }
}
