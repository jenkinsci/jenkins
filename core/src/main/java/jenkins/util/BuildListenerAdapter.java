/*
 * The MIT License
 *
 * Copyright 2014 Jesse Glick.
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

package jenkins.util;

import hudson.console.ConsoleNote;
import hudson.model.BuildListener;
import hudson.model.Cause;
import hudson.model.Result;
import hudson.model.TaskListener;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.List;

/**
 * Wraps a {@link TaskListener} as a {@link BuildListener} for compatibility with APIs which historically expected the latter.
 * Does not support {@link BuildListener#started} or {@link BuildListener#finished}.
 *
 * @since 1.577
 */
public final class BuildListenerAdapter implements BuildListener {

    private final TaskListener delegate;

    public BuildListenerAdapter(TaskListener delegate) {
        this.delegate = delegate;
    }

    @Override public void started(List<Cause> causes) {
        throw new UnsupportedOperationException();
    }

    @Override public void finished(Result result) {
        throw new UnsupportedOperationException();
    }

    @Override public PrintStream getLogger() {
        return delegate.getLogger();
    }

    @SuppressWarnings("rawtypes")
    @Override public void annotate(ConsoleNote ann) throws IOException {
        delegate.annotate(ann);
    }

    @Override public void hyperlink(String url, String text) throws IOException {
        delegate.hyperlink(url, text);
    }

    @Override public PrintWriter error(String msg) {
        return delegate.error(msg);
    }

    @Override public PrintWriter error(String format, Object... args) {
        return delegate.error(format, args);
    }

    @Override public PrintWriter fatalError(String msg) {
        return delegate.fatalError(msg);
    }

    @Override public PrintWriter fatalError(String format, Object... args) {
        return delegate.fatalError(format, args);
    }

    public static BuildListener wrap(TaskListener l) {
        if (l instanceof BuildListener) {
            return (BuildListener) l;
        } else {
            return new BuildListenerAdapter(l);
        }
    }
}
