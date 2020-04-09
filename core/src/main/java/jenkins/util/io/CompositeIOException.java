/*
 * The MIT License
 *
 * Copyright (c) 2018 CloudBees, Inc.
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

package jenkins.util.io;

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@Restricted(NoExternalUse.class)
public class CompositeIOException extends IOException {
    private static final Logger LOGGER = Logger.getLogger(CompositeIOException.class.getName());
    /**
     * The maximum number of exceptions that can be contained in a single
     * {@code CompositeIOException}.
     * <p>
     * The number of exceptions is limited to avoid pathological cases where
     * where a huge number of exceptions could lead to excessive memory usage.
     * For example, if the number of exceptions was unlimited, a call to
     * {@code Util.deleteRecursive} could fail with a
     * {@code CompositeIOException} that contains an exception for every
     * single file inside of the directory.
     */
    public static final int MAX_SUPPRESSED_EXCEPTIONS = 15;

    private final List<IOException> exceptions;

    /**
     * Construct a {@code CompositeIOException} where the given list of
     * exceptions are added as suppressed exceptions to this exception.
     */
    public CompositeIOException(String message, @NonNull List<IOException> exceptions) {
        super(message);
        if (exceptions.size() > MAX_SUPPRESSED_EXCEPTIONS) {
            this.exceptions = new ArrayList<>(exceptions.subList(0, MAX_SUPPRESSED_EXCEPTIONS));
            LOGGER.log(Level.FINE, "Truncating {0} exceptions", exceptions.size() - MAX_SUPPRESSED_EXCEPTIONS);
        } else {
            this.exceptions = exceptions;
        }
        this.exceptions.forEach(this::addSuppressed);
    }

    public List<IOException> getExceptions() {
        return exceptions;
    }

    public CompositeIOException(String message, IOException... exceptions) {
        this(message, Arrays.asList(exceptions));
    }

    public UncheckedIOException asUncheckedIOException() {
        return new UncheckedIOException(this);
    }
}
