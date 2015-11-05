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

import hudson.util.StreamTaskListener;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.List;

/**
 * {@link BuildListener} that writes to an {@link OutputStream}.
 *
 * This class is remotable.
 * 
 * @author Kohsuke Kawaguchi
 */
public class StreamBuildListener extends StreamTaskListener implements BuildListener {
    public StreamBuildListener(OutputStream out, Charset charset) {
        super(out, charset);
    }

    public StreamBuildListener(File out, Charset charset) throws IOException {
        super(out, charset);
    }

    public StreamBuildListener(OutputStream w) {
        super(w);
    }

    /**
     * @deprecated as of 1.349
     *      The caller should use {@link #StreamBuildListener(OutputStream, Charset)} to pass in
     *      the charset and output stream separately, so that this class can handle encoding correctly.
     */
    @Deprecated
    public StreamBuildListener(PrintStream w) {
        super(w);
    }

    public StreamBuildListener(PrintStream w, Charset charset) {
        super(w,charset);
    }

    public void started(List<Cause> causes) {
        PrintStream l = getLogger();
        if (causes==null || causes.isEmpty())
            l.println("Started");
        else for (Cause cause : causes) {
            // TODO elide duplicates as per CauseAction.getCauseCounts (used in summary.jelly)
            cause.print(this);
        }
    }

    public void finished(Result result) {
        getLogger().println("Finished: "+result);
    }

    private static final long serialVersionUID = 1L;
}
