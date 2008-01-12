package hudson.remoting;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

/**
 * Periodically perform a ping.
 *
 * <p>
 * Useful when a connection needs to be kept alive by sending data,
 * or when the disconnection is not properly detected.
 *
 * <p>
 * {@link #onDead()} method needs to be overrided to define
 * what to do when a connection appears to be dead.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.170
 */
public abstract class PingThread extends Thread {
    private final Channel channel;

    public PingThread(Channel channel) {
        super("Ping thread for channel "+channel);
        this.channel = channel;
    }

    public void run() {
        try {
            while(true) {
                long nextCheck = System.currentTimeMillis()+interval;

                ping();

                // wait until the next check
                long diff;
                while((diff=nextCheck-System.currentTimeMillis())>0)
                    Thread.sleep(diff);
            }
        } catch (IOException e) {
            onDead();
        } catch (InterruptedException e) {
            // use interruption as a way to terminate the ping thread.
            LOGGER.fine(getName()+" is interrupted. Terminating");
        }
    }

    private void ping() throws IOException, InterruptedException {
        Future<?> f = channel.callAsync(new Ping());
        try {
            f.get(TIME_OUT,MILLISECONDS);
        } catch (ExecutionException e) {
            onDead();
        } catch (TimeoutException e) {
            onDead();
        }
    }

    /**
     * Called when ping failed.
     */
    protected abstract void onDead();

    private static final class Ping implements Callable<Void, IOException> {
        private static final long serialVersionUID = 1L;

        public Void call() throws IOException {
            return null;
        }
    }

    /**
     * Performs a check every this milliseconds.
     */
    private static final long interval = 5*60*1000; // 5 mins

    /**
     * Time out in milliseconds.
     * If the response doesn't come back by then, the channel is considered dead.
     */
    private static final long TIME_OUT = 60*1000; // 1 min

    private static final Logger LOGGER = Logger.getLogger(PingThread.class.getName());
}
