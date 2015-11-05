package hudson.cli;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.remoting.Channel;
import hudson.security.SecurityRealm;
import jenkins.model.Jenkins;

/**
 * Perform {@link SecurityRealm} independent authentication.
 *
 * <p>
 * Implementing this extension point requires changes in the CLI module, as during authentication
 * neither side trusts each other enough to start code-transfer. But it does allow us to
 * use different implementations of the same protocol.
 *
 * <h2>Current Implementations</h2>
 * <p>
 * Starting 1.419, CLI supports SSH public key based client/server mutual authentication.
 * The protocol name of this is "ssh".
 *
 * @author Kohsuke Kawaguchi
 * @since 1.419
 */
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
