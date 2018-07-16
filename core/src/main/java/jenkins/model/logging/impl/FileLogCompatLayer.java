package jenkins.model.logging.impl;

import hudson.AbortException;
import jenkins.model.logging.Loggable;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;

/**
 * Provides compatibility layer for logging.
 * @author Oleg Nenashev
 * @since TODO
 * @see jenkins.model.logging.Loggable
 * @see FileLogStorage
 * @see FileLogBrowser
 */
public interface FileLogCompatLayer {

    @Nonnull
    default File getLogFileOrFail(Loggable loggable) throws IOException {
        final File file = loggable.getLogFileCompatLocation();
        if (file == null) {
            throw new AbortException("File log compatibility layer is invoked for a loggable " +
                    "object which returned null for getLogFileCompatLocation(): " + loggable);
        }
        return file;
    }
}
