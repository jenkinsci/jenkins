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
