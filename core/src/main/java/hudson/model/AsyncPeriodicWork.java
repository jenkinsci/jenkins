package hudson.model;

import hudson.security.ACL;
import hudson.util.StreamTaskListener;
import jenkins.model.Jenkins;
import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextHolder;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

/**
 * {@link PeriodicWork} that takes a long time to run.
 *
 * <p>
 * Subclasses will implement the {@link #execute(TaskListener)} method and can carry out a long-running task.
 * This runs in a separate thread so as not to block the timer thread, and this class handles
 * all those details.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class AsyncPeriodicWork extends PeriodicWork {
    /**
     * Human readable name of the work.
     */
    public final String name;

    private Thread thread;

    protected AsyncPeriodicWork(String name) {
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

                    StreamTaskListener l = createListener();
                    try {
                        ACL.impersonate(ACL.SYSTEM);

                        execute(l);
                    } catch (IOException e) {
                        e.printStackTrace(l.fatalError(e.getMessage()));
                    } catch (InterruptedException e) {
                        e.printStackTrace(l.fatalError("aborted"));
                    } finally {
                        l.closeQuietly();
                    }

                    logger.log(Level.INFO, "Finished "+name+". "+
                        (System.currentTimeMillis()-startTime)+" ms");
                }
            },name+" thread");
            thread.start();
        } catch (Throwable t) {
            logger.log(Level.SEVERE, name+" thread failed with error", t);
        }
    }

    protected StreamTaskListener createListener() {
        try {
            return new StreamTaskListener(getLogFile());
        } catch (IOException e) {
            throw new Error(e);
        }
    }

    /**
     * Determines the log file that records the result of this task.
     */
    protected File getLogFile() {
        return new File(Jenkins.getInstance().getRootDir(),name+".log");
    }

    /**
     * Executes the task.
     *
     * @param listener
     *      Output sent will be reported to the users. (this work is TBD.)
     * @throws InterruptedException
     *      The caller will record the exception and moves on.
     * @throws IOException
     *      The caller will record the exception and moves on.
     */
    protected abstract void execute(TaskListener listener) throws IOException, InterruptedException;
}
