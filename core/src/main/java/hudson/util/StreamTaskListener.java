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
import hudson.Util;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import org.kohsuke.stapler.framework.io.WriterOutputStream;

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
    private PrintStream out;
    private @Nonnull Charset charset;

    /**
     * @deprecated as of 1.349
     *      The caller should use {@link #StreamTaskListener(OutputStream, Charset)} to pass in
     *      the charset and output stream separately, so that this class can handle encoding correctly,
     *      or use {@link #fromStdout()} or {@link #fromStderr()}.
     */
    @Deprecated
    public StreamTaskListener(PrintStream out) {
        this(out,null);
    }

    /**
     * @deprecated use {@link #StreamTaskListener(java.io.OutputStream, java.nio.charset.Charset)} instead.
     */
    @Deprecated
    public StreamTaskListener(OutputStream out) {
        this(out,null);
    }

    public StreamTaskListener(OutputStream out, Charset charset) {
        try {
            if (charset == null) {
                charset = Charset.defaultCharset();
                this.out = (out instanceof PrintStream) ? (PrintStream)out : new PrintStream(out, false, charset.name());
            } else {
                this.out = new PrintStream(out, false, charset.name());
            }
            this.charset = charset;
        } catch (UnsupportedEncodingException e) {
            // it's not very pretty to do this, but otherwise we'd have to touch too many call sites.
            throw new Error(e);
        }
    }

    /**
     * @deprecated use {@link #StreamTaskListener(java.io.File, java.nio.charset.Charset)} instead.
     */
    @Deprecated
    public StreamTaskListener(File out) throws IOException {
        this(out,null);
    }

    public StreamTaskListener(File out, Charset charset) throws IOException {
        // don't do buffering so that what's written to the listener
        // gets reflected to the file immediately, which can then be
        // served to the browser immediately
        this(Files.newOutputStream(asPath(out)), charset);
    }

    private static Path asPath(File out) throws IOException {
        return Util.fileToPath(out);
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
    public StreamTaskListener(File out, boolean append, Charset charset) throws IOException {
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

    public StreamTaskListener(Writer w) throws IOException {
        // It's not possible to retrieve the charset that the writer is using;
        // however, for all uses of this constructor, the writer is an instance
        // of StringWriter, so it's okay to assume UTF-8.
        this(new WriterOutputStream(w, StandardCharsets.UTF_8), StandardCharsets.UTF_8);
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
        return new StreamTaskListener(System.out);
    }

    public static StreamTaskListener fromStderr() {
        return new StreamTaskListener(System.err);
    }

    @Override
    public PrintStream getLogger() {
        return out;
    }

    @Override
    public @Nonnull Charset getCharset() { // getCharset() in TaskListener is annotated @Nonnull
        return charset;
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.writeObject(new RemoteOutputStream(new CloseProofOutputStream(this.out)));
        out.writeObject(charset.name());
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        out = new PrintStream((OutputStream)in.readObject(),true);
        String name = (String)in.readObject();
        charset = name==null ? Charset.defaultCharset() : Charset.forName(name);
    }

    @Override
    public void close() throws IOException {
        if (out != System.out && out != System.err)
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
