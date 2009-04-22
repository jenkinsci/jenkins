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

import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.ComputerSet;
import hudson.model.AdministrativeMonitor;
import hudson.triggers.Trigger;
import hudson.triggers.SafeTimerTask;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Convenient base class for common {@link NodeMonitor} implementation
 * where the "monitoring" consists of executing something periodically on every node
 * and taking some action based on its result.
 *
 * <p>
 * "T" represents the the result of the monitoring. 
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class AbstractNodeMonitorDescriptor<T> extends Descriptor<NodeMonitor> {
    protected AbstractNodeMonitorDescriptor() {
        this(HOUR);
    }

    protected AbstractNodeMonitorDescriptor(long interval) {
        schedule(interval);

    }

    protected AbstractNodeMonitorDescriptor(Class<? extends NodeMonitor> clazz) {
        this(clazz,HOUR);
    }

    protected AbstractNodeMonitorDescriptor(Class<? extends NodeMonitor> clazz, long interval) {
        super(clazz);

        schedule(interval);
    }

    private void schedule(long interval) {
        Trigger.timer.scheduleAtFixedRate(new SafeTimerTask() {
            public void doRun() {
                triggerUpdate();
            }
        }, interval, interval);
    }

    /**
     * Represents the last record of the update
     */
    private volatile Record record = null;

    /**
     * Represents the update activity in progress.
     */
    private volatile Record inProgress = null;

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
    protected abstract T monitor(Computer c) throws IOException,InterruptedException;

    /**
     * Obtains the monitoring result currently available, or null if no data is available.
     *
     * <p>
     * If no data is available, a background task to collect data will be started.
     */
    public T get(Computer c) {
        if(record==null) {
            // if this is the first time, schedule the check now
            if(inProgress==null) {
                synchronized(this) {
                    if(inProgress==null)
                        new Record().start();
                }
            }
            return null;
        }
        return record.data.get(c);
    }

    /**
     * Is this monitor currently ignored?
     */
    public boolean isIgnored() {
        NodeMonitor m = ComputerSet.getMonitors().get(this);
        return m==null || m.isIgnored();
    }

    /**
     * Utility method to mark the computer offline for derived classes.
     *
     * @return true
     *      if the node was actually taken offline by this act (as opposed to us deciding not to do it,
     *      or the computer already marked offline.)
     */
    protected boolean markOffline(Computer c) {
        if(isIgnored() || c.isTemporarilyOffline()) return false; // noop

        // TODO: define a mechanism to leave a note on this computer so that people know why we took it offline
        c.setTemporarilyOffline(true);

        // notify the admin
        MonitorMarkedNodeOffline no = AdministrativeMonitor.all().get(MonitorMarkedNodeOffline.class);
        if(no!=null)
            no.active = true;
        return true;
    }

    /**
     * @see NodeMonitor#triggerUpdate()
     */
    /*package*/ Thread triggerUpdate() {
        Record t = new Record();
        t.start();
        return t;
    }

    /**
     * Thread that monitors nodes, as well as the data structure to record
     * the result.
     */
    private final class Record extends Thread {
        /**
         * Last computed monitoring result.
         */
        private final Map<Computer,T> data = new HashMap<Computer,T>();

        public Record() {
            super("Monitoring thread for "+getDisplayName()+" started on "+new Date());
            synchronized(AbstractNodeMonitorDescriptor.this) {
                if(inProgress!=null) {
                    // maybe it got stuck?
                    LOGGER.warning("Previous "+getDisplayName()+" monitoring activity still in progress. Interrupting");
                    inProgress.interrupt();
                }
                inProgress = this;
            }
        }

        public void run() {
            try {
                long startTime = System.currentTimeMillis();

                for( Computer c : Hudson.getInstance().getComputers() ) {
                    try {
                        if(c.getChannel()==null)
                            data.put(c,null);
                        else
                            data.put(c,monitor(c));
                    } catch (IOException e) {
                        LOGGER.log(Level.WARNING, "Failed to monitor "+c.getDisplayName()+" for "+getDisplayName(), e);
                    }
                }

                synchronized(AbstractNodeMonitorDescriptor.this) {
                    assert inProgress==this;
                    inProgress = null;
                    record = this;
                }

                LOGGER.fine("Node monitoring "+getDisplayName()+" completed in "+(System.currentTimeMillis()-startTime)+"ms");
            } catch (InterruptedException e) {
                LOGGER.log(Level.WARNING,"Node monitoring "+getDisplayName()+" aborted.",e);
            }
        }
    }

    private final Logger LOGGER = Logger.getLogger(getClass().getName());

    private static final long HOUR = 1000*60*60L;
}
