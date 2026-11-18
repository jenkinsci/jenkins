package hudson.init.impl;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.init.Initializer;
import hudson.util.RunListInterruptedException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.EOFException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.kohsuke.MetaInfServices;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.UncaughtExceptionFilter;
import org.kohsuke.stapler.WebApp;

/**
 * Deals with exceptions that get thrown all the way up to the Stapler rendering layer.
 */
public class InstallUncaughtExceptionHandler {

    private static final Logger LOGGER = Logger.getLogger(InstallUncaughtExceptionHandler.class.getName());

    @Initializer
    public static void init(final Jenkins j) throws IOException {
        UncaughtExceptionFilter.setUncaughtExceptionHandler(j.getServletContext(), (e, context, req, rsp) -> handleException(j, e, req, rsp, 500));
        try {
            Thread.setDefaultUncaughtExceptionHandler(new DefaultUncaughtExceptionHandler());
            LOGGER.log(Level.FINE, "Successfully installed a global UncaughtExceptionHandler.");
        }
        catch (SecurityException ex) {
            LOGGER.log(Level.SEVERE,
                                                       "Failed to set the default UncaughtExceptionHandler. " +
                                                       "If any threads die due to unhandled coding errors then there will be no logging of this information. " +
                                                       "The lack of this diagnostic information will make it harder to track down issues which will reduce the supportability of Jenkins. " +
                                                       "It is highly recommended that you consult the documentation that comes with your servlet container on how to allow the " +
                                                       "`setDefaultUncaughtExceptionHandler` permission and enable it.", ex);
        }
    }

    static void handleException(Jenkins j, Throwable e, HttpServletRequest req, HttpServletResponse rsp, int code) throws IOException, ServletException {
        if (hasCause(e, RunListInterruptedException.class)) {
            handleInterruptedRequest(e, req, rsp);
            return;
        }
        if (rsp.isCommitted()) {
            LOGGER.log(hasCause(e, EOFException.class) ? Level.FINE : Level.WARNING, null, e);
            return;
        }
        String id = UUID.randomUUID().toString();
        LOGGER.log(hasCause(e, EOFException.class) ? Level.FINE : Level.WARNING, "Caught unhandled exception with ID " + id, e);
        req.setAttribute("jenkins.exception.id", id);
        req.setAttribute("jakarta.servlet.error.exception", e);
        rsp.setStatus(code);
        try {
            WebApp.get(j.getServletContext()).getSomeStapler().invoke(req, rsp, j, "/oops");
        } catch (ServletException | IOException x) {
            if (!Stapler.isSocketException(x)) {
                throw x;
            }
        }
    }

    private static boolean hasCause(Throwable e, Class<? extends Throwable> type) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable cause = e; cause != null && seen.add(cause); cause = cause.getCause()) {
            if (type.isInstance(cause)) {
                return true;
            }
        }
        return false;
    }

    private static void handleInterruptedRequest(Throwable e, HttpServletRequest req, HttpServletResponse rsp) throws IOException {
        LOGGER.log(Level.FINE, "Build history request interrupted", e);
        if (rsp.isCommitted()) {
            return;
        }
        rsp.resetBuffer();
        rsp.setHeader("Content-Length", null);
        rsp.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        rsp.setContentType("text/plain;charset=UTF-8");
        if (!"HEAD".equals(req.getMethod())) {
            String message = "Resource-intensive request interrupted before a complete result could be produced.";
            PrintWriter writer;
            try {
                writer = rsp.getWriter();
            } catch (IllegalStateException streamSelected) {
                // resetBuffer() leaves the servlet's output mode selected.
                rsp.getOutputStream().write(message.getBytes(StandardCharsets.UTF_8));
                return;
            }
            writer.write(message);
        }
    }

    @Restricted(NoExternalUse.class)
    @MetaInfServices
    public static class ErrorCustomizer implements HttpResponses.ErrorCustomizer {
        @CheckForNull
        @Override
        public HttpResponses.HttpResponseException handleError(int code, Throwable cause) {
            if (Jenkins.getInstanceOrNull() == null) {
                return null;
            }
            return new HttpResponses.HttpResponseException(cause) {
                @Override
                public void generateResponse(StaplerRequest2 req, StaplerResponse2 rsp, Object node) throws IOException, ServletException {
                    handleException(Jenkins.get(), cause, req, rsp, code);
                }
            };
        }
    }

    /** An UncaughtExceptionHandler that just logs the exception */
    private static class DefaultUncaughtExceptionHandler implements Thread.UncaughtExceptionHandler {

        @Override
        public void uncaughtException(Thread t, Throwable ex) {
            // if this was an OutOfMemoryError then all bets about logging are off - but in the absence of anything else...
            LOGGER.log(Level.SEVERE,
                       "A thread (" + t.getName() + '/' + t.getId()
                                     + ") died unexpectedly due to an uncaught exception. This may leave your server corrupted and usually indicates a software bug.",
                       ex);
        }

    }

    private InstallUncaughtExceptionHandler() {}
}
