/*
 * The MIT License
 *
 * Copyright (c) 2010, InfraDNA, Inc.
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
package hudson.model.queue;

/**
 * A concurrency primitive that waits for N number of threads to synchronize.
 * If any of the threads are interrupted while waiting for the completion of the condition,
 * then all the involved threads get interrupted.
 *
 * @author Kohsuke Kawaguchi
 */
class Latch {
    private final int n;
    private int i=0;
    private boolean interrupted;

    public Latch(int n) {
        this.n = n;
    }

    public synchronized void abort() {
        interrupted = true;
        notifyAll();
    }


    public synchronized void synchronize() throws InterruptedException {
        check(n);

        boolean success=false;
        try {
            onCriteriaMet();
            success=true;
        } finally {
            if (!success)
                abort();
        }

        check(n*2);
    }

    private void check(int threshold) throws InterruptedException {
        i++;
        if (i==threshold) {
            notifyAll();
        } else {
            while (i<threshold && !interrupted) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    interrupted = true;
                    notifyAll();
                    throw e;
                }
            }
        }

        // all of us either leave normally or get interrupted
        if (interrupted)
            throw new InterruptedException();
    }

    protected void onCriteriaMet() throws InterruptedException {}
}
