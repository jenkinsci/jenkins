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

package hudson.triggers;

import hudson.model.AperiodicWork;
import hudson.model.AsyncAperiodicWork;
import hudson.model.AsyncPeriodicWork;
import hudson.model.PeriodicWork;
import hudson.security.ACL;
import hudson.security.ACLContext;
import java.io.File;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.util.SystemProperties;
import jenkins.util.Timer;

/**
 * Wrapper so that a fatal error in {@link TimerTask} will not terminate the timer.
 *
 * <p>
 * {@link Timer#get} is a shared timer instance that can be used inside Jenkins to schedule recurring work.
 * But the usual usage is automatic via {@link PeriodicWork} or {@link AperiodicWork}.
 * @author Kohsuke Kawaguchi
 * @since 1.124
 */
public abstract class SafeTimerTask extends TimerTask {

    /**
     * Lambda-friendly means of creating a task.
     * @since 2.216
     */
    public static SafeTimerTask of(ExceptionRunnable r) {
        return new SafeTimerTask() {
            @Override
            protected void doRun() throws Exception {
                r.run();
            }
        };
    }
    /**
     * @see #of
     * @since 2.216
     */

    @FunctionalInterface
    public interface ExceptionRunnable {
        void run() throws Exception;
    }

    /**
     * System property to change the location where (tasks) logging should be sent.
     * <p><strong>Beware: changing it while Jenkins is running gives no guarantee logs will be sent to the new location
     * until it is restarted.</strong></p>
     */
    static final String LOGS_ROOT_PATH_PROPERTY = SafeTimerTask.class.getName() + ".logsTargetDir";

    /**
     * Local marker to know if the information about using non default root directory for logs has already been logged at least once.
     * @see #LOGS_ROOT_PATH_PROPERTY
     */
    private static boolean ALREADY_LOGGED = false;

    @Override
    public final void run() {
        // background activity gets system credential,
        // just like executors get it.
        try (ACLContext ctx = ACL.as2(ACL.SYSTEM2)) {
            doRun();
        } catch (Throwable t) {
            LOGGER.log(Level.SEVERE, "Timer task " + this + " failed", t);
        }
    }

    protected abstract void doRun() throws Exception;


    /**
     * The root path that should be used to put logs related to the tasks running in Jenkins.
     *
     * @see AsyncAperiodicWork#getLogFile()
     * @see AsyncPeriodicWork#getLogFile()
     * @return the path where the logs should be put.
     * @since 2.114
     */
    public static File getLogsRoot() {
        String tagsLogsPath = SystemProperties.getString(LOGS_ROOT_PATH_PROPERTY);
        if (tagsLogsPath == null) {
            return new File(Jenkins.get().getRootDir(), "logs");
        } else {
            Level logLevel = Level.INFO;
            if (ALREADY_LOGGED) {
                logLevel = Level.FINE;
            }
            LOGGER.log(logLevel,
                       "Using non default root path for tasks logging: {0}. (Beware: no automated migration if you change or remove it again)",
                       LOGS_ROOT_PATH_PROPERTY);
            ALREADY_LOGGED = true;
            return new File(tagsLogsPath);
        }
    }

    private static final Logger LOGGER = Logger.getLogger(SafeTimerTask.class.getName());
}
