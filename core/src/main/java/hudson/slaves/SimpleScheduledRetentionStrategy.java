/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Stephen Connolly
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

import antlr.ANTLRException;
import hudson.Extension;
import static hudson.Util.fixNull;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Queue;
import hudson.scheduler.CronTabList;
import hudson.util.FormValidation;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.concurrent.GuardedBy;
import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import static java.util.logging.Level.INFO;

/**
 * {@link RetentionStrategy} that controls the agent based on a schedule.
 *
 * @author Stephen Connolly
 * @since 1.275
 */
public class SimpleScheduledRetentionStrategy extends RetentionStrategy<SlaveComputer> {

    private static final Logger LOGGER = Logger.getLogger(SimpleScheduledRetentionStrategy.class.getName());

    private final String startTimeSpec;
    private transient CronTabList tabs;
    private transient Calendar lastChecked;
    private transient long nextStop = Long.MIN_VALUE;
    private transient long nextStart = Long.MIN_VALUE;
    private transient long lastStop = Long.MAX_VALUE;
    private transient long lastStart = Long.MAX_VALUE;
    private final int upTimeMins;
    private final boolean keepUpWhenActive;

    @DataBoundConstructor
    public SimpleScheduledRetentionStrategy(String startTimeSpec, int upTimeMins, boolean keepUpWhenActive)
            throws ANTLRException {
        this.startTimeSpec = startTimeSpec;
        this.keepUpWhenActive = keepUpWhenActive;
        this.tabs = CronTabList.create(startTimeSpec);
        this.lastChecked = new GregorianCalendar();
        this.upTimeMins = Math.max(1, upTimeMins);
        this.lastChecked.add(Calendar.MINUTE, -1);
    }

    public int getUpTimeMins() {
        return upTimeMins;
    }

    public boolean isKeepUpWhenActive() {
        return keepUpWhenActive;
    }

    public String getStartTimeSpec() {
        return startTimeSpec;
    }

    private synchronized void updateStartStopWindow() {
        if (lastStart == Long.MAX_VALUE && lastStop == Long.MAX_VALUE) {
            // we need to initialize

            // get some useful default values for the lastStart and lastStop... they should be in the past and at least
            // less than any useful real last start/stop
            // so default lastStart = now - upTime * 3, and lastStop = now - upTime * 2
            Calendar time = new GregorianCalendar();
            time.add(Calendar.MINUTE, -upTimeMins);
            time.add(Calendar.MINUTE, -upTimeMins);
            time.add(Calendar.MINUTE, -upTimeMins);
            lastStart = time.getTimeInMillis();
            time.add(Calendar.MINUTE, upTimeMins);
            lastStop = time.getTimeInMillis();

            // we're only interested in the last start if it is less than the upTimeMins ago
            // any older and last Start is not relevant as the node should be stopped
            time = new GregorianCalendar();
            time.add(Calendar.MINUTE, -upTimeMins);
            time.add(Calendar.MINUTE, -1);

            while (System.currentTimeMillis() + 1000 > time.getTimeInMillis()) {
                if (tabs.check(time)) {
                    lastStart = time.getTimeInMillis();
                    time.add(Calendar.MINUTE, upTimeMins);
                    lastStop = time.getTimeInMillis();
                    break;
                }
                time.add(Calendar.MINUTE, 1);
            }
            nextStart = lastStart;
            nextStop = lastStop;
        }
        if (nextStop < System.currentTimeMillis()) {
            // next stop is in the past
            lastStart = nextStart;
            lastStop = nextStop;

            // we don't want to look too far into the future
            Calendar time = new GregorianCalendar();
            time.add(Calendar.MINUTE, Math.min(15, upTimeMins));
            long stopLooking = time.getTimeInMillis();
            time.setTimeInMillis(nextStop);
            while (stopLooking > time.getTimeInMillis()) {
                if (tabs.check(time)) {
                    nextStart = time.getTimeInMillis();
                    time.add(Calendar.MINUTE, upTimeMins);
                    nextStop = time.getTimeInMillis();
                    break;
                }
                time.add(Calendar.MINUTE, 1);
            }
        }
    }

    protected synchronized Object readResolve() throws ObjectStreamException {
        try {
            tabs = CronTabList.create(startTimeSpec);
            lastChecked = new GregorianCalendar();
            this.lastChecked.add(Calendar.MINUTE, -1);
            nextStop = Long.MIN_VALUE;
            nextStart = Long.MIN_VALUE;
            lastStop = Long.MAX_VALUE;
            lastStart = Long.MAX_VALUE;
        } catch (ANTLRException e) {
            InvalidObjectException x = new InvalidObjectException(e.getMessage());
            x.initCause(e);
            throw x;
        }
        return this;
    }

    @Override
    public boolean isManualLaunchAllowed(final SlaveComputer c) {
        return isOnlineScheduled();
    }

    @GuardedBy("hudson.model.Queue.lock")
    public synchronized long check(final SlaveComputer c) {
        boolean shouldBeOnline = isOnlineScheduled();
        LOGGER.log(Level.FINE, "Checking computer {0} against schedule. online = {1}, shouldBeOnline = {2}",
                new Object[]{c.getName(), c.isOnline(), shouldBeOnline});
        if (shouldBeOnline && c.isOffline()) {
            LOGGER.log(INFO, "Trying to launch computer {0} as schedule says it should be on-line at "
                    + "this point in time", new Object[]{c.getName()});
            if (c.isLaunchSupported()) {
                Computer.threadPoolForRemoting.submit(new Runnable() {
                    public void run() {
                        try {
                            c.connect(true).get();
                            if (c.isOnline()) {
                                LOGGER.log(INFO, "Launched computer {0} per schedule", new Object[]{c.getName()});
                            }
                            if (keepUpWhenActive && c.isOnline() && !c.isAcceptingTasks()) {
                                LOGGER.log(INFO,
                                        "Enabling new jobs for computer {0} as it has started its scheduled uptime",
                                        new Object[]{c.getName()});
                                c.setAcceptingTasks(true);
                            }
                        } catch (InterruptedException e) {
                        } catch (ExecutionException e) {
                        }
                    }
                });
            }
        } else if (!shouldBeOnline && c.isOnline()) {
            if (keepUpWhenActive) {
                if (!c.isIdle() && c.isAcceptingTasks()) {
                    c.setAcceptingTasks(false);
                    LOGGER.log(INFO,
                            "Disabling new jobs for computer {0} as it has finished its scheduled uptime",
                            new Object[]{c.getName()});
                    return 1;
                } else if (c.isIdle() && c.isAcceptingTasks()) {
                    Queue.withLock(new Runnable() {
                        @Override
                        public void run() {
                            if (c.isIdle()) {
                                LOGGER.log(INFO, "Disconnecting computer {0} as it has finished its scheduled uptime",
                                        new Object[]{c.getName()});
                                c.disconnect(OfflineCause
                                        .create(Messages._SimpleScheduledRetentionStrategy_FinishedUpTime()));
                            } else {
                                c.setAcceptingTasks(false);
                            }
                        }
                    });
                } else if (c.isIdle() && !c.isAcceptingTasks()) {
                    Queue.withLock(new Runnable() {
                        @Override
                        public void run() {
                            if (c.isIdle()) {
                                LOGGER.log(INFO, "Disconnecting computer {0} as it has finished all jobs running when "
                                        + "it completed its scheduled uptime", new Object[]{c.getName()});
                                c.disconnect(OfflineCause
                                        .create(Messages._SimpleScheduledRetentionStrategy_FinishedUpTime()));
                            }
                        }
                    });
                }
            } else {
                // no need to get the queue lock as the user has selected the break builds option!
                LOGGER.log(INFO, "Disconnecting computer {0} as it has finished its scheduled uptime",
                        new Object[]{c.getName()});
                c.disconnect(OfflineCause.create(Messages._SimpleScheduledRetentionStrategy_FinishedUpTime()));
            }
        }
        return 1;
    }

    private boolean isOnlineScheduled() {
        updateStartStopWindow();
        long now = System.currentTimeMillis();
        return (lastStart < now && lastStop > now) || (nextStart < now && nextStop > now);
    }

    @Extension @Symbol("schedule")
    public static class DescriptorImpl extends Descriptor<RetentionStrategy<?>> {
        public String getDisplayName() {
            return Messages.SimpleScheduledRetentionStrategy_displayName();
        }

        /**
         * Performs syntax check.
         */
        public FormValidation doCheck(@QueryParameter String value) {
            try {
                String msg = CronTabList.create(fixNull(value)).checkSanity();
                if (msg != null)
                    return FormValidation.warning(msg);
                return FormValidation.ok();
            } catch (ANTLRException e) {
                return FormValidation.error(e.getMessage());
            }
        }
    }
}
