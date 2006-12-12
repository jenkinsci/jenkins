package hudson.remoting;

import java.io.Serializable;

/**
 * One-way command to be sent over to the remote system and executed.
 *
 * This is layer 0, the lower most layer.
 * 
 * @author Kohsuke Kawaguchi
 */
abstract class Command implements Serializable {
    /**
     * Called on a remote system to perform this command.
     *
     * @param channel
     *      The {@link Channel} of the remote system.
     */
    protected abstract void execute(Channel channel);

    private static final long serialVersionUID = 1L;
}
