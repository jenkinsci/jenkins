package jenkins.model.logging.impl;

import hudson.console.ConsoleLogFilter;
import hudson.model.TaskListener;
import jenkins.model.logging.LogBrowser;
import jenkins.model.logging.Loggable;
import jenkins.model.logging.LoggingMethod;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;

import javax.annotation.CheckForNull;

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
        return null;
    }

    //@CheckForNull
    //@Override
    //public StreamRunListener createRunListener(Run<?, ?> build) {
    //    return null;
    //}

    @Override
    public ConsoleLogFilter createLoggerDecorator() {
        return null;
    }

    @Override
    public LogBrowser getDefaultLogBrowser() {
        return new NoopLogBrowser(getOwner());
    }
}
