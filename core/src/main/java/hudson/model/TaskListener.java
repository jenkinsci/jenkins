/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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
package hudson.model;

import hudson.console.ConsoleNote;
import hudson.console.HyperlinkNote;
import hudson.util.AbstractTaskListener;
import hudson.util.NullStream;
import hudson.util.StreamTaskListener;

import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Serializable;

/**
 * Receives events that happen during some lengthy operation
 * that has some chance of failures, such as a build, SCM change polling,
 * slave launch, and so on.
 *
 * <p>
 * This interface is implemented by Hudson core and passed to extension points so that
 * they can record the progress of the operation without really knowing how those information
 * and handled/stored by Hudson.
 *
 * <p>
 * The information is one way or the other made available to users, and
 * so the expectation is that when something goes wrong, enough information
 * shall be written to a {@link TaskListener} so that the user can diagnose
 * what's going wrong.
 *
 * <p>
 * {@link StreamTaskListener} is the most typical implementation of this interface.
 * All the {@link TaskListener} implementations passed to plugins from Hudson core are remotable.
 *
 * @see AbstractTaskListener
 * @author Kohsuke Kawaguchi
 */
public interface TaskListener extends Serializable {
    /**
     * This writer will receive the output of the build
     *
     * @return
     *      must be non-null.
     */
    PrintStream getLogger();

    /**
     * Annotates the current position in the output log by using the given annotation.
     * If the implementation doesn't support annotated output log, this method might be no-op.
     * @since 1.349
     */
    void annotate(ConsoleNote ann) throws IOException;

    /**
     * Places a {@link HyperlinkNote} on the given text.
     *
     * @param url
     *      If this starts with '/', it's interpreted as a path within the context path.
     */
    void hyperlink(String url, String text) throws IOException;

    /**
     * An error in the build.
     *
     * @return
     *      A writer to receive details of the error. Not null.
     */
    PrintWriter error(String msg);

    /**
     * {@link Formatter#format(String, Object[])} version of {@link #error(String)}.
     */
    PrintWriter error(String format, Object... args);

    /**
     * A fatal error in the build.
     *
     * @return
     *      A writer to receive details of the error. Not null.
     */
    PrintWriter fatalError(String msg);

    /**
     * {@link Formatter#format(String, Object[])} version of {@link #fatalError(String)}.
     */
    PrintWriter fatalError(String format, Object... args);

    /**
     * {@link TaskListener} that discards the output.
     */
    TaskListener NULL = new StreamTaskListener(new NullStream());
}
