package hudson.remoting;

/**
 * @author Kohsuke Kawaguchi
 */
public class ChannelTest extends RmiTestBase {
    public void testCapability() {
        // for now this is disabled
        assertTrue(!channel.remoteCapability.supportsMultiClassLoaderRPC());
    }
}
