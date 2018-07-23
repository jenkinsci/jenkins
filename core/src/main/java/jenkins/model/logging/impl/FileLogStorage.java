package jenkins.model.logging.impl;

import hudson.console.ConsoleLogFilter;
import hudson.model.Run;
import hudson.model.TaskListener;
import jenkins.model.logging.Loggable;
import jenkins.model.logging.LoggingMethod;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;

import javax.annotation.CheckForNull;

/**
 * Legacy File Log storage implementation.
 * When used, logging always goes to a file on the naster side.
 * @author Oleg Nenashev
 * @since TODO
 */
@Restricted(Beta.class)
public class FileLogStorage extends LoggingMethod implements FileLogCompatLayer {

    public FileLogStorage(Loggable loggable) {
        super(loggable);
    }

    @CheckForNull
    @Override
    public TaskListener createTaskListener() {
        return null;
    }

    @CheckForNull
    @Override
    public ConsoleLogFilter createLoggerDecorator() {
        return null;
    }
}
