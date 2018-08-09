/*
 * The MIT License
 *
 * Copyright 2018 CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package jenkins.model.logging;

import jenkins.model.logging.impl.CompatFileLogStorage;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Interface which indicates that custom logging is applicable to the object.
 * @author Oleg Nenashev
 * @since TODO
 * @see LogStorage
 */
@Restricted(Beta.class)
public interface Loggable {

    @Nonnull
    default LogStorage getLogStorage() {
        return LogStorageFactory.locate(this);
    }

    /**
     * Determines a default logger to be used.
     * @return Default logger.
     */
    @Nonnull
    LogStorage getDefaultLogStorage();

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
     * @see CompatFileLogStorage
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
