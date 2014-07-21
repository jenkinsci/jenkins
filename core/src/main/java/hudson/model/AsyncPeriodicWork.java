package hudson.model;

import hudson.security.ACL;
import hudson.util.StreamTaskListener;
import jenkins.model.Jenkins;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.LogRecord;

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
    @SuppressWarnings("deprecation") // in this case we really want to use PeriodicWork.logger since it reports the impl class
    public final void doRun() {
        try {
            if(thread!=null && thread.isAlive()) {
                logger.log(this.getSlowLoggingLevel(), "{0} thread is still running. Execution aborted.", name);
                return;
            }
            thread = new Thread(new Runnable() {
                public void run() {
                    logger.log(getNormalLoggingLevel(), "Started {0}", name);
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

                    logger.log(getNormalLoggingLevel(), "Finished {0}. {1,number} ms",
                            new Object[]{name, (System.currentTimeMillis()-startTime)});
                }
            },name+" thread");
            thread.start();
        } catch (Throwable t) {
            LogRecord lr = new LogRecord(this.getErrorLoggingLevel(), "{0} thread failed with error");
            lr.setThrown(t);
            lr.setParameters(new Object[]{name});
            logger.log(lr);
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
     * Returns the logging level at which normal messages are displayed.
     * 
     * @return 
     *      The logging level as @Level.
     *
     * @since 1.551
     */
    protected Level getNormalLoggingLevel() {
        return Level.INFO;
    }
    
    /**
     * Returns the logging level at which previous task still executing messages is displayed.
     *
     * @return
     *      The logging level as @Level.
     *
     * @since 1.565
     */
    protected Level getSlowLoggingLevel() {
        return getNormalLoggingLevel();
    }

    /**
     * Returns the logging level at which error messages are displayed.
     * 
     * @return 
     *      The logging level as @Level.
     *
     * @since 1.551
     */
    protected Level getErrorLoggingLevel() {
        return Level.SEVERE;
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
