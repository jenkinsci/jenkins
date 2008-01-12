package hudson.remoting;

import hudson.remoting.Channel.Mode;

import java.io.OutputStream;
import java.io.IOException;
import java.io.InputStream;
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
        Mode m = Mode.BINARY;
        boolean ping = false;

        for (String arg : args) {
            if(arg.equals("-text")) {
                System.out.println("Running in text mode");
                m = Mode.TEXT;
                continue;
            }
            if(arg.equals("-ping")) {
                ping = true;
                continue;
            }
            System.err.println("Invalid option: "+arg);
            System.exit(-1);
        }

        // this will prevent programs from accidentally writing to System.out
        // and messing up the stream.
        OutputStream os = System.out;
        System.setOut(System.err);
        main(System.in,os,m,ping);
        System.exit(0);
    }

    public static void main(InputStream is, OutputStream os) throws IOException, InterruptedException {
        main(is,os,Mode.BINARY);
    }

    public static void main(InputStream is, OutputStream os, Mode mode) throws IOException, InterruptedException {
        main(is,os,mode,false);
    }

    public static void main(InputStream is, OutputStream os, Mode mode, boolean performPing) throws IOException, InterruptedException {
        ExecutorService executor = Executors.newCachedThreadPool();
        Channel channel = new Channel("channel", executor, mode, is, os);
        System.err.println("channel started");
        if(performPing) {
            System.err.println("Starting periodic ping thread");
            new PingThread(channel) {
                @Override
                protected void onDead() {
                    System.err.println("Ping failed. Terminating");
                    System.exit(-1);
                }
            }.start();
        }
        channel.join();
        System.err.println("channel stopped");
    }
}
