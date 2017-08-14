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

import hudson.Extension;
import hudson.FilePath;
import jenkins.util.SystemProperties;
import hudson.model.Computer;
import hudson.model.Slave;
import hudson.model.TaskListener;
import hudson.remoting.Channel;
import hudson.remoting.PingThread;
import jenkins.security.MasterToSlaveCallable;
import jenkins.slaves.PingFailureAnalyzer;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Establish a periodic ping to keep connections between {@link Slave slaves}
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
    private int pingTimeoutSeconds = SystemProperties.getInteger(TIMEOUT_SECONDS_PROPERTY, PING_TIMEOUT_SECONDS_DEFAULT, Level.WARNING);

    /**
     * Interval for the ping in seconds.
     */
    private int pingIntervalSeconds = PING_INTERVAL_SECONDS_DEFAULT;

    public ChannelPinger() {
        
        Integer interval = SystemProperties.getInteger(INTERVAL_SECONDS_PROPERTY, null, Level.WARNING);
        
        // if interval wasn't set we read the deprecated property in minutes
        if (interval == null) {
            interval = SystemProperties.getInteger(INTERVAL_MINUTES_PROPERTY_DEPRECATED,null, Level.WARNING);
            if (interval != null) {
                LOGGER.warning(INTERVAL_MINUTES_PROPERTY_DEPRECATED + " property is deprecated, " + INTERVAL_SECONDS_PROPERTY + " should be used");
                interval *= 60; //to seconds       
            }
        }
        
        if (interval != null) {
            pingIntervalSeconds = interval;
        }
    }

    @Override
    public void preOnline(Computer c, Channel channel, FilePath root, TaskListener listener)  {
        install(channel);
    }

    public void install(Channel channel) {
        if (pingTimeoutSeconds < 1 || pingIntervalSeconds < 1) {
            LOGGER.warning("Agent ping is disabled");
            return;
        }

        try {
            channel.call(new SetUpRemotePing(pingTimeoutSeconds, pingIntervalSeconds));
            LOGGER.fine("Set up a remote ping for " + channel.getName());
        } catch (Exception e) {
            LOGGER.severe("Failed to set up a ping for " + channel.getName());
        }

        // set up ping from both directions, so that in case of a router dropping a connection,
        // both sides can notice it and take compensation actions.
        setUpPingForChannel(channel, pingTimeoutSeconds, pingIntervalSeconds, true);
    }

    static class SetUpRemotePing extends MasterToSlaveCallable<Void, IOException> {
        private static final long serialVersionUID = -2702219700841759872L;
        @Deprecated
        private transient int pingInterval;
        private final int pingTimeoutSeconds;
        private final int pingIntervalSeconds;

        SetUpRemotePing(int pingTimeoutSeconds, int pingIntervalSeconds) {
            this.pingTimeoutSeconds = pingTimeoutSeconds;
            this.pingIntervalSeconds = pingIntervalSeconds;
        }

        @Override
        public Void call() throws IOException {
            setUpPingForChannel(Channel.current(), pingTimeoutSeconds, pingIntervalSeconds, false);
            return null;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + pingIntervalSeconds;
            result = prime * result + pingTimeoutSeconds;
            return result;
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
            if (pingTimeoutSeconds != other.pingTimeoutSeconds) {
                return false;
            }
            return true;
        }

        protected Object readResolve() {
            if (pingInterval != 0) {
                return new SetUpRemotePing(PING_TIMEOUT_SECONDS_DEFAULT, pingInterval * 60);
            }
            return this;
        }
    }

    static void setUpPingForChannel(final Channel channel, int timeoutSeconds, int intervalSeconds, final boolean analysis) {
        LOGGER.log(Level.FINE, "setting up ping on {0} with a {1} seconds interval and {2} seconds timeout", new Object[] {channel.getName(), intervalSeconds, timeoutSeconds});
        final AtomicBoolean isInClosed = new AtomicBoolean(false);
        final PingThread t = new PingThread(channel, timeoutSeconds * 1000L, intervalSeconds * 1000L) {
            @Override
            protected void onDead(Throwable cause) {
                try {
                    if (analysis) {
                        analyze(cause);
                    }
                    if (isInClosed.get()) {
                        LOGGER.log(Level.FINE,"Ping failed after the channel "+channel.getName()+" is already partially closed.",cause);
                    } else {
                        LOGGER.log(Level.INFO,"Ping failed. Terminating the channel "+channel.getName()+".",cause);
                        channel.close(cause);
                    }
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE,"Failed to terminate the channel "+channel.getName(),e);
                }
            }
            /** Keep in a separate method so we do not even try to do class loading on {@link PingFailureAnalyzer} from an agent JVM. */
            private void analyze(Throwable cause) throws IOException {
                for (PingFailureAnalyzer pfa : PingFailureAnalyzer.all()) {
                    pfa.onPingFailure(channel,cause);
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
