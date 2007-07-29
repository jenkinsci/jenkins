package hudson.node_monitors;

import hudson.model.Computer;
import hudson.model.Hudson;
import hudson.model.Node;
import hudson.triggers.Trigger;
import hudson.util.ClockDifference;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link NodeMonitor} that checks clock of {@link Node} to
 * detect out of sync clocks.
 *
 * @author Kohsuke Kawaguchi
 */
public class ClockMonitor extends NodeMonitor {
    public String getColumnCaption() {
        return "Clock Diff";
    }

    /**
     * Obtains the difference.
     */
    public ClockDifference getDifferenceFor(Computer c) {
        if(record==null) {
            // if this is the first time, try to check it now
            if(inProgress==null) {
                synchronized(ClockMonitor.class) {
                    if(inProgress==null)
                        new Record().start();
                }
            }
            return null;
        }
        return record.diff.get(c);
    }

    /**
     * Represents the last record of the update
     */
    private static volatile Record record = null;

    /**
     * Represents the update activity in progress.
     */
    private static volatile Record inProgress = null;

    /**
     * Thread that computes the clock difference, as well as the data structure to record
     * the result.
     */
    private static final class Record extends Thread {
        /**
         * Last computed clock difference.
         */
        private final Map<Computer,ClockDifference> diff = new HashMap<Computer,ClockDifference>();

        /**
         * When was {@link #diff} last updated?
         */
        private Date lastUpdated;

        public Record() {
            super("clock monitor update thread started "+new Date());
            synchronized(ClockMonitor.class) {
                if(inProgress!=null) {
                    // maybe it got stuck?
                    LOGGER.warning("Previous clock check still in progress. Interrupting");
                    inProgress.interrupt();
                }
                inProgress = this;
            }
        }

        public void run() {
            try {
                long startTime = System.currentTimeMillis();

                for( Computer c : Hudson.getInstance().getComputers() ) {
                    Node n = c.getNode();
                    if(n!=null && !c.isOffline())
                        try {
                            diff.put(c, n.getClockDifference());
                        } catch (IOException e) {
                            LOGGER.log(Level.WARNING, "Failed to check clock", e);
                        }
                }

                lastUpdated = new Date();

                synchronized(ClockMonitor.class) {
                    assert inProgress==this;
                    inProgress = null;
                    record = this;
                }

                LOGGER.info("Clock difference check completed in "+(System.currentTimeMillis()-startTime)+"ms");
            } catch (InterruptedException e) {
                LOGGER.log(Level.WARNING,"Clock difference check aborted",e);
            }
        }
    }

    private static final Logger LOGGER = Logger.getLogger(ClockMonitor.class.getName());

    static {
        long HOUR = 1000*60*60L;

        // check every hour
        Trigger.timer.scheduleAtFixedRate(new TimerTask() {
            public void run() {
                // start checking
                new Record().start();
            }
        },HOUR,HOUR);
    }
}
