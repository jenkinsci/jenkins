package hudson.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import hudson.remoting.Channel;
import hudson.remoting.ChannelBuilder;
import hudson.remoting.FastPipedInputStream;
import hudson.remoting.FastPipedOutputStream;
import hudson.remoting.VirtualChannel;
import hudson.util.ProcessTree.OSProcess;
import hudson.util.ProcessTree.ProcessCallable;
import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import jenkins.security.MasterToSlaveCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * @author Kohsuke Kawaguchi
 */
class ProcessTreeTest {

    /**
     * Two channels that are connected to each other, but shares the same classloader.
     */
    private Channel french;
    private Channel british;
    private ExecutorService executors;

    @BeforeEach
    void setUp() throws Exception {
        executors = Executors.newCachedThreadPool();
        final FastPipedInputStream p1i = new FastPipedInputStream();
        final FastPipedInputStream p2i = new FastPipedInputStream();
        final FastPipedOutputStream p1o = new FastPipedOutputStream(p1i);
        final FastPipedOutputStream p2o = new FastPipedOutputStream(p2i);

        Future<Channel> f1 = executors.submit(() -> new ChannelBuilder("This side of the channel", executors).withMode(Channel.Mode.BINARY).build(p1i, p2o));
        Future<Channel> f2 = executors.submit(() -> new ChannelBuilder("The other side of the channel", executors).withMode(Channel.Mode.BINARY).build(p2i, p1o));
        french = f1.get();
        british = f2.get();
    }

    @AfterEach
    void tearDown() {
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

    static class  Tag implements Serializable {
        ProcessTree tree;
        OSProcess p;
        int id;
        @Serial
        private static final long serialVersionUID = 1L;
    }

    @Test
    void remoting() throws Exception {
        Assumptions.assumeFalse(ProcessTree.get() == ProcessTree.DEFAULT, "on some platforms where we fail to list any processes");

        Tag t = french.call(new MyCallable());

        // make sure the serialization preserved the reference graph
        assertSame(t.p.getTree(), t.tree);

        // verify that some remote call works
        t.p.getEnvironmentVariables();

        // it should point to the same object
        assertEquals(t.id, t.p.getPid());

        t.p.act(new ProcessCallableImpl());
    }

    private static class MyCallable extends MasterToSlaveCallable<Tag, IOException> implements Serializable {
        @Override
        public Tag call() {
            Tag t = new Tag();
            t.tree = ProcessTree.get();
            t.p = t.tree.iterator().next();
            t.id = t.p.getPid();
            return t;
        }

        @Serial
        private static final long serialVersionUID = 1L;
    }

    private static class ProcessCallableImpl implements ProcessCallable<Void> {
        @Override
        public Void invoke(OSProcess process, VirtualChannel channel) {
            assertNotNull(process);
            assertNotNull(channel);
            return null;
        }
    }
}
