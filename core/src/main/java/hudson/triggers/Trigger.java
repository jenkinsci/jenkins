/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Brian Westrich, Jean-Baptiste Quenot, Stephen Connolly, Tom Huybrechts
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

import antlr.ANTLRException;
import hudson.DependencyRunner;
import hudson.DependencyRunner.ProjectRunnable;
import hudson.ExtensionPoint;
import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.Build;
import hudson.model.ComputerSet;
import hudson.model.Describable;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.model.Project;
import hudson.model.PeriodicWork;
import hudson.model.TopLevelItem;
import hudson.model.TopLevelItemDescriptor;
import hudson.scheduler.CronTab;
import hudson.scheduler.CronTabList;
import hudson.util.DoubleLaunchChecker;

import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.Timer;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Triggers a {@link Build}.
 *
 * <p>
 * To register a custom {@link Trigger} from a plugin,
 * put {@link Extension} on your {@link TriggerDescriptor} class.
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
    public void run() {}

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

    public TriggerDescriptor getDescriptor() {
        return (TriggerDescriptor)Hudson.getInstance().getDescriptor(getClass());
    }



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

    protected Object readResolve() throws ObjectStreamException {
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
    @Extension
    public static class Cron extends PeriodicWork {
        private final Calendar cal = new GregorianCalendar();

        public long getRecurrencePeriod() {
            return MIN;
        }

        public void doRun() {
            while(new Date().getTime()-cal.getTimeInMillis()>1000) {
                LOGGER.fine("cron checking "+cal.getTime().toLocaleString());

                try {
                    checkTriggers(cal);
                } catch (Throwable e) {
                    LOGGER.log(Level.WARNING,"Cron thread throw an exception",e);
                    // bug in the code. Don't let the thread die.
                    e.printStackTrace();
                }

                cal.add(Calendar.MINUTE,1);
            }
        }
    }

    private static Future previousSynchronousPolling;

    public static void checkTriggers(final Calendar cal) {
        Hudson inst = Hudson.getInstance();

        // Are we using synchronous polling?
        SCMTrigger.DescriptorImpl scmd = inst.getDescriptorByType(SCMTrigger.DescriptorImpl.class);
        if (scmd.synchronousPolling) {
            LOGGER.fine("using synchronous polling");

            // Check that previous synchronous polling job is done to prevent piling up too many jobs
            if (previousSynchronousPolling == null || previousSynchronousPolling.isDone()) {
                // Process SCMTriggers in the order of dependencies. Note that the crontab spec expressed per-project is
                // ignored, only the global setting is honored. The polling job is submitted only if the previous job has
                // terminated.
                // FIXME allow to set a global crontab spec
                previousSynchronousPolling = scmd.getExecutor().submit(new DependencyRunner(new ProjectRunnable() {
                    public void run(AbstractProject p) {
                        for (Trigger t : (Collection<Trigger>) p.getTriggers().values()) {
                            if (t instanceof SCMTrigger) {
                                LOGGER.fine("synchronously triggering SCMTrigger for project " + t.job.getName());
                                t.run();
                            }
                        }
                    }
                }));
            } else {
                LOGGER.fine("synchronous polling has detected unfinished jobs, will not trigger additional jobs.");
            }
        }

        // Process all triggers, except SCMTriggers when synchronousPolling is set
        for (AbstractProject<?,?> p : inst.getAllItems(AbstractProject.class)) {
            for (Trigger t : p.getTriggers().values()) {
                if (! (t instanceof SCMTrigger && scmd.synchronousPolling)) {
                    LOGGER.fine("cron checking "+p.getName());

                    if (t.tabs.check(cal)) {
                        LOGGER.config("cron triggered "+p.getName());
                        try {
                            t.run();
                        } catch (Throwable e) {
                            // t.run() is a plugin, and some of them throw RuntimeException and other things.
                            // don't let that cancel the polling activity. report and move on.
                            LOGGER.log(Level.WARNING, t.getClass().getName()+".run() failed for "+p.getName(),e);
                        }
                    }
                }
            }
        }
    }

    private static final Logger LOGGER = Logger.getLogger(Trigger.class.getName());

    /**
     * This timer is available for all the components inside Hudson to schedule
     * some work.
     *
     * Initialized and cleaned up by {@link Hudson}, but value kept here for compatibility.
     *
     * If plugins want to run periodic jobs, they should implement {@link PeriodicWork}.
     */
    public static Timer timer;

    public static void init() {
        new DoubleLaunchChecker().schedule();

        // start all PeridocWorks
        for(PeriodicWork p : PeriodicWork.all())
            timer.scheduleAtFixedRate(p,p.getInitialDelay(),p.getRecurrencePeriod());

        // start monitoring nodes, although there's no hurry.
        timer.schedule(new SafeTimerTask() {
            public void doRun() {
                ComputerSet.initialize();
            }
        }, 1000*10);
    }

    /**
     * Returns all the registered {@link Trigger} descriptors.
     */
    public static DescriptorExtensionList<Trigger<?>,TriggerDescriptor> all() {
        return (DescriptorExtensionList)Hudson.getInstance().getDescriptorList(Trigger.class);
    }

    /**
     * Returns a subset of {@link TriggerDescriptor}s that applys to the given item.
     */
    public static List<TriggerDescriptor> for_(Item i) {
        List<TriggerDescriptor> r = new ArrayList<TriggerDescriptor>();
        for (TriggerDescriptor t : all()) {
            if(!t.isApplicable(i))  continue;

            if (i instanceof TopLevelItem) {// ugly
                TopLevelItemDescriptor tld = ((TopLevelItem) i).getDescriptor();
                // tld shouldn't be really null in contract, but we often write test Describables that
                // doesn't have a Descriptor.
                if(tld!=null && !tld.isApplicable(t))    continue;
            }

            r.add(t);
        }
        return r;
    }
}
