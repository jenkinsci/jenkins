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
