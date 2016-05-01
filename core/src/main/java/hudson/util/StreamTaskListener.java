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
import hudson.console.ConsoleNote;
import hudson.console.HudsonExceptionNote;
import hudson.model.TaskListener;
import hudson.remoting.RemoteOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.kohsuke.stapler.framework.io.WriterOutputStream;

/**
 * {@link TaskListener} that generates output into a single stream.
 *
 * <p>
 * This object is remotable.
 * 
 * @author Kohsuke Kawaguchi
 */
public class StreamTaskListener extends AbstractTaskListener implements Serializable, Closeable {
    private PrintStream out;

    /**
     * Encoding used for the log content.
     * Used before 2.1 as Jenkins was storing build log with build node's encoding.
     * Starting with 2.1 charset conversion is done on the fly so logs are stored on master with local encoding.
     */
    private Charset charset;

    public StreamTaskListener(PrintStream out) {
        this((OutputStream)out);
    }

    public StreamTaskListener(OutputStream out) {
        try {
            this.charset = StandardCharsets.UTF_8;
            this.out = new PrintStream(out, false, this.charset.name());
        } catch (UnsupportedEncodingException e) {
            // it's not very pretty to do this, but otherwise we'd have to touch too many call sites.
            throw new Error(e);
        }
    }

    /** @deprecated charset is forced to UTF-8 */
    public StreamTaskListener(OutputStream out, Charset charset) {
        this(out);
    }

    public StreamTaskListener(File out) throws IOException {
        // don't do buffering so that what's written to the listener
        // gets reflected to the file immediately, which can then be
        // served to the browser immediately
        this(new FileOutputStream(out));
    }

    /** @deprecated charset is forced to UTF-8 */
    public StreamTaskListener(File out, Charset charset) throws IOException {
        this(out);
    }

    /**
     * Constructs a {@link StreamTaskListener} that sends the output to a specified file.
     *
     * @param out     the file.
     * @param append  if {@code true}, then output will be written to the end of the file rather than the beginning.
     * @throws IOException if the file could not be opened.
     * @since 2.1
     */
    public StreamTaskListener(File out, boolean append) throws IOException {
        // don't do buffering so that what's written to the listener
        // gets reflected to the file immediately, which can then be
        // served to the browser immediately
        this(new FileOutputStream(out, append));
    }

     /** @deprecated charset is forced to UTF-8 */
    public StreamTaskListener(File out, boolean append, Charset charset) throws IOException {
        this(out, append);
    }

    public StreamTaskListener(Writer w) throws IOException {
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
        return new StreamTaskListener(System.out);
    }

    public static StreamTaskListener fromStderr() {
        return new StreamTaskListener(System.err);
    }

    public PrintStream getLogger() {
        return out;
    }

    private PrintWriter _error(String prefix, String msg) {
        out.print(prefix);
        out.println(msg);

        // the idiom in Hudson is to use the returned writer for writing stack trace,
        // so put the marker here to indicate an exception. if the stack trace isn't actually written,
        // HudsonExceptionNote.annotate recovers gracefully.
        try {
            annotate(new HudsonExceptionNote());
        } catch (IOException e) {
            // for signature compatibility, we have to swallow this error
        }
        return new PrintWriter(
            charset!=null ? new OutputStreamWriter(out,charset) : new OutputStreamWriter(out),true);
    }

    public PrintWriter error(String msg) {
        return _error("ERROR: ",msg);
    }

    public PrintWriter error(String format, Object... args) {
        return error(String.format(format,args));
    }

    public PrintWriter fatalError(String msg) {
        return _error("FATAL: ",msg);
    }

    public PrintWriter fatalError(String format, Object... args) {
        return fatalError(String.format(format,args));
    }

    public void annotate(ConsoleNote ann) throws IOException {
        ann.encodeTo(out);
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
