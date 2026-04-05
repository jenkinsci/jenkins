package jenkins.management;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.AbortException;
import hudson.Functions;
import hudson.Util;
import hudson.console.AnnotatedLargeText;
import hudson.model.AdministrativeMonitor;
import hudson.model.TaskListener;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.util.StreamTaskListener;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;

/**
 * Convenient partial implementation of {@link AdministrativeMonitor} that involves a background "fixing" action
 * once the user opts in for the execution of it.
 *
 * <p>
 * A subclass defines what that background fixing actually does in {@link #fix(TaskListener)}. The logging output
 * from it gets persisted, and this class provides a "/log" view that allows the administrator to monitor its progress.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class AsynchronousAdministrativeMonitor extends AdministrativeMonitor {
    /**
     * Set to non-null once the background activity starts running.
     */
    private volatile FixThread fixThread;

    /**
     * Is there an active execution process going on?
     */
    public boolean isFixingActive() {
        return fixThread != null && fixThread.isAlive();
    }

    /**
     * Used to URL-bind {@link AnnotatedLargeText}.
     */
    public AnnotatedLargeText getLogText() {
        return new AnnotatedLargeText<>(
                getLogFile(), Charset.defaultCharset(),
                !isFixingActive(), this);
    }

    /**
     * Rewrite log file.
     */
    protected File getLogFile() {
        File base = getBaseDir();
        try {
            Util.createDirectories(base.toPath());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new File(base, "log");
    }

    protected File getBaseDir() {
        return new File(Jenkins.get().getRootDir(), getClass().getName());
    }

    @Override
    public abstract String getDisplayName();

    /**
     * Starts the background fixing activity.
     *
     * @param forceRestart
     *      If true, any ongoing fixing activity gets interrupted and the new one starts right away.
     */
    protected synchronized Thread start(boolean forceRestart) {
        if (!forceRestart && isFixingActive()) {
            fixThread.interrupt();
        }

        if (forceRestart || !isFixingActive()) {
            fixThread = new FixThread();
            fixThread.start();
        }
        return fixThread;
    }

    /**
     * Run on a separate thread in the background to fix up stuff.
     */
    protected abstract void fix(TaskListener listener) throws Exception;

    protected class FixThread extends Thread {
        FixThread() {
            super(getDisplayName());
        }

        @Override
        public void run() {
            StreamTaskListener listener = null;
            try (ACLContext ctx = ACL.as2(ACL.SYSTEM2)) {
                listener = new StreamTaskListener(getLogFile());
                try {
                    doRun(listener);
                } finally {
                    listener.close();
                }
            } catch (IOException ex) {
                if (listener == null) {
                    LOGGER.log(Level.SEVERE, "Cannot create listener for " + getName(), ex);
                    //TODO: throw IllegalStateException?
                } else {
                    LOGGER.log(Level.WARNING, "Cannot close listener for " + getName(), ex);
                }
            }
         }

        /**
         * Runs the monitor and encapsulates all errors within.
         * @since 1.590
         */
        private void doRun(@NonNull TaskListener listener) {
            try {
                fix(listener);
            } catch (AbortException e) {
                listener.error(e.getMessage());
            } catch (Throwable e) {
                Functions.printStackTrace(e, listener.error(getName() + " failed"));
                LOGGER.log(Level.WARNING, getName() + " failed", e);
            }
        }
    }

    private static final Logger LOGGER = Logger.getLogger(AsynchronousAdministrativeMonitor.class.getName());
}
