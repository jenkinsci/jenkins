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
package hudson.maven;

import hudson.model.BuildListener;
import hudson.model.Cause;
import hudson.model.Result;
import hudson.model.StreamBuildListener;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.List;

/**
 * Delegating {@link BuildListener} that can have "side" {@link OutputStream}
 * that gets log outputs. The side stream can be changed at runtime.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.133
 */
final class SplittableBuildListener implements BuildListener, Serializable {
    /**
     * The actual {@link BuildListener} where the output goes.
     */
    private final BuildListener core;

    /**
     * Used to accumulate data when no one is claiming the {@link #side},
     * so that the next one who set the {@link #side} can claim all the data.
     */
    private ByteArrayOutputStream unclaimed = new ByteArrayOutputStream();

    private OutputStream side = unclaimed;

    /**
     * Constant {@link PrintStream} connected to both {@link #core} and {@link #side}.
     * This is so that we can change the side stream without the client noticing it.
     */
    private final PrintStream logger;

    public SplittableBuildListener(BuildListener core) {
        this.core = core;
        final OutputStream base = core.getLogger();
        logger = new PrintStream(new OutputStream() {
            public void write(int b) throws IOException {
                base.write(b);
                side.write(b);
            }

            public void write(byte b[], int off, int len) throws IOException {
                base.write(b,off,len);
                side.write(b,off,len);
            }

            public void flush() throws IOException {
                base.flush();
                side.flush();
            }

            public void close() throws IOException {
                base.close();
                side.close();
            }
        });
    }
    
    public void setSideOutputStream(OutputStream os) throws IOException {
        if(os==null) {
            os = unclaimed;
        } else {
            os.write(unclaimed.toByteArray());
            unclaimed = new ByteArrayOutputStream();
        }
        this.side = os;
    }

    public void started(List<Cause> causes) {
        core.started(causes);
    }

    public void finished(Result result) {
        core.finished(result);
    }

    public PrintStream getLogger() {
        return logger;
    }

    public PrintWriter error(String msg) {
        core.error(msg);
        return new PrintWriter(logger);
    }

    public PrintWriter error(String format, Object... args) {
        core.error(format,args);
        return new PrintWriter(logger);
    }

    public PrintWriter fatalError(String msg) {
        core.fatalError(msg);
        return new PrintWriter(logger);
    }

    public PrintWriter fatalError(String format, Object... args) {
        core.fatalError(format,args);
        return new PrintWriter(logger);
    }

    private Object writeReplace() {
        return new StreamBuildListener(logger);
    }

    private static final long serialVersionUID = 1L;
}
