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

package hudson.node_monitors;

import hudson.Util;
import hudson.model.AdministrativeMonitor;
import hudson.model.Computer;
import hudson.model.ComputerSet;
import hudson.model.Descriptor;
import hudson.slaves.OfflineCause;
import hudson.triggers.SafeTimerTask;
import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.util.SystemProperties;
import jenkins.util.Timer;
import net.jcip.annotations.GuardedBy;

/**
 * Convenient base class for common {@link NodeMonitor} implementation
 * where the "monitoring" consists of executing something periodically on every node
 * and taking some action based on its result.
 *
 * @param <T>
 *     represents the result of the monitoring.
 * @author Kohsuke Kawaguchi
 */
public abstract class AbstractNodeMonitorDescriptor<T> extends Descriptor<NodeMonitor> {
    private static long PERIOD = TimeUnit.MINUTES.toMillis(SystemProperties.getInteger(AbstractNodeMonitorDescriptor.class.getName() + ".periodMinutes", 60));

    /**
     * @deprecated as of 1.522
     *      Extend from {@link AbstractAsyncNodeMonitorDescriptor}
     */
    @Deprecated
    protected AbstractNodeMonitorDescriptor() {
        this(PERIOD);
    }

    /**
     * Indicates if this monitor is capable to take agents offline in case it detects a problem.
     * If true, this will enable the configuration option to ignore the monitor.
     * Defaults to {@code true} so plugins that do not override this method behave as before.
     * Plugins that do implement a monitor that will not take agents offline should override this
     * method and return false.
     *
     * @return true if this monitor might take agents offline
     * @since 2.437
     */
    public boolean canTakeOffline() {
        return true;
    }

    @Override
    public String getConfigPage() {
        return getViewPage(clazz, "configure.jelly");
    }

    /**
     * @deprecated as of 1.522
     *      Extend from {@link AbstractAsyncNodeMonitorDescriptor}
     */
    @Deprecated
    protected AbstractNodeMonitorDescriptor(long interval) {
        schedule(interval);
    }

    /**
     * @deprecated as of 1.522
     *      Extend from {@link AbstractAsyncNodeMonitorDescriptor}
     */
    @Deprecated
    protected AbstractNodeMonitorDescriptor(Class<? extends NodeMonitor> clazz) {
        this(clazz, PERIOD);
    }

    /**
     * @deprecated as of 1.522
     *      Extend from {@link AbstractAsyncNodeMonitorDescriptor}
     */
    @Deprecated
    protected AbstractNodeMonitorDescriptor(Class<? extends NodeMonitor> clazz, long interval) {
        super(clazz);

        schedule(interval);
    }

    private void schedule(long interval) {
        Timer.get()
            .scheduleAtFixedRate(new SafeTimerTask() {
                @Override
                public void doRun() {
                    triggerUpdate();
                }
            }, interval, interval, TimeUnit.MILLISECONDS);
    }

    /**
     * Represents the last record of the update.
     *
     * Once set to non-null, never be null.
     */
    private transient volatile Record record = null;

    /**
     * Represents the update activity in progress.
     */
    @GuardedBy("this")
    private transient Record inProgress = null;

    /**
     * Represents when an update activity was last started.
     */
    @GuardedBy("this")
    private transient long inProgressStarted = Long.MIN_VALUE;

    /**
     * Performs monitoring of the given computer object.
     * This method is invoked periodically to perform the monitoring of the computer.
     *
     * @return
     *      Application-specific value that represents the observed monitoring value
     *      on the given node. This value will be returned from the {@link #get(Computer)} method.
     *      If null is returned, it will be interpreted as "no observed value." This is
     *      convenient way of abandoning the observation on a particular computer,
     *      whereas {@link IOException} is useful for indicating a hard error that needs to be
     *      corrected.
     */
    protected abstract T monitor(Computer c) throws IOException, InterruptedException;

    /**
     * Performs monitoring across the board.
     *
     * @return
     *      For all the computers, report the monitored values.
     */
    protected Map<Computer, T> monitor() throws InterruptedException {
        Map<Computer, T> data = new HashMap<>();
        for (Computer c : Jenkins.get().getComputers()) {
            try {
                Thread.currentThread().setName("Monitoring " + c.getDisplayName() + " for " + getDisplayName());

                if (c.getChannel() == null)
                    data.put(c, null);
                else
                    data.put(c, monitor(c));
            } catch (RuntimeException | IOException e) {
                LOGGER.log(Level.WARNING, "Failed to monitor " + c.getDisplayName() + " for " + getDisplayName(), e);
            } catch (InterruptedException e) {
                throw (InterruptedException) new InterruptedException("Node monitoring " + c.getDisplayName() + " for " + getDisplayName() + " aborted.").initCause(e);
            }
        }
        return data;
    }

    /**
     * Obtains the monitoring result currently available, or null if no data is available.
     *
     * <p>
     * If no data is available, a background task to collect data will be started.
     */
    public T get(Computer c) {
        if (record == null || !record.data.containsKey(c)) {
            // if we don't have the data, schedule the check now
            triggerUpdate();
            return null;
        }
        return record.data.get(c);
    }

    /**
     * Is the monitoring activity currently in progress?
     */
    private synchronized boolean isInProgress() {
        return inProgress != null && inProgress.isAlive();
    }

    /**
     * The timestamp that indicates when the last round of the monitoring has completed.
     */
    public long getTimestamp() {
        return record == null ? 0L : record.timestamp;
    }

    public String getTimestampString() {
        if (record == null)
            return Messages.AbstractNodeMonitorDescriptor_NoDataYet();
        return Util.getTimeSpanString(System.currentTimeMillis() - record.timestamp);
    }

    /**
     * Is this monitor currently ignored?
     */
    public boolean isIgnored() {
        NodeMonitor m = ComputerSet.getMonitors().get(this);
        return m == null || m.isIgnored();
    }

    /**
     * Utility method to mark the computer online for derived classes.
     *
     * @return true
     *      if the node was actually taken online by this act (as opposed to us deciding not to do it,
     *      or the computer was already online.)
     */
    protected boolean markOnline(Computer c) {
        if (isIgnored() || c.isOnline()) return false; // noop
        c.setTemporarilyOfflineCause(null);
        return true;
    }

    /**
     * Utility method to mark the computer offline for derived classes.
     *
     * @return true
     *      if the node was actually taken offline by this act (as opposed to us deciding not to do it,
     *      or the computer already marked offline.)
     */
    protected boolean markOffline(Computer c, OfflineCause oc) {
        if (isIgnored() || c.isTemporarilyOffline()) return false; // noop

        c.setTemporarilyOfflineCause(oc);

        // notify the admin
        MonitorMarkedNodeOffline no = AdministrativeMonitor.all().get(MonitorMarkedNodeOffline.class);
        if (no != null)
            no.active = true;
        return true;
    }

    /**
     * @deprecated as of 1.320
     *      Use {@link #markOffline(Computer, OfflineCause)} to specify the cause.
     */
    @Deprecated
    protected boolean markOffline(Computer c) {
        return markOffline(c, null);
    }

    /**
     * @see NodeMonitor#triggerUpdate()
     */
    /*package*/ synchronized Thread triggerUpdate() {
        if (inProgress != null) {
            if (!inProgress.isAlive()) {
                LOGGER.log(Level.WARNING, "Previous {0} monitoring activity died without cleaning up after itself",
                    getDisplayName());
                inProgress = null;
            } else if (System.currentTimeMillis() > inProgressStarted + getMonitoringTimeOut() + 1000) {
                // maybe it got stuck?
                LOGGER.log(Level.WARNING, "Previous {0} monitoring activity still in progress. Interrupting",
                        getDisplayName());
                inProgress.interrupt();
                inProgress = null; // we interrupted the old one so it's now dead to us.
            } else {
                // return the in progress
                return inProgress;
            }
        }
        final Record t = new Record();
        t.start();
        // only store the new thread if we started it
        inProgress = t;
        inProgressStarted = System.currentTimeMillis();
        return inProgress;
    }

    /**
     * Controls the time out of monitoring.
     */
    protected long getMonitoringTimeOut() {
        return TimeUnit.SECONDS.toMillis(30);
    }

    /**
     * Thread that monitors nodes, as well as the data structure to record
     * the result.
     */
    private final class Record extends Thread {
        /**
         * Last computed monitoring result.
         */
        private /*final*/ Map<Computer, T> data = Collections.emptyMap();

        private long timestamp;

        Record() {
            super("Monitoring thread for " + getDisplayName() + " started on " + new Date());
        }

        @Override
        public void run() {
            try {
                long startTime = System.currentTimeMillis();
                String oldName = getName();
                data = monitor();
                setName(oldName);

                timestamp = System.currentTimeMillis();
                record = this;

                LOGGER.log(Level.FINE, "Node monitoring {0} completed in {1}ms", new Object[] {getDisplayName(), System.currentTimeMillis() - startTime});
            } catch (InterruptedException x) {
                // interrupted by new one, fine
            } catch (Throwable t) {
                LOGGER.log(Level.WARNING, "Unexpected node monitoring termination: " + getDisplayName(), t);
            } finally {
                synchronized (AbstractNodeMonitorDescriptor.this) {
                    if (inProgress == this)
                        inProgress = null;
                }
            }
        }
    }

    private static final Logger LOGGER = Logger.getLogger(AbstractNodeMonitorDescriptor.class.getName());
}
