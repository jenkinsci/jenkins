package hudson.remoting;

import org.jvnet.hudson.test.Bug;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;

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

    /**
     * Objects exported during the request arg capturing is subject to caller auto-deallocation.
     */
    @Bug(10424)
    public void testExportCallerDeallocation() throws Exception {
        for (int i=0; i<100; i++) {
            final GreeterImpl g = new GreeterImpl();
            channel.call(new GreetingTask(g));
            assertEquals(g.name,"Kohsuke");
            assertTrue("in this scenario, auto-unexport by the caller should kick in.",
                    !channel.exportedObjects.isExported(g));
        }
    }

    /**
     * Objects exported outside the request context should be deallocated by the callee.
     */
    @Bug(10424)
    public void testExportCalleeDeallocation() throws Exception {
        for (int j=0; j<10; j++) {
            final GreeterImpl g = new GreeterImpl();
            channel.call(new GreetingTask(channel.export(Greeter.class,g)));
            assertEquals(g.name,"Kohsuke");
            boolean isExported = channel.exportedObjects.isExported(g);
            if (!isExported) {
                // it is unlikely but possible that GC happens on remote node
                // and 'g' gets unexported before we get to execute the above line
                // if so, try again. if we kept failing after a number of retries,
                // then it's highly suspicious that the caller is doing the deallocation,
                // which is a bug.
                continue;
            }

            // now we verify that 'g' gets eventually unexported by remote.
            // to do so, we keep calling System.gc().
            for (int i=0; i<30 && channel.exportedObjects.isExported(g); i++) {
                channel.call(new GCTask());
                Thread.sleep(100);
            }

            assertTrue(
                    "Object isn't getting unexported by remote",
                    !channel.exportedObjects.isExported(g));

            return;
        }

        fail("in this scenario, remote will unexport this");
    }

    public interface Greeter {
        void greet(String name);
    }

    private static class GreeterImpl implements Greeter, Serializable {
        String name;
        public void greet(String name) {
            this.name = name;
        }

        private Object writeReplace() {
            return Channel.current().export(Greeter.class,this);
        }
    }

    private static class GreetingTask implements Callable<Object, IOException> {
        private final Greeter g;

        public GreetingTask(Greeter g) {
            this.g = g;
        }

        public Object call() throws IOException {
            g.greet("Kohsuke");
            return null;
        }
    }

    private static class GCTask implements Callable<Object, IOException> {
        public Object call() throws IOException {
            System.gc();
            return null;
        }
    }
}
