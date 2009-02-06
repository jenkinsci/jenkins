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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.Writer;

import org.kohsuke.stapler.framework.io.WriterOutputStream;

/**
 * {@link TaskListener} that generates output into a single stream.
 *
 * <p>
 * This object is remotable.
 * 
 * @author Kohsuke Kawaguchi
 */
public final class StreamTaskListener implements TaskListener, Serializable {
    private PrintStream out;

    public StreamTaskListener(PrintStream out) {
        this.out = out;
    }

    public StreamTaskListener(OutputStream out) {
        this(new PrintStream(out));
    }

    public StreamTaskListener(File out) throws FileNotFoundException {
        // don't do buffering so that what's written to the listener
        // gets reflected to the file immediately, which can then be
        // served to the browser immediately
        this(new FileOutputStream(out));
    }

    public StreamTaskListener(Writer w) {
        this(new WriterOutputStream(w));
    }

    /**
     * Creates {@link StreamTaskListener} that swallows the result.
     */
    public StreamTaskListener() {
        this(new NullStream());
    }

    public PrintStream getLogger() {
        return out;
    }

    public PrintWriter error(String msg) {
        out.println(msg);
        return new PrintWriter(new OutputStreamWriter(out),true);
    }

    public PrintWriter error(String format, Object... args) {
        return error(String.format(format,args));
    }

    public PrintWriter fatalError(String msg) {
        return error(msg);
    }

    public PrintWriter fatalError(String format, Object... args) {
        return fatalError(String.format(format,args));
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.writeObject(new RemoteOutputStream(new CloseProofOutputStream(this.out)));
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        out = new PrintStream((OutputStream)in.readObject(),true);
    }

    public void close() {
        out.close();
    }

    private static final long serialVersionUID = 1L;
}
