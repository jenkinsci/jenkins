package hudson.model;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.triggers.SafeTimerTask;
import hudson.triggers.Trigger;

import java.util.Random;
import java.util.logging.Logger;



/**
 * Extension point which allows scheduling a task with variable interval. Interval in evaluated every time before next
 * task is scheduled by calling {@link #getRecurrencePeriod()}. Task to be scheduled is obtain by calling {@link #getNewInstance()}.
 * 
 * <p>
 * This class is similar to {@link PeriodicWork}. The main difference is in re-evaluating delay interval every time.
 * See {@link PeriodicWork} for details. Analog of {@link AsyncPeriodicWork} is {@link AsyncAperiodicWork}.
 * 
 * @author vjuranek
 *
 */
public abstract class AperiodicWork extends SafeTimerTask implements ExtensionPoint{
	
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
        return Math.abs(new Random().nextLong())%getRecurrencePeriod();
    }

    @Override
    public final void doRun() throws Exception{
    	doAperiodicRun();
    	Trigger.timer.schedule(getNewInstance(), getRecurrencePeriod());
    }
    
    protected abstract void doAperiodicRun();
    
    /**
     * Returns all the registered {@link AperiodicWork}s.
     */
    public static ExtensionList<AperiodicWork> all() {
        return Hudson.getInstance().getExtensionList(AperiodicWork.class);
    }

}
