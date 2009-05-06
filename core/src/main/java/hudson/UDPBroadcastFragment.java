package hudson;

import hudson.model.Hudson;

import java.net.SocketAddress;

/**
 * Extension point that contributes an XML fragment to the UDP broadcast.
 *
 * <p>
 * Put {@link Extension} on your implementation class to have it auto-discovered.
 * 
 * @author Kohsuke Kawaguchi
 * @since 1.304
 * @see UDPBroadcastThread
 */
public abstract class UDPBroadcastFragment implements ExtensionPoint {
    /**
     * Called to build up a response XML.
     *
     * @param buf
     *      This is the buffer to write XML to. The implementation of this method
     *      should write a complete fragment. Because of the packet length restriction
     *      in UDP (somewhere around 1500 bytes), you cannot send a large amount of information.
     * @param sender
     *      The socket address that sent the discovery packet out.
     */
    public abstract void buildFragment(StringBuilder buf, SocketAddress sender);

    /**
     * Returns all the registered {@link UDPBroadcastFragment}s.
     */
    public static ExtensionList<UDPBroadcastFragment> all() {
        return Hudson.getInstance().getExtensionList(UDPBroadcastFragment.class);
    }
}
