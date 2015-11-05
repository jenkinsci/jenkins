package jenkins.slaves;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Slave;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.util.Properties;

/**
 * Receives incoming slaves connecting through {@link JnlpSlaveAgentProtocol2}.
 *
 * <p>
 * This is useful to establish the communication with other JVMs and use them
 * for different purposes outside {@link Slave}s.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.561
 */
public abstract class JnlpAgentReceiver implements ExtensionPoint {

    /**
     * Called after the client has connected.
     *
     * <p>
     * The implementation must do the following in the order:
     *
     * <ol>
     *     <li>Check if the implementation recognizes and claims the given name.
     *         If not, return false to let other {@link JnlpAgentReceiver} have a chance to
     *         take this connection.
     *
     *     <li>If you claim the name but the connection is refused, call
     *         {@link JnlpSlaveHandshake#error(String)} to refuse the client, and return true.
     *         The connection will be shut down and the client will report this error to the user.
     *
     *     <li>If you claim the name and the connection is OK, call
     *         {@link JnlpSlaveHandshake#success(Properties)} to accept the client.
     *
     *     <li>Proceed to build a channel with {@link JnlpSlaveHandshake#createChannelBuilder(String)}
     *         and return true
     *
     * @param name
     *      Name of the incoming JNLP agent. All {@link JnlpAgentReceiver} shares a single namespace
     *      of names. The implementation needs to be able to tell which name belongs to them.
     *
     * @param handshake
     *      Encapsulation of the interaction with the incoming JNLP agent.
     *
     * @return
     *      true if the name was claimed and the handshake was completed (either successfully or unsuccessfully)
     *      false if the name was not claimed. Other {@link JnlpAgentReceiver}s will be called to see if they
     *      take this connection.
     *
     * @throws Exception
     *      Any exception thrown from this method will fatally terminate the connection.
     */
    public abstract boolean handle(String name, JnlpSlaveHandshake handshake) throws IOException, InterruptedException;

    public static ExtensionList<JnlpAgentReceiver> all() {
        return ExtensionList.lookup(JnlpAgentReceiver.class);
    }
}
