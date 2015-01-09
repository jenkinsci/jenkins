package hudson;


import hudson.remoting.Channel;
import hudson.remoting.FastPipedInputStream;
import hudson.remoting.FastPipedOutputStream;
import java.io.IOException;
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

    @Override protected void after() {
        try {
            french.close(); // this will automatically initiate the close on the other channel, too.
            french.join();
            british.join();
        } catch (IOException e) {
            // perhaps this exception is caused by earlier abnormal termination of the channel?
            /* for the record, this is the failure.
                    Nov 12, 2009 6:18:55 PM hudson.remoting.Channel$CloseCommand execute
                    SEVERE: close command failed on This side of the channel
                    java.io.IOException: Pipe is already closed
                        at hudson.remoting.FastPipedOutputStream.write(FastPipedOutputStream.java:127)
                        at java.io.ObjectOutputStream$BlockDataOutputStream.drain(ObjectOutputStream.java:1838)
                        at java.io.ObjectOutputStream$BlockDataOutputStream.setBlockDataMode(ObjectOutputStream.java:1747)
                        at java.io.ObjectOutputStream.writeNonProxyDesc(ObjectOutputStream.java:1249)
                        at java.io.ObjectOutputStream.writeClassDesc(ObjectOutputStream.java:1203)
                        at java.io.ObjectOutputStream.writeOrdinaryObject(ObjectOutputStream.java:1387)
                        at java.io.ObjectOutputStream.writeObject0(ObjectOutputStream.java:1150)
                        at java.io.ObjectOutputStream.writeFatalException(ObjectOutputStream.java:1538)
                        at java.io.ObjectOutputStream.writeObject(ObjectOutputStream.java:329)
                        at hudson.remoting.Channel.send(Channel.java:413)
                        at hudson.remoting.Channel.close(Channel.java:717)
                        at hudson.remoting.Channel$CloseCommand.execute(Channel.java:676)
                        at hudson.remoting.Channel$ReaderThread.run(Channel.java:860)
                    Caused by: hudson.remoting.FastPipedInputStream$ClosedBy: The pipe was closed at...
                        at hudson.remoting.FastPipedInputStream.close(FastPipedInputStream.java:103)
                        at java.io.ObjectInputStream$PeekInputStream.close(ObjectInputStream.java:2305)
                        at java.io.ObjectInputStream$BlockDataInputStream.close(ObjectInputStream.java:2643)
                        at java.io.ObjectInputStream.close(ObjectInputStream.java:873)
                        at hudson.remoting.Channel$ReaderThread.run(Channel.java:866)
                    Nov 12, 2009 6:18:55 PM hudson.remoting.Channel$CloseCommand execute
                    INFO: close command created at
                    Command close created at
                        at hudson.remoting.Command.<init>(Command.java:58)
                        at hudson.remoting.Command.<init>(Command.java:47)
                        at hudson.remoting.Channel$CloseCommand.<init>(Channel.java:673)
                        at hudson.remoting.Channel$CloseCommand.<init>(Channel.java:673)
                        at hudson.remoting.Channel.close(Channel.java:717)
                        at hudson.remoting.Channel$CloseCommand.execute(Channel.java:676)
                        at hudson.remoting.Channel$ReaderThread.run(Channel.java:860)
                    Nov 12, 2009 6:18:55 PM hudson.remoting.Channel$ReaderThread run
                    SEVERE: I/O error in channel This side of the channel
                    java.io.EOFException
                        at java.io.ObjectInputStream$BlockDataInputStream.peekByte(ObjectInputStream.java:2554)
                        at java.io.ObjectInputStream.readObject0(ObjectInputStream.java:1297)
                        at java.io.ObjectInputStream.readObject(ObjectInputStream.java:351)
                        at hudson.remoting.Channel$ReaderThread.run(Channel.java:849)

             */
            e.printStackTrace();
        } catch (InterruptedException x) {
            throw new AssertionError(x);
        }
        executors.shutdownNow();
    }
}
