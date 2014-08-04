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

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.init.Initializer;
import hudson.triggers.SafeTimerTask;
import jenkins.model.Jenkins;
import jenkins.util.Timer;

import java.util.Random;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static hudson.init.InitMilestone.JOB_LOADED;


/**
 * Extension point which allows scheduling a task with variable interval. Interval in evaluated every time before next
 * task is scheduled by calling {@link #getRecurrencePeriod()}. Task to be scheduled is obtain by calling {@link #getNewInstance()}.
 * 
 * <p>
 * This class is similar to {@link PeriodicWork}. The main difference is in re-evaluating delay interval every time.
 * See {@link PeriodicWork} for details. Analog of {@link AsyncPeriodicWork} is {@link AsyncAperiodicWork}.
 * 
 * @author vjuranek
 * @since 1.410
 */
public abstract class AperiodicWork extends SafeTimerTask implements ExtensionPoint {
	
	protected final Logger logger = Logger.getLogger(getClass().getName());
	
    /**
     * Gets the number of milliseconds between successive executions.
     *
     * <p>
     * Jenkins calls this method every time the timer task is scheduled. 
     *
     */
    public abstract long getRecurrencePeriod();

    /**
     * Gets new instance of task to be executed. Method should return new instance each time, as there no check, if previously 
     * scheduled task already finished. Returning same instance could lead to throwing {@link IllegalStateException} (especially
     * in case of {@link AsyncAperiodicWork}) and therefore scheduling of next tasks will be broken.
     * 
     * @return AperiodicWork - timer task instance to be executed
     */
    public abstract AperiodicWork getNewInstance();
    
    /**
     * Gets the number of milliseconds till the first execution.
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

    @Override
    public final void doRun() throws Exception{
    	doAperiodicRun();
        Timer.get().schedule(getNewInstance(), getRecurrencePeriod(), TimeUnit.MILLISECONDS);
    }

    @Initializer(after= JOB_LOADED)
    public static void init() {
        // start all AperidocWorks
        for (AperiodicWork p : AperiodicWork.all()) {
            Timer.get().schedule(p, p.getInitialDelay(), TimeUnit.MILLISECONDS);
        }
    }

    protected abstract void doAperiodicRun();
    
    /**
     * Returns all the registered {@link AperiodicWork}s.
     */
    public static ExtensionList<AperiodicWork> all() {
        return ExtensionList.lookup(AperiodicWork.class);
    }

    private static final Random RANDOM = new Random();
}
