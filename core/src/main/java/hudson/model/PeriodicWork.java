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

import hudson.ExtensionListListener;
import hudson.init.Initializer;
import hudson.triggers.SafeTimerTask;
import hudson.ExtensionPoint;
import hudson.Extension;
import hudson.ExtensionList;
import jenkins.util.Timer;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.Random;

import static hudson.init.InitMilestone.JOB_CONFIG_ADAPTED;
import hudson.triggers.Trigger;

/**
 * Extension point to perform a periodic task in Hudson (through {@link Timer}.)
 *
 * <p>
 * This extension point is useful if your plugin needs to perform some work in the background periodically
 * (for example, monitoring, batch processing, garbage collection, etc.)
 *
 * <p>
 * Put {@link Extension} on your class to have it picked up and registered automatically, or
 * manually insert this to {@link Trigger#timer}.
 *
 * <p>
 * This class is designed to run a short task. Implementations whose periodic work takes a long time
 * to run should extend from {@link AsyncPeriodicWork} instead. 
 *
 * @author Kohsuke Kawaguchi
 * @see AsyncPeriodicWork
 */
public abstract class PeriodicWork extends SafeTimerTask implements ExtensionPoint {

    /** @deprecated Use your own logger, or send messages to the logger in {@link AsyncPeriodicWork#execute}. */
    @SuppressWarnings("NonConstantLogger")
    @Deprecated
    protected final Logger logger = Logger.getLogger(getClass().getName());

    /**
     * Gets the number of milliseconds between successive executions.
     *
     * <p>
     * Hudson calls this method once to set up a recurring timer, instead of
     * calling this each time after the previous execution completed. So this class cannot be
     * used to implement a non-regular recurring timer.
     *
     * <p>
     * IOW, the method should always return the same value.
     */
    public abstract long getRecurrencePeriod();

    /**
     * Gets the number of milliseconds til the first execution.
     *
     * <p>
     * By default it chooses the value randomly between 0 and {@link #getRecurrencePeriod()}
     */
    public long getInitialDelay() {
        long l = RANDOM.nextLong();
        // Math.abs(Long.MIN_VALUE)==Long.MIN_VALUE!
        if (l==Long.MIN_VALUE)
            l++;
        return Math.abs(l)%getRecurrencePeriod();
    }

    /**
     * Returns all the registered {@link PeriodicWork}s.
     */
    public static ExtensionList<PeriodicWork> all() {
        return ExtensionList.lookup(PeriodicWork.class);
    }

    @Initializer(after= JOB_CONFIG_ADAPTED)
    public static void init() {
        // start all PeriodicWorks
        ExtensionList<PeriodicWork> extensionList = all();
        extensionList.addListener(new PeriodicWorkExtensionListListener(extensionList));
        for (PeriodicWork p : extensionList) {
            schedulePeriodicWork(p);
        }
    }

    private static void schedulePeriodicWork(PeriodicWork p) {
        Timer.get().scheduleAtFixedRate(p, p.getInitialDelay(), p.getRecurrencePeriod(), TimeUnit.MILLISECONDS);
    }

    // time constants
    protected static final long MIN = 1000*60;
    protected static final long HOUR =60*MIN;
    protected static final long DAY = 24*HOUR;

    private static final Random RANDOM = new Random();

    /**
     * ExtensionListener that will kick off any new AperiodWork extensions from plugins that are dynamically
     * loaded.
     */
    private static class PeriodicWorkExtensionListListener extends ExtensionListListener {

        private final Set<PeriodicWork> registered = new HashSet<>();

        PeriodicWorkExtensionListListener(ExtensionList<PeriodicWork> initiallyRegistered) {
            registered.addAll(initiallyRegistered);
        }

        @Override
        public void onChange() {
            synchronized (registered) {
                for (PeriodicWork p : PeriodicWork.all()) {
                    // it is possibly to programatically remove Extensions but that is rarely used.
                    if (!registered.contains(p)) {
                        schedulePeriodicWork(p);
                        registered.add(p);
                    }
                }
            }
        }
    }
}
