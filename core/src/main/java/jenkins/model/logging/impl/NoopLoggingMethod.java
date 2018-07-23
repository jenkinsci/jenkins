package jenkins.model.logging.impl;

import hudson.model.BuildListener;
import hudson.model.TaskListener;
import jenkins.model.logging.LogBrowser;
import jenkins.model.logging.Loggable;
import jenkins.model.logging.LoggingMethod;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.PrintStream;

/**
 * Default Logging Method implementation which does nothing
 * @author Oleg Nenashev
 * @since TODO
 */
@Restricted(Beta.class)
public class NoopLoggingMethod extends LoggingMethod {

    public NoopLoggingMethod(Loggable loggable) {
        super(loggable);
    }

    @CheckForNull
    @Override
    public TaskListener createTaskListener() {
        return TaskListener.NULL;
    }

    @Nonnull
    @Override
    public BuildListener createBuildListener() throws IOException, InterruptedException {
        return new BuildListener() {
            @Nonnull
            @Override
            public PrintStream getLogger() {
                return TaskListener.NULL.getLogger();
            }
        };
    }

    @Override
    public LogBrowser getDefaultLogBrowser() {
        return new NoopLogBrowser(getOwner());
    }
}
