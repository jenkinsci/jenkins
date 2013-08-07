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

import hudson.console.ConsoleNote;
import hudson.model.BuildListener;
import hudson.model.Cause;
import hudson.model.Result;
import hudson.model.StreamBuildListener;
import hudson.remoting.Callable;
import hudson.remoting.Channel;
import hudson.util.AbstractTaskListener;
import jenkins.util.MarkFindingOutputStream;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.commons.io.output.DeferredFileOutputStream;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.util.List;

/**
 * Delegating {@link BuildListener} that can have "side" {@link OutputStream}
 * that gets log outputs. The side stream can be changed at runtime.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.133
 */
final class SplittableBuildListener extends AbstractTaskListener implements BuildListener, Serializable {
    /**
     * The actual {@link BuildListener} where the output goes.
     */
    private final BuildListener core;

    /**
     * Used to accumulate data when no one is claiming the {@link #side},
     * so that the next one who set the {@link #side} can claim all the data.
     * 
     * {@link DeferredFileOutputStream} is used so that even if we get out of sync with Maven
     * and end up accumulating a lot of data, we still won't kill the JVM.
     */
    private DeferredFileOutputStream unclaimed = newLog();

    private volatile OutputStream side = unclaimed;

    /**
     * Constant {@link PrintStream} connected to both {@link #core} and {@link #side}.
     * This is so that we can change the side stream without the client noticing it.
     */
    private final PrintStream logger;
    
    private int markCount = 0;
    private final Object markCountLock = new Object();

    public SplittableBuildListener(BuildListener core) {
        this.core = core;
        final OutputStream base = core.getLogger();
        
        final OutputStream tee = new OutputStream() {
            public void write(int b) throws IOException {
                base.write(b);
                synchronized (lock()) {
                    side.write(b);
                }
            }

            public void write(byte b[], int off, int len) throws IOException {
                base.write(b, off, len);
                synchronized (lock()) {
                    side.write(b, off, len);
                }
            }

            public void flush() throws IOException {
                base.flush();
                synchronized (lock()) {
                    side.flush();
                }
            }

            public void close() throws IOException {
                base.close();
                synchronized (lock()) {
                    side.close();
                }
            }
        };
        
        logger = new PrintStream(new MarkFindingOutputStream(tee) {
            @Override
            protected void onMarkFound() {
                synchronized (markCountLock) {
                    markCount++;
                    markCountLock.notifyAll();
                }
            }
        });
    }

    /**
     * Mark/sync operation.
     * 
     * <p>
     * Where {@link SplittableBuildListener} is used, Jenkins is normally in control of the process
     * that's generating the log (Maven, for example), and it's also the receiver.
     *
     * But because the stdout of the sender travels through a different route than the remoting channel,
     * a synchronization needs to happen when we switch the side OutputStream.
     *
     * <p>
     * This method does that synchronization by sending a marker string to the output, then
     * block until we receive it. Provided that we are in control of the process generating the output,
     * we will not receive any extra bytes after the marker string.
     */
    public void synchronizeOnMark(Channel ch) throws IOException, InterruptedException {
        synchronized (markCountLock) {
            int start = markCount;
            
            // have the remote send us a mark
            ch.call(new SendMark());
            
            // and block until we receive a mark
            while (markCount==start)
                markCountLock.wait();
        }
    }
    
    public void setSideOutputStream(OutputStream os) throws IOException {
        synchronized (lock()) {
            if(os==null) {
                os = unclaimed;
            } else {
                unclaimed.close();
                unclaimed.writeTo(os);
                File f = unclaimed.getFile();
                if (f!=null)    f.delete();

                unclaimed = newLog();
            }
            this.side = os;
        }
    }

    private DeferredFileOutputStream newLog() {
        return new DeferredFileOutputStream(10*1024,"maven-build","log",null);
    }
    
    /**
     * We need to be able to atomically write the buffered bits and then create a fresh {@link ByteArrayOutputStream},
     * when another thread (pipe I/O thread) is calling log.write().
     * 
     * This locks controls the access and the write operation to {@link #side} (and since that can point to the same
     * object as {@link #unclaimed}, that access needs to be in the same lock, too.)
     */
    private Object lock() {
        return this;
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
        return new PrintWriter(logger, true);
    }

    public PrintWriter error(String format, Object... args) {
        core.error(format,args);
        return new PrintWriter(logger, true);
    }

    public PrintWriter fatalError(String msg) {
        core.fatalError(msg);
        return new PrintWriter(logger, true);
    }

    public PrintWriter fatalError(String format, Object... args) {
        core.fatalError(format,args);
        return new PrintWriter(logger, true);
    }

    public void annotate(ConsoleNote ann) throws IOException {
        core.annotate(ann);
    }

    private Object writeReplace() throws IOException {
        return new StreamBuildListener(logger);
    }

    private static final long serialVersionUID = 1L;

    private static final byte[] MARK = toUTF8(MarkFindingOutputStream.MARK);

    private static byte[] toUTF8(String s) {
        try {
            return s.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }
    }

    private static class SendMark implements Callable<Void, IOException> {
        public Void call() throws IOException {
            // write a mark
            System.out.write(MARK);
            System.out.flush();
            return null;
        }
    }
}
