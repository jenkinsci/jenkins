package hudson.remoting;

/**
 * @author Kohsuke Kawaguchi
 */
public class ChannelTest extends RmiTestBase {
    public void testCapability() {
        assertTrue(channel.remoteCapability.supportsMultiClassLoaderRPC());
    }
}
