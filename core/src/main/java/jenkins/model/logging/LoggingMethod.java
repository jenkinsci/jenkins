package jenkins.model.logging;

import hudson.Launcher;
import hudson.console.ConsoleLogFilter;
import hudson.model.BuildListener;
import hudson.model.Node;
import hudson.model.Run;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import hudson.model.TaskListener;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;

import java.io.IOException;
import java.nio.charset.Charset;

/**
 * Defines logging method for Jenkins runs.
 * This method defines how the log is persisted to the disk.
 * @author Oleg Nenashev
 * @see LoggingMethodLocator
 * @since TODO
 */
@Restricted(Beta.class)
public abstract class LoggingMethod extends LogHandler {

    public LoggingMethod(Loggable loggable) {
        super(loggable);
    }

    /**
     * Decorates logging on the Jenkins master side.
     * These filters can be also used for log redirection and multi-reporting.
     * @return Log filter on the master. {@code null} if no custom implementation
     */
    @CheckForNull
    public abstract TaskListener createTaskListener();

    /**
     * Decorates logging on the Jenkins master side.
     * This method should be always implemented, because it will be consuming the input events.
     * Streams can be converted to per-line events by higher-level abstractions.
     *
     * @return Build Listener
     * @throws IOException initialization error or wrong {@link Loggable} type
     */
     @Nonnull
     public abstract BuildListener createBuildListener() throws IOException, InterruptedException;

    /**
     * Gets default Log browser which should be used with this Logging method.
     * It allows setting a custom default LogBrowser if needed.
     * @return Log browser or {@code null} if not defined.
     */
    @CheckForNull
    public LogBrowser getDefaultLogBrowser() {
        return null;
    }

    /**
     * Decorates external process launcher running on a node.
     * It may be overridden to redirect logs to external destination
     * instead of sending them by default to the master.
     * @param original Original launcher
     * @param run Run, for which the decoration should be performed
     * @param node Target node. May be {@code master} as well
     * @return Decorated launcher or {@code original} launcher
     */
    @Nonnull
    public Launcher decorateLauncher(@Nonnull Launcher original,
        @Nonnull Run<?,?> run, @Nonnull Node node) {
        return original;
    }
}
