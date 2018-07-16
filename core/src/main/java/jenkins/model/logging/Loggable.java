package jenkins.model.logging;

import jenkins.model.logging.impl.FileLogStorage;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Interface which indicates that custom logging is applicable to the object.
 * @author Oleg Nenashev
 * @since TODO
 * @see LogBrowser
 * @see LoggingMethod
 */
public interface Loggable {

    @Nonnull
    default LoggingMethod getLoggingMethod() {
        return LoggingMethodLocator.locate(this);
    }

    /**
     * Determines a default logger to be used.
     * @return Default logger.
     */
    @Nonnull
    LoggingMethod getDefaultLoggingMethod();

    @Nonnull
    default LogBrowser getLogBrowser() {
        return LoggingMethodLocator.locateBrowser(this);
    }

    /**
     * Determines a default log browser to be used.
     * @return Default log browser.
     */
    @Nonnull
    LogBrowser getDefaultLogBrowser();

    /**
     * Returns {@code true} if the log file is no longer being updated.
     */
    public boolean isLoggingFinished();

    /**
     * Returns charset to be used.
     * New implementations are recommended to use {@code UTF-8}-only (default),
     * but the method can be overridden by legacy implementations.
     * @return Charset to be used.
     */
    @Nonnull
    default Charset getCharset() {
        return StandardCharsets.UTF_8;
    }

    /**
     * Provides legacy File storage location for compatibility implementations.
     * @return Log file or {@code null} if it is not supported.
     *         A non-existent file may be returned if log is missing in the compatibility location
     * @see jenkins.model.logging.impl.FileLogBrowser
     * @see jenkins.model.logging.impl.FileLogStorage
     */
    @CheckForNull
    default File getLogFileCompatLocation() {
        return null;
    }


    /**
     * Get temporary directory of the Loggable object (if exists).
     * This loggable directory may be used to store temporary files if needed.
     * @return Temporary directory or {@code null} if not defined
     */
    @CheckForNull
    default File getTmpDir() {
        return null;
    }
}
