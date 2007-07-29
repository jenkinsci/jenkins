package hudson.node_monitors;

import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.triggers.Trigger;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TimerTask;
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
    protected AbstractNodeMonitorDescriptor(Class<? extends NodeMonitor> clazz) {
        this(clazz,HOUR);
    }

    protected AbstractNodeMonitorDescriptor(Class<? extends NodeMonitor> clazz, long interval) {
        super(clazz);

        // check every hour
        Trigger.timer.scheduleAtFixedRate(new TimerTask() {
            public void run() {
                // start checking
                new Record().start();
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
     */
    protected abstract T monitor(Computer c) throws IOException,InterruptedException;

    /**
     * Obtains the monitoring result.
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
     * Thread that monitors nodes, as well as the data structure to record
     * the result.
     */
    private final class Record extends Thread {
        /**
         * Last computed monitoring result.
         */
        private final Map<Computer,T> data = new HashMap<Computer,T>();

        /**
         * When was {@link #data} last updated?
         */
        private Date lastUpdated;

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
                        data.put(c,monitor(c));
                    } catch (IOException e) {
                        LOGGER.log(Level.WARNING, "Failed to monitor "+c+" for "+getDisplayName(), e);
                    }
                }

                lastUpdated = new Date();

                synchronized(AbstractNodeMonitorDescriptor.this) {
                    assert inProgress==this;
                    inProgress = null;
                    record = this;
                }

                LOGGER.info("Node monitoring "+getDisplayName()+" completed in "+(System.currentTimeMillis()-startTime)+"ms");
            } catch (InterruptedException e) {
                LOGGER.log(Level.WARNING,"Node monitoring "+getDisplayName()+" aborted.",e);
            }
        }
    }

    private final Logger LOGGER = Logger.getLogger(getClass().getName());

    private static final long HOUR = 1000*60*60L;
}
