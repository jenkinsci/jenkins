package hudson.remoting;

import junit.framework.Assert;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Hides the logic of starting/stopping a channel for test.
 *
 * @author Kohsuke Kawaguchi
 */
interface ChannelRunner {
    Channel start() throws Exception;
    void stop(Channel channel) throws Exception;
    String getName();

    Class<? extends ChannelRunner>[] LIST = new Class[] {
        InProcess.class,
        Fork.class
    };


    /**
     * Runs a channel in the same JVM.
     */
    static class InProcess implements ChannelRunner {
        private ExecutorService executor;
        /**
         * failure occurred in the other {@link Channel}.
         */
        private Exception failure;

        public Channel start() throws Exception {
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

            return new Channel("north", executor, in1, out2);
        }

        public void stop(Channel channel) throws Exception {
            channel.close();

            System.out.println("north completed");

            executor.shutdown();

            if(failure!=null)
                throw failure;  // report a failure in the south side
        }

        public String getName() {
            return "local";
        }
    }

    /**
     * Runs a channel in a separate JVM by launching a new JVM.
     */
    static class Fork implements ChannelRunner {
        private Process proc;
        private ExecutorService executor;
        private Copier copier;

        public Channel start() throws Exception {
            System.out.println("forking a new process");
            // proc = Runtime.getRuntime().exec("java -Xdebug -Xrunjdwp:transport=dt_socket,server=y,address=8000 hudson.remoting.Launcher");
            proc = Runtime.getRuntime().exec("java hudson.remoting.Launcher");

            copier = new Copier("copier",proc.getErrorStream(),System.err);
            copier.start();

            executor = Executors.newCachedThreadPool();
            return new Channel("north", executor, proc.getInputStream(), proc.getOutputStream());
        }

        public void stop(Channel channel) throws Exception {
            channel.close();

            System.out.println("north completed");

            executor.shutdown();

            copier.join();
            int r = proc.waitFor();
            System.out.println("south completed");

            Assert.assertEquals("exit code should have been 0",0,r);
        }

        public String getName() {
            return "fork";
        }
    }
}
