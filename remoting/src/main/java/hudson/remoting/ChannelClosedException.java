package hudson.remoting;

import java.io.IOException;

/**
 * Indicates that the channel is already closed.
 *
 * @author Kohsuke Kawaguchi
 */
public class ChannelClosedException extends IOException {
    /**
     * @deprecated
     *      Use {@link #ChannelClosedException(Throwable)}.
     */
    public ChannelClosedException() {
        super("channel is already closed");
    }

    public ChannelClosedException(Throwable cause) {
        super("channel is already closed");
        initCause(cause);
    }
}
