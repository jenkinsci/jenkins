package hudson;

import junit.framework.TestCase;

import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.Future;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import hudson.remoting.Channel;

/**
 * Test that uses a connected channel.
 *
 * @author Kohsuke Kawaguchi
 */
public class AbstractChannelTest extends TestCase {
    /**
     * Two channels that are connected to each other, but shares the same classloader.
     */
    protected Channel french;
    protected Channel british;
    private ExecutorService executors = Executors.newCachedThreadPool();

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        final PipedInputStream p1i = new PipedInputStream();
        final PipedInputStream p2i = new PipedInputStream();
        final PipedOutputStream p1o = new PipedOutputStream(p1i);
        final PipedOutputStream p2o = new PipedOutputStream(p2i);

        Future<Channel> f1 = executors.submit(new Callable<Channel>() {
            public Channel call() throws Exception {
                return new Channel("This side of the channel", executors, p1i, p2o);
            }
        });
        Future<Channel> f2 = executors.submit(new Callable<Channel>() {
            public Channel call() throws Exception {
                return new Channel("The other side of the channel", executors, p2i, p1o);
            }
        });
        french = f1.get();
        british = f2.get();
    }

    @Override
    protected void tearDown() throws Exception {
        french.close(); // this will automatically initiate the close on the other channel, too.
        french.join();
        british.join();
        executors.shutdown();
    }
}
