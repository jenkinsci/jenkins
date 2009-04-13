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
package hudson.remoting;

import java.io.IOException;

/**
 * Virtualized {@link Channel} that allows different implementations.
 * 
 * @author Kohsuke Kawaguchi
 */
public interface VirtualChannel {
    /**
     * Makes a remote procedure call.
     *
     * <p>
     * Sends {@link Callable} to the remote system, executes it, and returns its result.
     *
     * @throws InterruptedException
     *      If the current thread is interrupted while waiting for the completion.
     * @throws IOException
     *      If there's any error in the communication between {@link Channel}s.
     */
    <V,T extends Throwable>
    V call(Callable<V,T> callable) throws IOException, T, InterruptedException;

    /**
     * Makes an asynchronous remote procedure call.
     *
     * <p>
     * Similar to {@link #call(Callable)} but returns immediately.
     * The result of the {@link Callable} can be obtained through the {@link Future} object.
     *
     * @return
     *      The {@link Future} object that can be used to wait for the completion.
     * @throws IOException
     *      If there's an error during the communication.
     */
    <V,T extends Throwable>
    Future<V> callAsync(final Callable<V,T> callable) throws IOException;

    /**
     * Performs an orderly shut down of this channel (and the remote peer.)
     *
     * @throws IOException
     *      if the orderly shut-down failed.
     */
    void close() throws IOException;

    /**
     * Waits for this {@link Channel} to be closed down.
     *
     * The close-down of a {@link Channel} might be initiated locally or remotely.
     *
     * @throws InterruptedException
     *      If the current thread is interrupted while waiting for the completion.
     * @since 1.300
     */
    public void join() throws InterruptedException;

    /**
     * Waits for this {@link Channel} to be closed down, but only up the given milliseconds.
     *
     * @throws InterruptedException
     *      If the current thread is interrupted while waiting for the completion.
     * @since 1.300
     */
    public void join(long timeout) throws InterruptedException;

    /**
     * Exports an object for remoting to the other {@link Channel}
     * by creating a remotable proxy.
     *
     * <p>
     * All the parameters and return values must be serializable.
     *
     * @param type
     *      Interface to be remoted.
     * @return
     *      the proxy object that implements <tt>T</tt>. This object can be transfered
     *      to the other {@link Channel}, and calling methods on it from the remote side
     *      will invoke the same method on the given local <tt>instance</tt> object.
     */
    <T> T export( Class<T> type, T instance);
}
