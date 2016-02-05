/*
 * The MIT License
 *
 * Copyright (c) 2011, Vojtech Juranek
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
package hudson.model;

import hudson.security.ACL;
import hudson.util.StreamTaskListener;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

import jenkins.model.Jenkins;

/**
 * {@link AperiodicWork} that takes a long time to run. Similar to {@link AsyncPeriodicWork}, see {@link AsyncPeriodicWork} for
 * details and {@link AperiodicWork} for differences between {@link AperiodicWork} and {@link PeriodicWork}. 
 * 
 * @author vjuranek
 * @since 1.410
 */
public abstract class AsyncAperiodicWork extends AperiodicWork {
	
	/**
     * Name of the work.
     */
    public final String name;

    private Thread thread;

    protected AsyncAperiodicWork(String name) {
        this.name = name;
    }

    /**
     * Schedules this periodic work now in a new thread, if one isn't already running.
     */
    @Override
    public final void doAperiodicRun() {
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
