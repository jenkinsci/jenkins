package jenkins.model.logging;

import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.Node;
import hudson.model.Run;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import hudson.model.TaskListener;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;

import java.io.IOException;

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
     * Decorates logging on the Jenkins master side for non-{@link Run} loggable items.
     * @return Log filter on the master.
     *         {@code null} if the implementation does not support task logging
     * @throws IOException initialization error or wrong {@link Loggable} type
     * @throws InterruptedException one of the build listener decorators has been interrupted.
     */
    @CheckForNull
    public abstract TaskListener createTaskListener() throws IOException, InterruptedException;

    /**
     * Decorates logging on the Jenkins master side.
     * This method should be always implemented, because it will be consuming the input events.
     * Streams can be converted to per-line events by higher-level abstractions.
     *
     * @return Build Listener
     * @throws IOException initialization error or wrong {@link Loggable} type
     * @throws InterruptedException one of the build listener decorators has
     *            been interrupted.
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
