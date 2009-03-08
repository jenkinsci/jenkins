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

import hudson.CloseProofOutputStream;
import hudson.remoting.RemoteOutputStream;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.List;

/**
 * {@link BuildListener} that writes to an {@link OutputStream}.
 *
 * This class is remotable.
 * 
 * @author Kohsuke Kawaguchi
 */
public class StreamBuildListener implements BuildListener, Serializable {
    private PrintWriter w;

    private PrintStream ps;

    public StreamBuildListener(OutputStream w) {
        this(new PrintStream(w));
    }

    public StreamBuildListener(PrintStream w) {
        this(w,null);
    }

    public StreamBuildListener(PrintStream w, Charset charset) {
        this.ps = w;
        // unless we auto-flash, PrintStream will use BufferedOutputStream internally,
        // and break ordering
        this.w = new PrintWriter(new BufferedWriter(
                charset==null ? new OutputStreamWriter(w) : new OutputStreamWriter(w,charset)), true);
    }

    public void started(List<Cause> causes) {
        if (causes==null || causes.isEmpty())
            w.println("Started");
        else for (Cause cause : causes) {
            w.println(cause.getShortDescription());
        }
    }

    public PrintStream getLogger() {
        return ps;
    }

    public PrintWriter error(String msg) {
        w.println("ERROR: "+msg);
        return w;
    }

    public PrintWriter error(String format, Object... args) {
        return error(String.format(format,args));
    }

    public PrintWriter fatalError(String msg) {
        w.println("FATAL: "+msg);
        return w;
    }

    public PrintWriter fatalError(String format, Object... args) {
        return fatalError(String.format(format,args));
    }

    public void finished(Result result) {
        w.println("Finished: "+result);
    }


    private void writeObject(ObjectOutputStream out) throws IOException {
        out.writeObject(new RemoteOutputStream(new CloseProofOutputStream(ps)));
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        ps = new PrintStream((OutputStream)in.readObject(),true);
        w = new PrintWriter(ps,true);
    }

    private static final long serialVersionUID = 1L;
}
