package hudson.model;

import hudson.triggers.SafeTimerTask;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Abstract base class for a periodic work.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class PeriodicWork extends SafeTimerTask {

    /**
     * Name of the work.
     */
    private final String name;
    private Thread thread;

    protected final Logger logger = Logger.getLogger(getClass().getName());

    protected PeriodicWork(String name) {
        this.name = name;
    }

    /**
     * Schedules this periodic work now in a new thread, if one isn't already running.
     */
    public final void doRun() {
        try {
            if(thread!=null && thread.isAlive()) {
                logger.log(Level.INFO, name+" thread is still running. Execution aborted.");
                return;
            }
            thread = new Thread(new Runnable() {
                public void run() {
                    logger.log(Level.INFO, "Started "+name);
                    long startTime = System.currentTimeMillis();

                    execute();

                    logger.log(Level.INFO, "Finished "+name+". "+
                        (System.currentTimeMillis()-startTime)+" ms");
                }
            },name+" thread");
            thread.start();
        } catch (Throwable t) {
            logger.log(Level.SEVERE, name+" thread failed with error", t);
        }
    }

    protected abstract void execute();
}
