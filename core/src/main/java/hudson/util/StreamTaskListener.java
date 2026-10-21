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

package hudson.util;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.CloseProofOutputStream;
import hudson.model.TaskListener;
import hudson.remoting.RemoteOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.util.SystemProperties;
import org.kohsuke.stapler.framework.io.WriterOutputStream;

/**
 * {@link TaskListener} that generates output into a single stream.
 *
 * <p>
 * This object is remotable.
 *
 * @author Kohsuke Kawaguchi
 */
@SuppressWarnings("deprecation") // to preserve serial form
public class StreamTaskListener extends AbstractTaskListener implements TaskListener, Closeable {
    @NonNull
    private PrintStream out;
    @CheckForNull
    private Charset charset;

    /**
     * @deprecated as of 1.349
     *      The caller should use {@link #StreamTaskListener(OutputStream, Charset)} to pass in
     *      the charset and output stream separately, so that this class can handle encoding correctly,
     *      or use {@link #fromStdout()} or {@link #fromStderr()}.
     */
    @Deprecated
    public StreamTaskListener(@NonNull PrintStream out) {
        this(out, null);
    }

    /**
     * @deprecated as of 2.328
     *      The caller should use {@link #StreamTaskListener(OutputStream, Charset)} to pass in
     *      the charset and output stream separately, so that this class can handle encoding correctly,
     *      or use {@link #fromStdout()} or {@link #fromStderr()}.
     */
    @Deprecated
    public StreamTaskListener(@NonNull OutputStream out) {
        this(out, null);
    }

    public StreamTaskListener(@NonNull OutputStream out, @CheckForNull Charset charset) {
        if (charset == null) {
            this.out = out instanceof PrintStream ? (PrintStream) out : new PrintStream(out, false, Charset.defaultCharset());
        } else {
            this.out = new PrintStream(out, false, charset);
        }
        this.charset = charset;
    }

    /**
     * @deprecated as of 2.329
     *      The caller should use {@link #StreamTaskListener(File, Charset)} to pass in
     *      the charset and file separately, so that this class can handle encoding correctly.
     */
    @Deprecated
    public StreamTaskListener(@NonNull File out) throws IOException {
        this(out, null);
    }

    public StreamTaskListener(@NonNull File out, @CheckForNull Charset charset) throws IOException {
        // don't do buffering so that what's written to the listener
        // gets reflected to the file immediately, which can then be
        // served to the browser immediately
        this(Files.newOutputStream(asPath(out)), charset);
    }

    private static Path asPath(@NonNull File out) throws IOException {
        try {
            return out.toPath();
        } catch (InvalidPathException e) {
            throw new IOException(e);
        }
    }

    /**
     * Constructs a {@link StreamTaskListener} that sends the output to a specified file.
     *
     * @param out     the file.
     * @param append  if {@code true}, then output will be written to the end of the file rather than the beginning.
     * @param charset if non-{@code null} then the charset to use when writing.
     * @throws IOException if the file could not be opened.
     * @since 1.651
     */
    public StreamTaskListener(@NonNull File out, boolean append, @CheckForNull Charset charset) throws IOException {
        // don't do buffering so that what's written to the listener
        // gets reflected to the file immediately, which can then be
        // served to the browser immediately
        this(Files.newOutputStream(
                asPath(out),
                StandardOpenOption.CREATE, append ? StandardOpenOption.APPEND : StandardOpenOption.TRUNCATE_EXISTING
                ),
                charset
        );
    }

    public StreamTaskListener(@NonNull Writer w) throws IOException {
        this(new WriterOutputStream(w));
    }

    /**
     * @deprecated as of 1.349
     *      Use {@link #NULL}
     */
    @Deprecated
    public StreamTaskListener() throws IOException {
        this(OutputStream.nullOutputStream());
    }

    public static StreamTaskListener fromStdout() {
        return new StreamTaskListener(System.out, Charset.defaultCharset());
    }

    public static StreamTaskListener fromStderr() {
        return new StreamTaskListener(System.err, Charset.defaultCharset());
    }

    @Override
    public PrintStream getLogger() {
        return out;
    }

    @Override
    public Charset getCharset() {
        return charset != null ? charset : Charset.defaultCharset();
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.writeObject(new RemoteOutputStream(new CloseProofOutputStream(this.out)));
        out.writeObject(charset == null ? null : charset.name());
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE, null, new Throwable("serializing here with AUTO_FLUSH=" + AUTO_FLUSH));
        }
    }

    private static final String KEY_AUTO_FLUSH = StreamTaskListener.class.getName() + ".AUTO_FLUSH";

    static {
        SystemProperties.allowOnAgent(KEY_AUTO_FLUSH);
    }
    /**
     * Restores eager remote flushing behavior.
     * By default, remote tasks are expected to call {@link PrintStream#flush} before exiting.
     * This flag will ensure that no output is lost from tasks which neglect to do so,
     * at the expense of heavier Remoting traffic and reduced performance.
     */

    private static /* not final */ boolean AUTO_FLUSH = SystemProperties.getBoolean(KEY_AUTO_FLUSH);

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        OutputStream os = (OutputStream) in.readObject();
        String name = (String) in.readObject();
        out = new PrintStream(os, AUTO_FLUSH, name != null ? name : Charset.defaultCharset().name());
        charset = name == null ? null : Charset.forName(name);
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE, null, new Throwable("deserializing here with AUTO_FLUSH=" + AUTO_FLUSH));
        }
    }

    @Override
    public void close() throws IOException {
        out.close();
    }

    /**
     * Closes this listener and swallows any exceptions, if raised.
     *
     * @since 1.349
     */
    public void closeQuietly() {
        try {
            close();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to close", e);
        }
    }

    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = Logger.getLogger(StreamTaskListener.class.getName());
}
