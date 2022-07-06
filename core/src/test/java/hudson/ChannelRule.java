package hudson;


import hudson.remoting.Channel;
import hudson.remoting.ChannelBuilder;
import hudson.remoting.FastPipedInputStream;
import hudson.remoting.FastPipedOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.rules.ExternalResource;

/**
 * Test that uses a connected channel.
 *
 * @author Kohsuke Kawaguchi
 */
public final class ChannelRule extends ExternalResource {
    /**
     * Two channels that are connected to each other, but shares the same classloader.
     */
    public Channel french;
    public Channel british;
    private ExecutorService executors;

    @Override protected void before() throws Exception {
        executors = Executors.newCachedThreadPool();
        final FastPipedInputStream p1i = new FastPipedInputStream();
        final FastPipedInputStream p2i = new FastPipedInputStream();
        final FastPipedOutputStream p1o = new FastPipedOutputStream(p1i);
        final FastPipedOutputStream p2o = new FastPipedOutputStream(p2i);

        Future<Channel> f1 = executors.submit(new Callable<>() {
            @Override
            public Channel call() throws Exception {
                return new ChannelBuilder("This side of the channel", executors).withMode(Channel.Mode.BINARY).build(p1i, p2o);
            }
        });
        Future<Channel> f2 = executors.submit(new Callable<>() {
            @Override
            public Channel call() throws Exception {
                return new ChannelBuilder("The other side of the channel", executors).withMode(Channel.Mode.BINARY).build(p2i, p1o);
            }
        });
        french = f1.get();
        british = f2.get();
    }

    @Override protected void after() {
        try {
            french.close(); // this will automatically initiate the close on the other channel, too.
            french.join();
            british.join();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException x) {
            throw new AssertionError(x);
        }
        executors.shutdownNow();
    }
}
