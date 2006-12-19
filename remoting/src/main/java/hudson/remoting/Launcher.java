package hudson.remoting;

import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Entry point for running a {@link Channel} that uses stdin/stdout.
 *
 * <p>
 * This can be used as the main class for launching a channel on
 * a separate JVM.
 *
 * @author Kohsuke Kawaguchi
 */
public class Launcher {
    public static void main(String[] args) throws Exception {
        // this will prevent programs from accidentally writing to System.out
        // and messing up the stream.
        OutputStream os = System.out;
        System.setOut(System.err);

        ExecutorService executor = Executors.newCachedThreadPool();
        Channel channel = new Channel("channel", executor, System.in, os);
        System.err.println("channel started");
        channel.join();
        System.err.println("channel stopped");
        System.exit(0);
    }
}
