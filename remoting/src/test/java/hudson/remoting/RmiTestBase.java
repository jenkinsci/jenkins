package hudson.remoting;

import junit.framework.TestCase;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author Kohsuke Kawaguchi
 */
public abstract class RmiTestBase extends TestCase {

    protected Channel channel;
    private ExecutorService executor;
    private Exception failure;

    protected void setUp() throws Exception {
        final PipedInputStream in1 = new PipedInputStream();
        final PipedOutputStream out1 = new PipedOutputStream(in1);

        final PipedInputStream in2 = new PipedInputStream();
        final PipedOutputStream out2 = new PipedOutputStream(in2);

        executor = Executors.newCachedThreadPool();

        Thread t = new Thread("south bridge runner") {
            public void run() {
                try {
                    Channel s = new Channel("south", executor, in2, out1);
                    s.join();
                    System.out.println("south completed");
                } catch (IOException e) {
                    e.printStackTrace();
                    failure = e;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    failure = e;
                }
            }
        };
        t.start();

        channel = new Channel("north", executor, in1, out2);
    }


    protected void tearDown() throws Exception {
        channel.close();

        System.out.println("north completed");

        executor.shutdown();

        if(failure!=null)
            throw failure;  // report a failure in the south side
    }
}
