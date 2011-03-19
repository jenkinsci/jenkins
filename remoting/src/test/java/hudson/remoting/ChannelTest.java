package hudson.remoting;

import org.jvnet.hudson.test.Bug;

import java.io.IOException;
import java.io.ObjectInputStream;

/**
 * @author Kohsuke Kawaguchi
 */
public class ChannelTest extends RmiTestBase {
    public void testCapability() {
        assertTrue(channel.remoteCapability.supportsMultiClassLoaderRPC());
    }

    @Bug(9050)
    public void testFailureInDeserialization() throws Exception {
        try {
            channel.call(new CallableImpl());
            fail();
        } catch (IOException e) {
            e.printStackTrace();
            assertEquals("foobar",e.getCause().getCause().getMessage());
            assertTrue(e.getCause().getCause() instanceof ClassCastException);
        }
    }

    private static class CallableImpl implements Callable<Object,IOException> {
        public Object call() throws IOException {
            return null;
        }

        private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
            throw new ClassCastException("foobar");
        }
    }
}
