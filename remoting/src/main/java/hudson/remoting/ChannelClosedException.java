package hudson.remoting;

import java.io.IOException;

/**
 * Indicates that the channel is already closed.
 *
 * @author Kohsuke Kawaguchi
 */
public class ChannelClosedException extends IOException {
    public ChannelClosedException() {
        super("channel is already closed");
    }
}
