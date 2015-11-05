package hudson.slaves;

import hudson.model.Computer;
import hudson.model.Node;
import hudson.util.TimeUnit2;
import jenkins.model.Jenkins;

import javax.annotation.concurrent.GuardedBy;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Default convenience implementation of {@link RetentionStrategy} for slaves provisioned from {@link Cloud}.
 *
 * If a slave is idle for 10 mins, this retention strategy will remove the slave. This can be used as-is for
 * a {@link Node} provisioned by cloud to implement the auto-scaling semantics, it can be subtyped to tweak
 * the behavior, or it can be used as an example.
 * <p>TODO {@link CloudRetentionStrategy} seems to be a better implementation.
 * @author Kohsuke Kawaguchi
 * @since 1.510
 */
public class CloudSlaveRetentionStrategy<T extends Computer> extends RetentionStrategy<T> {

    @Override
    @GuardedBy("hudson.model.Queue.lock")
    public long check(T c) {
        if (!c.isConnecting() && c.isAcceptingTasks()) {
            if (isIdleForTooLong(c)) {
                try {
                    Node n = c.getNode();
                    if (n!=null)    // rare, but n==null if the node is deleted and being checked roughly at the same time
                        kill(n);
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Failed to remove "+c.getDisplayName(),e);
                }
            }
        }
        return checkCycle();
    }

    /**
     * Remove the node.
     *
     * <p>
     * To actually deallocate the resource tied to this {@link Node}, implement {@link Computer#onRemoved()}.
     */
    protected void kill(Node n) throws IOException {
        Jenkins.getInstance().removeNode(n);
    }

    /**
     * When do we check again next time?
     */
    protected long checkCycle() {
        return getIdleMaxTime()/10;
    }

    /**
     * Has this computer been idle for too long?
     */
    protected boolean isIdleForTooLong(T c) {
        return System.currentTimeMillis()-c.getIdleStartMilliseconds() > getIdleMaxTime();
    }

    /**
     * If the computer has been idle longer than this time, we'll kill the slave.
     */
    protected long getIdleMaxTime() {
        return TIMEOUT;
    }

    // for debugging, it's convenient to be able to reduce this time
    public static long TIMEOUT = Long.getLong(CloudSlaveRetentionStrategy.class.getName()+".timeout", TimeUnit2.MINUTES.toMillis(10));

    private static final Logger LOGGER = Logger.getLogger(CloudSlaveRetentionStrategy.class.getName());
}
