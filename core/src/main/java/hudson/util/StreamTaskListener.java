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
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.kohsuke.stapler.framework.io.WriterOutputStream;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

// TODO: AbstractTaskListener is empty now, but there are dependencies on that e.g. Ruby Runtime - JENKINS-48116)
// The change needs API deprecation policy or external usages cleanup.

/**
 * {@link TaskListener} that generates output into a single stream.
 *
 * <p>
 * This object is remotable.
 * 
 * @author Kohsuke Kawaguchi
 */
public class StreamTaskListener extends AbstractTaskListener implements TaskListener, Closeable {
    @Nonnull
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
    public StreamTaskListener(@Nonnull PrintStream out) {
        this(out,null);
    }

    public StreamTaskListener(@Nonnull OutputStream out) {
        this(out,null);
    }

    public StreamTaskListener(@Nonnull OutputStream out, @CheckForNull Charset charset) {
        try {
            if (charset == null)
                this.out = (out instanceof PrintStream) ? (PrintStream)out : new PrintStream(out, false);
            else
                this.out = new PrintStream(out, false, charset.name());
            this.charset = charset;
        } catch (UnsupportedEncodingException e) {
            // it's not very pretty to do this, but otherwise we'd have to touch too many call sites.
            throw new Error(e);
        }
    }

    public StreamTaskListener(@Nonnull File out) throws IOException {
        this(out,null);
    }

    public StreamTaskListener(@Nonnull File out, @CheckForNull Charset charset) throws IOException {
        // don't do buffering so that what's written to the listener
        // gets reflected to the file immediately, which can then be
        // served to the browser immediately
        this(Files.newOutputStream(asPath(out)), charset);
    }

    private static Path asPath(@Nonnull File out) throws IOException {
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
    public StreamTaskListener(@Nonnull File out, boolean append, @CheckForNull Charset charset) throws IOException {
        // don't do buffering so that what's written to the listener
        // gets reflected to the file immediately, which can then be
        // served to the browser immediately
        this(Files.newOutputStream(
                asPath(out),
                StandardOpenOption.CREATE, append ? StandardOpenOption.APPEND: StandardOpenOption.TRUNCATE_EXISTING
                ),
                charset
        );
    }

    public StreamTaskListener(@Nonnull Writer w) throws IOException {
        this(new WriterOutputStream(w));
    }

    /**
     * @deprecated as of 1.349
     *      Use {@link #NULL}
     */
    @Deprecated
    public StreamTaskListener() throws IOException {
        this(new NullStream());
    }

    public static StreamTaskListener fromStdout() {
        return new StreamTaskListener(System.out,Charset.defaultCharset());
    }

    public static StreamTaskListener fromStderr() {
        return new StreamTaskListener(System.err,Charset.defaultCharset());
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
        out.writeObject(charset==null? null : charset.name());
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        out = new PrintStream((OutputStream)in.readObject(),true);
        String name = (String)in.readObject();
        charset = name==null ? null : Charset.forName(name);
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
            LOGGER.log(Level.WARNING,"Failed to close",e);
        }
    }

    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = Logger.getLogger(StreamTaskListener.class.getName());
}
