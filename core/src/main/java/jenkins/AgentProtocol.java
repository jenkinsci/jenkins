package jenkins;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.TcpSlaveAgentListener;

import java.io.IOException;
import java.net.Socket;

/**
 * Pluggable Jenkins TCP agent protocol handler called from {@link TcpSlaveAgentListener}.
 *
 * <p>
 * To register your extension, put {@link Extension} annotation on your subtype.
 * Implementations of this extension point is singleton, and its {@link #handle(Socket)} method
 * gets invoked concurrently whenever a new connection comes in.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.467
 * @see TcpSlaveAgentListener
 */
public abstract class AgentProtocol implements ExtensionPoint {
    /**
     * Allow experimental {@link AgentProtocol} implementations to declare being opt-in.
     * @return {@code true} if the protocol requires explicit opt-in.
     * @since FIXME
     */
    public boolean isOptIn() {
        return false;
    }
    /**
     * Allow essential {@link AgentProtocol} implementations (basically {@link TcpSlaveAgentListener.PingAgentProtocol})
     * to be always enabled.
     *
     * @return {@code true} if the protocol can never be disbaled.
     * @since FIXME
     */
    public boolean isRequired() {
        return false;
    }
    /**
     * Protocol name.
     *
     * This is a short string that consists of printable ASCII chars. Sent by the client to select the protocol.
     *
     * @return
     *      null to be disabled. This is useful for avoiding getting used
     *      until the protocol is properly configured.
     */
    public abstract String getName();

    /**
     * Called by the connection handling thread to execute the protocol.
     */
    public abstract void handle(Socket socket) throws IOException, InterruptedException;

    /**
     * Returns all the registered {@link AgentProtocol}s.
     */
    public static ExtensionList<AgentProtocol> all() {
        return ExtensionList.lookup(AgentProtocol.class);
    }

    public static AgentProtocol of(String protocolName) {
        for (AgentProtocol p : all()) {
            String n = p.getName();
            if (n!=null && n.equals(protocolName))
                return p;
        }
        return null;
    }
}
