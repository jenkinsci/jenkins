/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc.
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

import hudson.model.AsyncPeriodicWork;
import hudson.model.TaskListener;
import jenkins.model.Jenkins;
import hudson.model.Computer;
import hudson.util.TimeUnit2;
import hudson.remoting.VirtualChannel;
import hudson.remoting.Channel;
import hudson.Extension;
import jenkins.util.SystemProperties;
import jenkins.security.SlaveToMasterCallable;
import org.jenkinsci.Symbol;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * Makes sure that connections to agents are alive, and if they are not, cut them off.
 *
 * <p>
 * If we only rely on TCP retransmission time out for this, the time it takes to detect a bad connection
 * is in the order of 10s of minutes, so we take the matters to our own hands.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.325
 */
@Extension @Symbol("connectionActivityMonitor")
public class ConnectionActivityMonitor extends AsyncPeriodicWork {
    public ConnectionActivityMonitor() {
        super("Connection Activity monitoring to agents");
    }

    protected void execute(TaskListener listener) throws IOException, InterruptedException {
        if (!enabled)   return;

        long now = System.currentTimeMillis();
        for (Computer c: Jenkins.getInstance().getComputers()) {
            VirtualChannel ch = c.getChannel();
            if (ch instanceof Channel) {
                Channel channel = (Channel) ch;
                if (now-channel.getLastHeard() > TIME_TILL_PING) {
                    // haven't heard from this agent for a while.
                    Long lastPing = (Long)channel.getProperty(ConnectionActivityMonitor.class);

                    if (lastPing!=null && now-lastPing > TIMEOUT) {
                        LOGGER.info("Repeated ping attempts failed on "+c.getName()+". Disconnecting");
                        c.disconnect(OfflineCause.create(Messages._ConnectionActivityMonitor_OfflineCause()));
                    } else {
                        // send a ping. if we receive a reply, it will be reflected in the next getLastHeard() call.
                        channel.callAsync(PING_COMMAND);
                        if (lastPing==null)
                            channel.setProperty(ConnectionActivityMonitor.class,now);
                    }
                } else {
                    // we are receiving data nicely
                    channel.setProperty(ConnectionActivityMonitor.class,null);
                }
            }
        }
    }

    public long getRecurrencePeriod() {
        return enabled ? FREQUENCY : TimeUnit2.DAYS.toMillis(30);
    }

    /**
     * Time till initial ping
     */
    private static final long TIME_TILL_PING = SystemProperties.getLong(ConnectionActivityMonitor.class.getName()+".timeToPing",TimeUnit2.MINUTES.toMillis(3));

    private static final long FREQUENCY = SystemProperties.getLong(ConnectionActivityMonitor.class.getName()+".frequency",TimeUnit2.SECONDS.toMillis(10));

    /**
     * When do we abandon the effort and cut off?
     */
    private static final long TIMEOUT = SystemProperties.getLong(ConnectionActivityMonitor.class.getName()+".timeToPing",TimeUnit2.MINUTES.toMillis(4));


    // disabled by default until proven in the production
    public boolean enabled = SystemProperties.getBoolean(ConnectionActivityMonitor.class.getName()+".enabled");

    private static final PingCommand PING_COMMAND = new PingCommand();
    private static final class PingCommand extends SlaveToMasterCallable<Void,RuntimeException> {
        public Void call() throws RuntimeException {
            return null;
        }

        private static final long serialVersionUID = 1L;
    }

    private static final Logger LOGGER = Logger.getLogger(ConnectionActivityMonitor.class.getName());
}
