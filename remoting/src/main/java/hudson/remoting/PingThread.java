/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
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

    /**
     * Performs a check every this milliseconds.
     */
    private final long interval;

    public PingThread(Channel channel, long interval) {
        super("Ping thread for channel "+channel);
        this.channel = channel;
        this.interval = interval;
        setDaemon(true);
    }

    public PingThread(Channel channel) {
        this(channel,5*60*1000/*5 mins*/);
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
     * Time out in milliseconds.
     * If the response doesn't come back by then, the channel is considered dead.
     */
    private static final long TIME_OUT = 60*1000; // 1 min

    private static final Logger LOGGER = Logger.getLogger(PingThread.class.getName());
}
