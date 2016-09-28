package jenkins.model.logging;

import hudson.Launcher;
import hudson.console.ConsoleLogFilter;
import hudson.model.AbstractProject;
import hudson.model.Node;
import hudson.model.Run;
import java.io.OutputStream;
import java.io.Serializable;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import jenkins.model.Jenkins;
import jenkins.model.logging.LoggingDefinitionLauncherWrapper.DefaultLocalLauncher;
import jenkins.model.logging.LoggingDefinitionLauncherWrapper.DefaultRemoteLauncher;

/**
 * Defines logging method for Jenkins runs.
 * @author Oleg Nenashev
 * @see LoggingMethodLocator
 * @since TODO
 */
public abstract class LoggingMethod implements Serializable {

    /**
     * Decorates logging on the Jenkins master side.
     * These filters can be also used for log redirection and multi-reporting.
     * @param build Build to be decorated
     * @return Log filter on the master. {@code null} if no custom implementation
     */
    @CheckForNull
    public abstract ConsoleLogFilter createLoggerDecorator(Run<?,?> build);

    /**
     * Decorates external process launcher running on a node.
     * @param original Original launcher
     * @param run Run, for which the decoration should be performed
     * @param node Target node. May be {@code master} as well
     * @return Decorated launcher or {@code original} launcher
     */
    @Nonnull
    public Launcher decorateLauncher(@Nonnull Launcher original,
        @Nonnull Run<?,?> run, @Nonnull Node node) {
        if (node instanceof Jenkins) {
            return new DefaultLocalLauncher(original);
        } else {
            return new DefaultRemoteLauncher(original, run, this);
        }
    }

    /**
     * Provides the output stream for given run.
     * @param run Run
     * @return output stream
     */
    public abstract OutputStreamWrapper provideOutStream(Run run);

    /**
     * Provides the error stream for given run.
     * @param run Run
     * @return error stream
     */
    public abstract OutputStreamWrapper provideErrStream(Run run);

    /**
     * Fallback Logging methods for jobs, which do not define the implementation.
     */
    public static final LoggingMethod NOOP = new NoopLoggingMethod();
    private static class NoopLoggingMethod extends LoggingMethod {
        @Override
        public Launcher decorateLauncher(Launcher l, Run run, Node node) {
            return l;
        }

        @Override
        public ConsoleLogFilter createLoggerDecorator(Run<?, ?> build) {
            return null;
        }

        @Override
        public OutputStreamWrapper provideOutStream(Run run) {
            return null;
        }

        @Override
        public OutputStreamWrapper provideErrStream(Run run) {
            return null;
        }
    }

    /**
     * Default logging method for {@link AbstractProject} classes
     */
    public static class DefaultAbstractBuildLoggingMethod extends NoopLoggingMethod {
        // Default impl
    }

    public static abstract class OutputStreamWrapper extends OutputStream implements Serializable {
        public abstract Object readResolve();
    }
}
