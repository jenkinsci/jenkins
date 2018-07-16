package jenkins.model.logging.impl;

import hudson.console.AnnotatedLargeText;
import jenkins.model.logging.LogBrowser;
import jenkins.model.logging.Loggable;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.logging.Logger;

/**
 * Default Log Browser implementation which does nothing.
 * @author Oleg Nenashev
 * @since TODO
 * @see NoopLoggingMethod
 */
@Restricted(Beta.class)
public class NoopLogBrowser extends LogBrowser {

    private static final Logger LOGGER = Logger.getLogger(NoopLogBrowser.class.getName());

    public NoopLogBrowser(Loggable loggable) {
        super(loggable);
    }

    @Override
    public AnnotatedLargeText overallLog() {
        return new BrokenAnnotatedLargeText(
                new UnsupportedOperationException("Browsing is not supported"),
                getOwner().getCharset());
    }

    @Override
    public AnnotatedLargeText stepLog(@CheckForNull String stepId, boolean b) {
        return overallLog();
    }

}
