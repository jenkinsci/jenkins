package jenkins.model.logging.impl;

import hudson.console.ConsoleLogFilter;
import hudson.model.AbstractBuild;
import hudson.model.BuildableItemWithBuildWrappers;
import hudson.model.Job;
import hudson.model.Run;
import hudson.tasks.BuildWrapper;
import jenkins.model.logging.Loggable;
import jenkins.model.logging.LoggingMethod;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Logging method which takes {@link OutputStream} as a destination.
 * This implementation consults with {@link ConsoleLogFilter} extensions in Jenkins.
 * @author Oleg Nenashev
 * @since TODO
 */
@Restricted(Beta.class)
public abstract class StreamLoggingMethod extends LoggingMethod {

    private static final Logger LOGGER =
            Logger.getLogger(StreamLoggingMethod.class.getName());

    public StreamLoggingMethod(@Nonnull Loggable loggable) {
        super(loggable);
    }

    public abstract OutputStream createOutputStream() throws IOException;

    /**
     * Defines an additional Console Log Filter to be used with the logging method.
     * This filter may be also used for log redirection and multi-reporting in very custom cases.
     * @return Log filter. {@code null} if no custom implementation
     */
    public ConsoleLogFilter getExtraConsoleLogFilter() {
        return null;
    }

    public final CloseableStreamBuildListener createBuildListener() throws IOException, InterruptedException {

        OutputStream logger = createOutputStream();
        if (!(loggable instanceof Run<?,?>)) {
            throw new IOException("Loggable is not a Run instance: " + loggable.getClass());
        }
        Run<?,?> build = (Run<?, ?>)loggable;

        // Global log filter
        for (ConsoleLogFilter filter : ConsoleLogFilter.all()) {
            logger = filter.decorateLogger(build, logger);
        }
        final Job<?, ?> project = build.getParent();

        // Project specific log filters
        if (project instanceof BuildableItemWithBuildWrappers && build instanceof AbstractBuild) {
            BuildableItemWithBuildWrappers biwbw = (BuildableItemWithBuildWrappers) project;
            for (BuildWrapper bw : biwbw.getBuildWrappersList()) {
                logger = bw.decorateLogger((AbstractBuild) build, logger);
            }
        }

        // Decorate logger by logging method of this build
        final ConsoleLogFilter f = getExtraConsoleLogFilter();
        if (f != null) {
            LOGGER.log(Level.INFO, "Decorated run {0} by a custom log filter {1}",
                    new Object[]{this, f});
            logger = f.decorateLogger(build, logger);
        }
        return new CloseableStreamBuildListener(logger, getOwner().getCharset());
    }
}
