package hudson.model;

import hudson.Functions;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.util.StreamTaskListener;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import jenkins.model.Jenkins;
import jenkins.util.SystemProperties;

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
     * The default number of minutes after which to try and rotate the log file used by {@link #createListener()}.
     * This value is controlled by the system property {@code hudson.model.AsyncPeriodicWork.logRotateMinutes}.
     * Each individual AsyncPeriodicWork can also have a per-extension override using the system property
     * based on their fully qualified class name with {@code .logRotateMinutes} appended.
     *
     * @since 1.651
     */
    private static final long LOG_ROTATE_MINUTES = SystemProperties.getLong(AsyncPeriodicWork.class.getName() + ".logRotateMinutes",
            TimeUnit.DAYS.toMinutes(1));
    /**
     * The default file size after which to try and rotate the log file used by {@link #createListener()}.
     * A value of {@code -1L} disables rotation based on file size.
     * This value is controlled by the system property {@code hudson.model.AsyncPeriodicWork.logRotateSize}.
     * Each individual AsyncPeriodicWork can also have a per-extension override using the system property
     * based on their fully qualified class name with {@code .logRotateSize} appended.
     *
     * @since 1.651
     */
    private static final long LOG_ROTATE_SIZE = SystemProperties.getLong(AsyncPeriodicWork.class.getName() + ".logRotateSize", 10485760L);
    /**
     * The number of milliseconds (since startup or previous rotation) after which to try and rotate the log file.
     *
     * @since 1.651
     */
    private final long logRotateMillis;
    /**
     * {@code -1L} disabled file size based log rotation, otherwise when starting an {@link #execute(TaskListener)},
     * if the log file size is above this number of bytes then the log file will be rotated beforehand.
     *
     * @since 1.651
     */
    private final long logRotateSize;
    /**
     * The last time the log files were rotated. On start-up this will be {@link Long#MIN_VALUE} to ensure that the
     * logs are always rotated every time Jenkins starts up to make it easier to correlate events with the main log.
     *
     * @since 1.651
     */
    private long lastRotateMillis = Long.MIN_VALUE;
    /**
     * Human readable name of the work.
     */
    public final String name;

    private Thread thread;

    private final AtomicBoolean pending = new AtomicBoolean(false);

    protected AsyncPeriodicWork(String name) {
        this.name = name;
        this.logRotateMillis = TimeUnit.MINUTES.toMillis(
                SystemProperties.getLong(getClass().getName() + ".logRotateMinutes", LOG_ROTATE_MINUTES));
        this.logRotateSize = SystemProperties.getLong(getClass().getName() + ".logRotateSize", LOG_ROTATE_SIZE);
    }

    /**
     * Schedules this periodic work now in a new thread, if one isn't already running.
     */
    @Override
    @SuppressWarnings("deprecation") // in this case we really want to use PeriodicWork.logger since it reports the impl class
    public final void doRun() {
        try {
            if (thread != null && thread.isAlive()) {
                if (queueIfAlreadyRunning()) {
                    logger.log(this.getSlowLoggingLevel(), "Scheduling another run of {0} since it is already running", name);
                    pending.set(true);
                } else {
                    logger.log(this.getSlowLoggingLevel(), "{0} thread is still running. Execution aborted.", name);
                }
                return;
            }
            thread = new Thread(() -> {
                logger.log(Level.FINE, "Started {0}", name);
                long startTime = System.currentTimeMillis();
                long stopTime;

                LazyTaskListener l = new LazyTaskListener(this::createListener, String.format("Started at %tc", new Date(startTime)));
                try {
                    try (ACLContext ctx = ACL.as2(ACL.SYSTEM2)) {
                        execute(l);
                    }
                } catch (IOException e) {
                    Functions.printStackTrace(e, l.fatalError(e.getMessage()));
                } catch (InterruptedException e) {
                    Functions.printStackTrace(e, l.fatalError("aborted"));
                } finally {
                    stopTime = System.currentTimeMillis();
                    l.close(String.format("Finished at %tc. %dms", new Date(stopTime), stopTime - startTime));
                }

                logger.log(Level.FINE, "Finished {0}. {1,number} ms",
                        new Object[]{name, stopTime - startTime});
                thread = null;
                if (pending.getAndSet(false)) {
                    logger.log(this.getSlowLoggingLevel(), "An execution of {0} was requested while it was running, scheduling another run now", name);
                    doRun();
                }
            }, name + " thread");
            thread.start();
        } catch (Throwable t) {
            LogRecord lr = new LogRecord(this.getErrorLoggingLevel(), "{0} thread failed with error");
            lr.setThrown(t);
            lr.setParameters(new Object[]{name});
            logger.log(lr);
        }
    }

    static final class LazyTaskListener implements TaskListener {

        private final Supplier<StreamTaskListener> supplier;
        private final String openingMessage;
        private StreamTaskListener delegate;

        LazyTaskListener(Supplier<StreamTaskListener> supplier, String openingMessage) {
            this.supplier = supplier;
            this.openingMessage = openingMessage;
        }

        @Override
        public synchronized PrintStream getLogger() {
            if (delegate == null) {
                delegate = supplier.get();
                delegate.getLogger().println(openingMessage);
            }
            return delegate.getLogger();
        }

        synchronized void close(String closingMessage) {
            if (delegate != null) {
                try {
                    delegate.getLogger().println(closingMessage);
                } finally {
                    delegate.closeQuietly();
                }
            }
        }

    }

    protected StreamTaskListener createListener() {
        File f = getLogFile();
        if (!f.getParentFile().isDirectory()) {
            if (!f.getParentFile().mkdirs()) {
                logger.log(getErrorLoggingLevel(), "Could not create directory {0}", f.getParentFile());
            }
        }
        if (f.isFile()) {
            if (lastRotateMillis + logRotateMillis < System.currentTimeMillis()
                    || (logRotateSize > 0 && f.length() > logRotateSize)) {
                lastRotateMillis = System.currentTimeMillis();
                File prev = null;
                for (int i = 5; i >= 0; i--) {
                    File curr = i == 0 ? f : new File(f.getParentFile(), f.getName() + "." + i);
                    if (curr.isFile()) {
                        if (prev != null && !prev.exists()) {
                            if (!curr.renameTo(prev)) {
                                logger.log(getErrorLoggingLevel(), "Could not rotate log files {0} to {1}",
                                        new Object[]{curr, prev});
                            }
                        } else {
                            if (!curr.delete()) {
                                logger.log(getErrorLoggingLevel(), "Could not delete log file {0} to enable rotation",
                                        curr);
                            }
                        }
                    }
                    prev = curr;
                }
            }
        } else {
            lastRotateMillis = System.currentTimeMillis();
            // migrate old log files the first time we start-up
            File oldFile = new File(Jenkins.get().getRootDir(), f.getName());
            if (oldFile.isFile()) {
                File newFile = new File(f.getParentFile(), f.getName() + ".1");
                if (!newFile.isFile()) {
                    // if there has never been rotation then this is the first time
                    if (oldFile.renameTo(newFile)) {
                        logger.log(getNormalLoggingLevel(), "Moved {0} to {1}", new Object[]{oldFile, newFile});
                    } else {
                        logger.log(getErrorLoggingLevel(), "Could not move {0} to {1}",
                                new Object[]{oldFile, newFile});
                    }
                }
            }
        }
        try {
            return new StreamTaskListener(f, true, null);
        } catch (IOException e) {
            throw new Error(e);
        }
    }

    /**
     * Determines the log file that records the result of this task.
     */
    protected File getLogFile() {
        return new File(getLogsRoot(), "/tasks/" + name + ".log");
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

    /**
     * @return true if a new run should be queued if it is already running while called.
     * @since TODO
     */
    protected boolean queueIfAlreadyRunning() {
        return false;
    }
}
