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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.console.ConsoleNote;
import hudson.console.HyperlinkNote;
import hudson.remoting.Channel;
import hudson.util.StreamTaskListener;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Formatter;
import org.jenkinsci.remoting.SerializableOnlyOverRemoting;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.ProtectedExternally;

/**
 * Receives events that happen during some lengthy operation
 * that has some chance of failures, such as a build, SCM change polling,
 * agent launch, and so on.
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
 *
 * <p>
 * Implementations are generally expected to be remotable via {@link Channel}.
 *
 * @author Kohsuke Kawaguchi
 */
public interface TaskListener extends SerializableOnlyOverRemoting {
    /**
     * This writer will receive the output of the build
     */
    @NonNull
    PrintStream getLogger();

    /**
     * A charset to use for methods returning {@link PrintWriter}.
     * Should match that used to construct {@link #getLogger}.
     * @return by default, UTF-8
     */
    @Restricted(ProtectedExternally.class)
    @NonNull
    default Charset getCharset() {
        return StandardCharsets.UTF_8;
    }

    private PrintWriter _error(String prefix, String msg) {
        PrintStream out = getLogger();
        out.print(prefix);
        out.println(msg);

        Charset charset = getCharset();
        return new PrintWriter(new OutputStreamWriter(out, charset), true);
    }

    /**
     * Annotates the current position in the output log by using the given annotation.
     * If the implementation doesn't support annotated output log, this method might be no-op.
     * @since 1.349
     */
    @SuppressWarnings("rawtypes")
    default void annotate(ConsoleNote ann) throws IOException {
        ann.encodeTo(getLogger());
    }

    /**
     * Places a {@link HyperlinkNote} on the given text.
     *
     * @param url
     *      If this starts with '/', it's interpreted as a path within the context path.
     */
    default void hyperlink(String url, String text) throws IOException {
        annotate(new HyperlinkNote(url, text.length()));
        getLogger().print(text);
    }

    /**
     * An error in the build.
     *
     * @return
     *      A writer to receive details of the error.
     */
    @NonNull
    default PrintWriter error(String msg) {
        return _error("ERROR: ", msg);
    }

    /**
     * {@link Formatter#format(String, Object[])} version of {@link #error(String)}.
     */
    @NonNull
    default PrintWriter error(String format, Object... args) {
        return error(String.format(format, args));
    }

    /**
     * A fatal error in the build.
     *
     * @return
     *      A writer to receive details of the error.
     */
    @NonNull
    default PrintWriter fatalError(String msg) {
        return _error("FATAL: ", msg);
    }

    /**
     * {@link Formatter#format(String, Object[])} version of {@link #fatalError(String)}.
     */
    @NonNull
    default PrintWriter fatalError(String format, Object... args) {
        return fatalError(String.format(format, args));
    }

    /**
     * {@link TaskListener} that discards the output.
     */
    TaskListener NULL = new NullTaskListener();
}
