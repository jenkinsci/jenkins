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

/**
 * Concurrency primitive.
 *
 * <p>
 * A {@link OneShotEvent} is like a pandora's box.
 * It starts with the closed (non-signaled) state.
 * Multiple threads can wait for the event to become the signaled state.
 *
 * <p>
 * Once the event becomes signaled, or the pandora's box is opened,
 * every thread gets through freely, and there's no way to turn it back off. 
 *
 * @author Kohsuke Kawaguchi
 */
public final class OneShotEvent {
    private boolean signaled;
    private final Object lock;

    public OneShotEvent() {
        this.lock = this;
    }

    public OneShotEvent(Object lock) {
        this.lock = lock;
    }

    /**
     * Non-blocking method that signals this event.
     */
    public void signal() {
        synchronized (lock) {
            if(signaled)        return;
            this.signaled = true;
            lock.notifyAll();
        }
    }

    /**
     * Blocks until the event becomes the signaled state.
     *
     * <p>
     * This method blocks infinitely until a value is offered.
     */
    public void block() throws InterruptedException {
        synchronized (lock) {
            while(!signaled)
                lock.wait();
        }
    }

    /**
     * Blocks until the event becomes the signaled state.
     *
     * <p>
     * If the specified amount of time elapses,
     * this method returns null even if the value isn't offered.
     */
    public void block(long timeout) throws InterruptedException {
        synchronized (lock) {
            if(!signaled)
                lock.wait(timeout);
        }
    }

    /**
     * Returns true if a value is offered.
     */
    public boolean isSignaled() {
        synchronized (lock) {
            return signaled;
        }
    }
}
