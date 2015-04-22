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
import org.kohsuke.stapler.framework.io.WriterOutputStream;

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
import java.util.logging.Level;
import java.util.logging.Logger;

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
    private Charset charset;

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

    public StreamTaskListener(OutputStream out) {
        this(out,null);
    }

    public StreamTaskListener(OutputStream out, Charset charset) {
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

    public StreamTaskListener(File out) throws IOException {
        this(out,null);
    }

    public StreamTaskListener(File out, Charset charset) throws IOException {
        // don't do buffering so that what's written to the listener
        // gets reflected to the file immediately, which can then be
        // served to the browser immediately
        this(new FileOutputStream(out),charset);
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
        return new StreamTaskListener(System.out,Charset.defaultCharset());
    }

    public static StreamTaskListener fromStderr() {
        return new StreamTaskListener(System.err,Charset.defaultCharset());
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
