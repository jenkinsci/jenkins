/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc.
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
package org.jvnet.hudson.test;

/**
 * Lock mechanism to let multiple threads execute phases sequentially.
 *
 * @author Kohsuke Kawaguchi
 */
public class SequenceLock {
    /**
     * Currently executing phase N.
     */
    private int n;

    /**
     * This thread is executing the phase
     */
    private Thread t;

    private boolean aborted;

    /**
     * Blocks until all the previous phases are completed, and returns when the specified phase <i>i</i> is started.
     * If the calling thread was executing an earlier phase, that phase is marked as completed.
     *
     * @throws IllegalStateException
     *      if the sequential lock protocol is aborted, or the thread that owns the current phase has quit.
     */
    public synchronized void phase(int i) throws InterruptedException {
        done(); // mark the previous phase done
        while (i!=n) {
            if (aborted)
                throw new IllegalStateException("SequenceLock aborted");
            if (t!=null && !t.isAlive())
                throw new IllegalStateException("Owner thread of the current phase has quit"+t);
            if (i<n)
                throw new IllegalStateException("Phase "+i+" is already completed");
            wait();
        }

        t = Thread.currentThread();
    }

    /**
     * Marks the current phase completed that the calling thread was executing.
     *
     * <p>
     * This is only necessary when the thread exits the last phase, as {@link #phase(int)} call implies the
     * {@link #done()} call.
     */
    public synchronized void done() {
        if (t==Thread.currentThread()) {
            // phase N done
            n++;
            t = null;
            notifyAll();
        }
    }

    /**
     * Tell all the threads that this sequencing was aborted.
     * Everyone waiting for future phases will receive an error.
     *
     * <p>
     * Calling this method from the finally block prevents a dead lock if one of the participating thread
     * aborts with an exception, as without the explicit abort operation, other threads will block forever
     * for a phase that'll never come. 
     */
    public synchronized void abort() {
        aborted = true;
        notifyAll();
    }
}
