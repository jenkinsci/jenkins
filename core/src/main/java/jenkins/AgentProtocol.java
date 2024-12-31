package jenkins;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.TcpAgentListener;
import java.io.IOException;
import java.net.Socket;

/**
 * Pluggable Jenkins TCP agent protocol handler called from {@link TcpAgentListener}.
 *
 * <p>
 * To register your extension, put {@link Extension} annotation on your subtype.
 * Implementations of this extension point is singleton, and its {@link #handle(Socket)} method
 * gets invoked concurrently whenever a new connection comes in.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.467
 * @see TcpAgentListener
 */
public abstract class AgentProtocol implements ExtensionPoint {
    /**
     * @deprecated no longer used
     */
    @Deprecated
    public boolean isOptIn() {
        return false;
    }

    /**
     * @deprecated no longer used
     */
    @Deprecated
    public boolean isRequired() {
        return false;
    }

    /**
     * @deprecated no longer used
     */
    @Deprecated
    public boolean isDeprecated() {
        return false;
    }

    /**
     * Protocol name.
     *
     * This is a short string that consists of printable ASCII chars. Sent by the client to select the protocol.
     *
     * @return
     *      null to be disabled
     */
    @CheckForNull
    public abstract String getName();

    /**
     * @deprecated no longer used
     */
    @Deprecated
    public String getDisplayName() {
        return getName();
    }

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

    @CheckForNull
    public static AgentProtocol of(String protocolName) {
        for (AgentProtocol p : all()) {
            String n = p.getName();
            if (n != null && n.equals(protocolName))
                return p;
        }
        return null;
    }
}
