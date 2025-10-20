/*
 * The MIT License
 *
 * Copyright (c) 2011, Nathan Parry
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

package hudson.slaves;

import com.google.common.annotations.VisibleForTesting;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Computer;
import hudson.model.Slave;
import hudson.model.TaskListener;
import hudson.remoting.Channel;
import hudson.remoting.PingThread;
import java.io.IOException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.security.MasterToSlaveCallable;
import jenkins.slaves.PingFailureAnalyzer;
import jenkins.util.SystemProperties;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Establish a periodic ping to keep connections between {@link Slave agents}
 * and the main Jenkins node alive. This prevents network proxies from
 * terminating connections that are idle for too long.
 *
 * @since 1.405
 */
@Extension
public class ChannelPinger extends ComputerListener {
    static final int PING_TIMEOUT_SECONDS_DEFAULT = 4 * 60;
    static final int PING_INTERVAL_SECONDS_DEFAULT = 5 * 60;

    private static final Logger LOGGER = Logger.getLogger(ChannelPinger.class.getName());
    private static final String TIMEOUT_SECONDS_PROPERTY = ChannelPinger.class.getName() + ".pingTimeoutSeconds";
    private static final String INTERVAL_MINUTES_PROPERTY_DEPRECATED = ChannelPinger.class.getName() + ".pingInterval";
    private static final String INTERVAL_SECONDS_PROPERTY = ChannelPinger.class.getName() + ".pingIntervalSeconds";

    /**
     * Timeout for the ping in seconds.
     */
    private Duration pingTimeout = SystemProperties.getDuration(TIMEOUT_SECONDS_PROPERTY, ChronoUnit.SECONDS, Duration.ofSeconds(PING_TIMEOUT_SECONDS_DEFAULT));

    /**
     * Interval for the ping in seconds.
     */
    private Duration pingInterval = Duration.ofSeconds(PING_INTERVAL_SECONDS_DEFAULT);

    public ChannelPinger() {

        Duration interval = SystemProperties.getDuration(INTERVAL_SECONDS_PROPERTY, ChronoUnit.SECONDS, null);

        // if interval wasn't set we read the deprecated property in minutes
        if (interval == null) {
            interval = SystemProperties.getDuration(INTERVAL_MINUTES_PROPERTY_DEPRECATED, ChronoUnit.MINUTES, null);
            if (interval != null) {
                LOGGER.warning(INTERVAL_MINUTES_PROPERTY_DEPRECATED + " property is deprecated, " + INTERVAL_SECONDS_PROPERTY + " should be used");
            }
        }

        if (interval != null) {
            pingInterval = interval;
        }
    }

    @Override
    public void preOnline(Computer c, Channel channel, FilePath root, TaskListener listener)  {
        SlaveComputer slaveComputer = null;
        if (c instanceof SlaveComputer) {
            slaveComputer = (SlaveComputer) c;
        }
        install(channel, slaveComputer);
    }

    public void install(Channel channel) {
        install(channel, null);
    }

    @VisibleForTesting
    /*package*/ void install(Channel channel, @CheckForNull SlaveComputer c) {
        var pingTimeoutSeconds = (int) pingTimeout.toSeconds();
        var pingIntervalSeconds = (int) pingInterval.toSeconds();
        if (pingTimeoutSeconds < 1 || pingIntervalSeconds < 1) {
            LOGGER.warning("Agent ping is disabled");
            return;
        }

        // set up ping from both directions, so that in case of a router dropping a connection,
        // both sides can notice it and take compensation actions.
        try {
            channel.call(new SetUpRemotePing(pingTimeoutSeconds, pingIntervalSeconds));
            LOGGER.fine("Set up a remote ping for " + channel.getName());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to set up a ping for " + channel.getName(), e);
        }

        setUpPingForChannel(channel, c, pingTimeoutSeconds, pingIntervalSeconds, true);
    }

    @VisibleForTesting
    @Restricted(NoExternalUse.class)
    public static class SetUpRemotePing extends MasterToSlaveCallable<Void, IOException> {
        private static final long serialVersionUID = -2702219700841759872L;
        @Deprecated
        private transient int pingInterval;
        private final int pingTimeoutSeconds;
        private final int pingIntervalSeconds;

        public SetUpRemotePing(int pingTimeoutSeconds, int pingIntervalSeconds) {
            this.pingTimeoutSeconds = pingTimeoutSeconds;
            this.pingIntervalSeconds = pingIntervalSeconds;
        }

        @Override
        public Void call() throws IOException {
            // No sense in setting up channel pinger if the channel is being closed
            setUpPingForChannel(getOpenChannelOrFail(), null, pingTimeoutSeconds, pingIntervalSeconds, false);
            return null;
        }

        @Override
        public int hashCode() {
            return Objects.hash(pingIntervalSeconds, pingTimeoutSeconds);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            SetUpRemotePing other = (SetUpRemotePing) obj;
            if (pingIntervalSeconds != other.pingIntervalSeconds) {
                return false;
            }
            return pingTimeoutSeconds == other.pingTimeoutSeconds;
        }

        protected Object readResolve() {
            if (pingInterval != 0) {
                return new SetUpRemotePing(PING_TIMEOUT_SECONDS_DEFAULT, pingInterval * 60);
            }
            return this;
        }
    }

    @VisibleForTesting
    @Restricted(NoExternalUse.class)
    public static void setUpPingForChannel(final Channel channel, final SlaveComputer computer, int timeoutSeconds, int intervalSeconds, final boolean analysis) {
        LOGGER.log(Level.FINE, "setting up ping on {0} with a {1} seconds interval and {2} seconds timeout", new Object[] {channel.getName(), intervalSeconds, timeoutSeconds});
        final AtomicBoolean isInClosed = new AtomicBoolean(false);
        final PingThread t = new PingThread(channel, TimeUnit.SECONDS.toMillis(timeoutSeconds), TimeUnit.SECONDS.toMillis(intervalSeconds)) {
            @Override
            protected void onDead(Throwable cause) {
                    if (analysis) {
                        analyze(cause);
                    }
                    boolean inClosed = isInClosed.get();
                    // Disassociate computer channel before closing it
                    if (computer != null) {
                        Exception exception = cause instanceof Exception ? (Exception) cause : new IOException(cause);
                        computer.disconnect(new OfflineCause.ChannelTermination(exception));
                    }
                    if (inClosed) {
                        LOGGER.log(Level.FINE, "Ping failed after the channel " + channel.getName() + " is already partially closed.", cause);
                    } else {
                        LOGGER.log(Level.INFO, "Ping failed. Terminating the channel " + channel.getName() + ".", cause);
                        if (computer == null) {
                            // Disconnect from agent side.
                            try {
                                channel.close(cause);
                            } catch (IOException x) {
                                LOGGER.log(Level.WARNING, "could not disconnect " + channel.getName(), x);
                            }
                        }
                    }
            }
            /** Keep in a separate method so we do not even try to do class loading on {@link PingFailureAnalyzer} from an agent JVM. */

            private void analyze(Throwable cause) {
                for (PingFailureAnalyzer pfa : PingFailureAnalyzer.all()) {
                    try {
                        pfa.onPingFailure(channel, cause);
                    } catch (IOException ex) {
                        LOGGER.log(Level.WARNING, "Ping failure analyzer " + pfa.getClass().getName() + " failed for " + channel.getName(), ex);
                    }
                }
            }

            @Deprecated
            @Override
            protected void onDead() {
                onDead(null);
            }
        };

        channel.addListener(new Channel.Listener() {
            @Override
            public void onClosed(Channel channel, IOException cause) {
                LOGGER.fine("Terminating ping thread for " + channel.getName());
                isInClosed.set(true);
                t.interrupt();  // make sure the ping thread is terminated
            }
        });

        t.start();
        LOGGER.log(Level.FINE, "Ping thread started for {0} with a {1} seconds interval and a {2} seconds timeout",
                   new Object[] { channel, intervalSeconds, timeoutSeconds });
    }
}
