package hudson.triggers;

import antlr.ANTLRException;
import hudson.ExtensionPoint;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.Build;
import hudson.model.Describable;
import hudson.model.FingerprintCleanupThread;
import hudson.model.Hudson;
import hudson.model.Project;
import hudson.model.WorkspaceCleanupThread;
import hudson.model.Item;
import hudson.scheduler.CronTab;
import hudson.scheduler.CronTabList;

import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Triggers a {@link Build}.
 *
 * <p>
 * To register a custom {@link Trigger} from a plugin,
 * add it to {@link Triggers#TRIGGERS}.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class Trigger<J extends Item> implements Describable<Trigger<?>>, ExtensionPoint {

    /**
     * Called when a {@link Trigger} is loaded into memory and started.
     *
     * @param project
     *      given so that the persisted form of this object won't have to have a back pointer.
     * @param newInstance
     *      True if this is a newly created trigger first attached to the {@link Project}.
     *      False if this is invoked for a {@link Project} loaded from disk.
     */
    public void start(J project, boolean newInstance) {
        this.job = project;
    }

    /**
     * Executes the triggered task.
     *
     * This method is invoked when {@link #Trigger(String)} is used
     * to create an instance, and the crontab matches the current time.
     */
    protected void run() {}

    /**
     * Called before a {@link Trigger} is removed.
     * Under some circumstances, this may be invoked more than once for
     * a given {@link Trigger}, so be prepared for that.
     *
     * <p>
     * When the configuration is changed for a project, all triggers
     * are removed once and then added back.
     */
    public void stop() {}

    /**
     * Returns an action object if this {@link Trigger} has an action
     * to contribute to a {@link Project}.
     */
    public Action getProjectAction() {
        return null;
    }

    public abstract TriggerDescriptor getDescriptor();



    protected final String spec;
    protected transient CronTabList tabs;
    protected transient J job;

    /**
     * Creates a new {@link Trigger} that gets {@link #run() run}
     * periodically. This is useful when your trigger does
     * some polling work.
     */
    protected Trigger(String cronTabSpec) throws ANTLRException {
        this.spec = cronTabSpec;
        this.tabs = CronTabList.create(cronTabSpec);
    }

    /**
     * Creates a new {@link Trigger} without using cron.
     */
    protected Trigger() {
        this.spec = "";
        this.tabs = new CronTabList(Collections.<CronTab>emptyList());
    }

    /**
     * Gets the crontab specification.
     *
     * If you are not using cron service, just ignore it.
     */
    public final String getSpec() {
        return spec;
    }

    private Object readResolve() throws ObjectStreamException {
        try {
            tabs = CronTabList.create(spec);
        } catch (ANTLRException e) {
            InvalidObjectException x = new InvalidObjectException(e.getMessage());
            x.initCause(e);
            throw x;
        }
        return this;
    }


    /**
     * Runs every minute to check {@link TimerTrigger} and schedules build.
     */
    private static class Cron extends TimerTask {
        private final Calendar cal = new GregorianCalendar();

        public void run() {
            LOGGER.fine("cron checking "+cal.getTime().toLocaleString());

            try {
                Hudson inst = Hudson.getInstance();
                for (AbstractProject<?,?> p : inst.getAllItems(AbstractProject.class)) {
                    for (Trigger t : p.getTriggers().values()) {
                        LOGGER.fine("cron checking "+p.getName());
                        if(t.tabs.check(cal)) {
                            LOGGER.fine("cron triggered "+p.getName());
                            t.run();
                        }
                    }
                }
            } catch (Throwable e) {
                LOGGER.log(Level.WARNING,"Cron thread throw an exception",e);
                // bug in the code. Don't let the thread die.
                e.printStackTrace();
            }

            cal.add(Calendar.MINUTE,1);
        }
    }

    private static final Logger LOGGER = Logger.getLogger(Trigger.class.getName());

    /**
     * This timer is available for all the components inside Hudson to schedule
     * some work.
     */
    public static final Timer timer = new Timer("Hudson cron thread");

    public static void init() {
        timer.scheduleAtFixedRate(new Cron(), 1000*60, 1000*60/*every minute*/);

        // clean up fingerprint once a day
        long HOUR = 1000*60*60;
        long DAY = HOUR*24;
        timer.scheduleAtFixedRate(new FingerprintCleanupThread(),DAY,DAY);
        timer.scheduleAtFixedRate(new WorkspaceCleanupThread(),DAY+4*HOUR,DAY);
    }
}
