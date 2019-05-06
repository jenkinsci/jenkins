package hudson.init.impl;

import hudson.init.Initializer;
import jenkins.model.Jenkins;
import jenkins.telemetry.impl.java11.MissingClassTelemetry;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.WebApp;
import org.kohsuke.stapler.compression.CompressionFilter;

import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import java.io.EOFException;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Deals with exceptions that get thrown all the way up to the Stapler rendering layer.
 */
public class InstallUncaughtExceptionHandler {

    private static final Logger LOGGER = Logger.getLogger(InstallUncaughtExceptionHandler.class.getName());

    private static final Set exceptionsToReportToJava11Telemetry =
            new HashSet<Class>(Arrays.asList(ClassNotFoundException.class, NoClassDefFoundError.class));

    @Initializer
    public static void init(final Jenkins j) throws IOException {
        CompressionFilter.setUncaughtExceptionHandler(j.servletContext, (e, context, req, rsp) -> {
                if (rsp.isCommitted()) {
                    LOGGER.log(isEOFException(e) ? Level.FINE : Level.WARNING, null, e);
                    return;
                }
                req.setAttribute("javax.servlet.error.exception",e);
                try {
                    // If we have an exception, let's see if it's related with missing classes on Java 11. We reach
                    // here with a ClassNotFoundException in an action, for example.
                    reportMissingClassJava11Telemetry(e);
                    WebApp.get(j.servletContext).getSomeStapler().invoke(req, rsp, j, "/oops");
                } catch (ServletException | IOException x) {
                    if (!Stapler.isSocketException(x)) {
                        throw x;
                    }
                }
        });
        try {
            Thread.setDefaultUncaughtExceptionHandler(new DefaultUncaughtExceptionHandler());
            LOGGER.log(Level.FINE, "Successfully installed a global UncaughtExceptionHandler.");
        }
        catch (SecurityException ex) {
            LOGGER.log(Level.SEVERE,
                                                       "Failed to set the default UncaughtExceptionHandler.  " +
                                                       "If any threads die due to unhandled coding errors then there will be no logging of this information.  " +
                                                       "The lack of this diagnostic information will make it harder to track down issues which will reduce the supportability of Jenkins.  " +
                                                       "It is highly recommended that you consult the documentation that comes with you servlet container on how to allow the " +
                                                       "`setDefaultUncaughtExceptionHandler` permission and enable it.", ex);
        }
    }

    private static boolean isEOFException(Throwable e) {
        if (e == null) {
            return false;
        } else if (e instanceof EOFException) {
            return true;
        } else {
            return isEOFException(e.getCause());
        }
    }

    /** An UncaughtExceptionHandler that just logs the exception */
    private static class DefaultUncaughtExceptionHandler implements Thread.UncaughtExceptionHandler {

        @Override
        public void uncaughtException(Thread t, Throwable ex) {
            // if this was an OutOfMemoryError then all bets about logging are off - but in the absence of anything else...
            LOGGER.log(Level.SEVERE,
                       "A thread (" + t.getName() + '/' + t.getId()
                                     + ") died unexpectedly due to an uncaught exception, this may leave your Jenkins in a bad way and is usually indicative of a bug in the code.",
                       ex);

            // If we have an exception, let's see if it's related with missing classes on Java 11 We reach
            // here with a ClassNotFoundException in an action, for example.
            reportMissingClassJava11Telemetry(ex);
        }

    }

    private InstallUncaughtExceptionHandler() {}

    /**
     * Report the class not found via {@link MissingClassTelemetry} if this exception is related.
     * @param e the exception to look into
     * @return true if this exception or a cause of this one or a suppressed exception of this one was reported.
     * {@link #exceptionsToReportToJava11Telemetry}.
     */
    private static boolean reportMissingClassJava11Telemetry(@Nonnull Throwable e) {
        // It this exception is the one searched
        if (isMissedClassRelatedException(e)) {
            MissingClassTelemetry.reportException(e);
            return true;
        }

        // We search in its cause exception
        if (e.getCause() != null) {
            reportMissingClassJava11Telemetry(e.getCause());
            return true;
        }

        // We search in its suppressed exceptions
        for (Throwable suppressed: e.getSuppressed()) {
            if (suppressed != null) {
                if (reportMissingClassJava11Telemetry(suppressed)) {
                    return true;
                }
            }
        }

        // If this exception or its ancestors are not related with missed classes
        return false;
    }

    /**
     * Check if the exception specified is related with a missed class, that is, defined in the
     * {@link #exceptionsToReportToJava11Telemetry} method.
     * @param e the exception to look into
     * @return true if the class is related with missed classes.
     */
    private static boolean isMissedClassRelatedException(Throwable e) {
        return exceptionsToReportToJava11Telemetry.contains(e.getClass());
    }
}
