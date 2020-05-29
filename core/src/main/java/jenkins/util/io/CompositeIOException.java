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

@Restricted(NoExternalUse.class)
public class CompositeIOException extends IOException {
    private static final long serialVersionUID = 121943141387608148L;

    /**
     * The maximum number of exceptions that can be reported by a single
     * {@code CompositeIOException}.
     * <p>
     * The number of exceptions is limited to avoid pathological cases where
     * a huge number of exceptions could lead to excessive memory usage.
     * For example, if the number of exceptions was unlimited, a call to
     * {@code Util.deleteRecursive} could fail with a
     * {@code CompositeIOException} that contains an exception for every
     * single file inside of the directory.
     */
    public static final int EXCEPTION_LIMIT = 10;

    private final List<IOException> exceptions;

    /**
     * Construct a new {@code CompositeIOException} where the given list of
     * exceptions are added as suppressed exceptions to the new exception.
     * <p>
     * If the given list of exceptions is longer than {@link #EXCEPTION_LIMIT},
     * the list will be truncated to that length, and a message indicating the
     * number of discarded exceptions will be appended to the original message.
     */
    public CompositeIOException(String message, @NonNull List<IOException> exceptions) {
        super(message + getDiscardedExceptionsMessage(exceptions));
        if (exceptions.size() > EXCEPTION_LIMIT) {
            this.exceptions = new ArrayList<>(exceptions.subList(0, EXCEPTION_LIMIT));
        } else {
            this.exceptions = exceptions;
        }
        this.exceptions.forEach(this::addSuppressed);
    }

    /**
     * @see CompositeIOException(String, List)
     */
    public CompositeIOException(String message, IOException... exceptions) {
        this(message, Arrays.asList(exceptions));
    }

    public List<IOException> getExceptions() {
        return exceptions;
    }

    public UncheckedIOException asUncheckedIOException() {
        return new UncheckedIOException(this);
    }

    private static String getDiscardedExceptionsMessage(List<IOException> exceptions) {
        if (exceptions.size() > EXCEPTION_LIMIT) {
            return " (Discarded " + (exceptions.size() - EXCEPTION_LIMIT) + " additional exceptions)";
        } else {
            return "";
        }
    }
}
