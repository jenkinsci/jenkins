package hudson.slaves;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.Computer;
import hudson.model.Slave;
import hudson.model.TaskListener;
import hudson.remoting.Callable;
import hudson.remoting.Channel;
import hudson.remoting.PingThread;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Establish a periodic ping to keep connections between {@link Slave slaves}
 * and the main Jenkins node alive. This prevents network proxies from
 * terminating connections that are idle for too long.
 */
@Extension
public class ChannelPinger extends ComputerListener {
    private static final Logger LOGGER = Logger.getLogger(ChannelPinger.class.getName());
    private static final String SYS_PROPERTY_NAME  = ChannelPinger.class.getName() + ".pingInterval";

    /**
     * Interval for the ping in minutes.
     */
    private int pingInterval = 5;

    public ChannelPinger() {
        String interval = System.getProperty(SYS_PROPERTY_NAME);
        if (interval != null) {
            try {
                pingInterval = Integer.valueOf(interval);
            } catch (NumberFormatException e) {
                LOGGER.warning("Ignoring invalid " + SYS_PROPERTY_NAME + "=" + interval);
            }
        }
    }

    @Override
    public void preOnline(Computer c, Channel channel, FilePath root, TaskListener listener)  {
        if (pingInterval < 1) {
            LOGGER.fine("Slave ping is disabled");
            return;
        }

        try {
            channel.call(new SetUpRemotePing(pingInterval));
            LOGGER.fine("Set up a remote ping for " + c.getName());
        } catch (Exception e) {
            LOGGER.severe("Failed to set up a ping for " + c.getName());
        }

        // TODO: Set up a local to remote ping too?
        // If we just want to keep some activity on the channel this doesn't
        // matter, but if we consider the ping a 'are you alive?' check it
        // might be useful.
        //setUpPingForChannel(channel, pingInterval);
    }

    private static class SetUpRemotePing implements Callable<Void, IOException> {
        private static final long serialVersionUID = -2702219700841759872L;
        private int pingInterval;
        public SetUpRemotePing(int pingInterval) {
            this.pingInterval = pingInterval;
        }

        @Override
        public Void call() throws IOException {
            setUpPingForChannel(Channel.current(), pingInterval);
            return null;
        }
    }

    private static void setUpPingForChannel(final Channel channel, int interval) {
        final AtomicBoolean isInClosed = new AtomicBoolean(false);
        final PingThread t = new PingThread(channel, interval * 60 * 1000) {
            protected void onDead() {
                try {
                    if (isInClosed.get()) {
                        LOGGER.fine("Ping failed after socket is already closed");
                    }
                    else {
                        LOGGER.info("Ping failed. Terminating the socket.");
                        channel.close();
                    }
                } catch (IOException e) {
                    LOGGER.severe("Failed to terminate the socket: " + e);
                }
            }
        };

        channel.addListener(new Channel.Listener() {
            @Override
            public void onClosed(Channel channel, IOException cause) {
                LOGGER.fine("Terminating ping thread for " + channel);
                isInClosed.set(true);
                t.interrupt();  // make sure the ping thread is terminated
            }
        });

        t.start();
        LOGGER.fine("Ping thread started for " + channel + " with a " + interval + " minute interval");
    }
}
