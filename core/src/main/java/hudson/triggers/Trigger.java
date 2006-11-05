package hudson.triggers;

import antlr.ANTLRException;
import hudson.model.Action;
import hudson.model.Build;
import hudson.model.Describable;
import hudson.model.FingerprintCleanupThread;
import hudson.model.Hudson;
import hudson.model.Project;
import hudson.model.WorkspaceCleanupThread;
import hudson.scheduler.CronTabList;
import hudson.ExtensionPoint;
import hudson.tasks.BuildStep;

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
public abstract class Trigger implements Describable<Trigger>, ExtensionPoint {

    /**
     * Called when a {@link Trigger} is loaded into memory and started.
     *
     * @param project
     *      given so that the persisted form of this object won't have to have a back pointer.
     */
    public void start(Project project) {
        this.project = project;
    }

    /**
     * Executes the triggered task.
     *
     * This method is invoked when the crontab matches the current time.
     */
    protected abstract void run();

    /**
     * Called before a {@link Trigger} is removed.
     * Under some circumstances, this may be invoked more than once for
     * a given {@link Trigger}, so be prepared for that.
     */
    public void stop() {}

    /**
     * Returns an action object if this {@link Trigger} has an action
     * to contribute to a {@link Project}.
     */
    public Action getProjectAction() {
        return null;
    }



    protected final String spec;
    protected transient CronTabList tabs;
    protected transient Project project;

    protected Trigger(String cronTabSpec) throws ANTLRException {
        this.spec = cronTabSpec;
        this.tabs = CronTabList.create(cronTabSpec);
    }

    protected Trigger() {
        this.spec = "";
        this.tabs = new CronTabList(Collections.EMPTY_LIST);
    }

    public String getSpec() {
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
                for (Project p : inst.getProjects()) {
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

    public static final Timer timer = new Timer(); // "Hudson cron thread"); -- this is a new constructor since 1.5

    public static void init() {
        timer.scheduleAtFixedRate(new Cron(), 1000*60, 1000*60/*every minute*/);

        // clean up fingerprint once a day
        long HOUR = 1000*60*60;
        long DAY = HOUR*24;
        timer.scheduleAtFixedRate(new FingerprintCleanupThread(),DAY,DAY);
        timer.scheduleAtFixedRate(new WorkspaceCleanupThread(),DAY+4*HOUR,DAY);
    }
}
