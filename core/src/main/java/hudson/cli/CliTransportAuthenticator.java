package hudson.cli;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.remoting.Channel;

/**
 * @deprecated No longer used.
 */
@Deprecated
public abstract class CliTransportAuthenticator implements ExtensionPoint {
    /**
     * Checks if this implementation supports the specified protocol.
     *
     * @param protocol
     *      Identifier. CLI.jar is hard-coded with the built-in knowledge about a specific protocol.
     * @return
     *      true if this implementation supports the specified protocol,
     *      in which case {@link #authenticate(String, Channel, Connection)} would be called next.
     */
    public abstract boolean supportsProtocol(String protocol);

    /**
     * Performs authentication.
     *
     * <p>
     * The authentication
     *
     * @param protocol
     *      Protocol identifier that {@link #supportsProtocol(String)} returned true.
     * @param channel
     *      Communication channel to the client.
     * @param con
     */
    public abstract void authenticate(String protocol, Channel channel, Connection con);

    public static ExtensionList<CliTransportAuthenticator> all() {
        return ExtensionList.lookup(CliTransportAuthenticator.class);
    }
}
