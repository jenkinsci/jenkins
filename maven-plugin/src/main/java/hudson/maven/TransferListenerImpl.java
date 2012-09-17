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

import hudson.model.TaskListener;
import org.apache.maven.wagon.events.TransferEvent;
import org.apache.maven.wagon.events.TransferListener;

import java.io.PrintStream;

/**
 * {@link TransferListener} implementation.
 *
 * <p>
 * This implementation puts the transfer progress indication in a distinctively formatted line,
 * so that on HTML we can render the progress as a progress bar.
 *
 * @author Kohsuke Kawaguchi
 */
final class TransferListenerImpl implements TransferListener {
    /**
     * Receives the formatter messages.
     */
    private final PrintStream out;

    private long transferedSize;


    public TransferListenerImpl(TaskListener listener) {
        this.out = listener.getLogger();
    }

    public void transferInitiated( TransferEvent e )
    {
        String url = e.getWagon().getRepository().getUrl();

        if(e.getRequestType()==TransferEvent.REQUEST_PUT) {
            out.println("Uploading to "+url);
        } else {
            out.println("Downloading "+url);
        }
    }

    public void transferStarted(TransferEvent e) {
        transferedSize = 0;
        long total = e.getResource().getContentLength();
        out.println(HEADER+" start "+total);
    }

    public void transferProgress(TransferEvent e, byte[] buffer, int length) {
        transferedSize += length;
        out.println(HEADER + " progress " + transferedSize);
    }

    public void transferCompleted(TransferEvent e) {
        out.println(HEADER + " completed");
    }

    public void transferError(TransferEvent e) {
        out.println(HEADER + " error");
        e.getException().printStackTrace(out);
    }

    public void debug(String message) {
    }

    /**
     * Lines printed by this class will have this header. 
     */
    public static final String HEADER = "[:TRANSFER:]";
}
