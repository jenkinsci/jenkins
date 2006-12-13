package hudson.remoting;

import java.util.concurrent.Executors;

/**
 * Entry point for running a {@link Channel} that uses stdin/stdout.
 *
 * This can be used as the main class for launching a channel on
 * a separate JVM.
 *
 * @author Kohsuke Kawaguchi
 */
public class Launcher {
    public static void main(String[] args) throws Exception {
        Channel channel = new Channel("channel", Executors.newCachedThreadPool(), System.in, System.out);
        channel.join();
        System.exit(0);
    }
}
