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

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.List;

@Restricted(NoExternalUse.class)
public class CompositeIOException extends IOException {
    private final List<IOException> exceptions;

    public CompositeIOException(String message, @Nonnull List<IOException> exceptions) {
        super(message);
        this.exceptions = exceptions;
    }

    public CompositeIOException(String message, IOException... exceptions) {
        this(message, Arrays.asList(exceptions));
    }

    public List<IOException> getExceptions() {
        return exceptions;
    }

    @Override
    public void printStackTrace(PrintStream s) {
        super.printStackTrace(s);
        for (IOException exception : exceptions) {
            exception.printStackTrace(s);
        }
    }

    @Override
    public void printStackTrace(PrintWriter s) {
        super.printStackTrace(s);
        for (IOException exception : exceptions) {
            exception.printStackTrace(s);
        }
    }

    public UncheckedIOException asUncheckedIOException() {
        return new UncheckedIOException(this);
    }
}
