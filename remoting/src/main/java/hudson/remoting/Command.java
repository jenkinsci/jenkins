package hudson.remoting;

import java.io.Serializable;

/**
 * One-way command to be sent over to the remote system and executed there.
 * This is layer 0, the lower most layer.
 *
 * <p>
 * At this level, remoting of class files are not provided, so both {@link Channel}s
 * need to have the definition of {@link Command}-implementation.
 * 
 * @author Kohsuke Kawaguchi
 */
abstract class Command implements Serializable {
    /**
     * This exception captures the stack trace of where the Command object is created.
     * This is useful for diagnosing the error when command fails to execute on the remote peer. 
     */
    public final Exception createdAt;


    protected Command() {
        this.createdAt = new Source();
    }

    /**
     * Called on a remote system to perform this command.
     *
     * @param channel
     *      The {@link Channel} of the remote system.
     */
    protected abstract void execute(Channel channel);

    private static final long serialVersionUID = 1L;

    private final class Source extends Exception {
        public Source() {
        }

        public String toString() {
            return "Command "+Command.this.toString()+" created at";
        }

        private static final long serialVersionUID = 1L;
    }
}
