/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Brian Westrich, Jean-Baptiste Quenot, Stephen Connolly, Tom Huybrechts
 *               2015 Kanstantsin Shautsou
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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.DependencyRunner;
import hudson.DependencyRunner.ProjectRunnable;
import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.ExtensionPoint;
import hudson.RestrictedSince;
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.Build;
import hudson.model.Describable;
import hudson.model.Item;
import hudson.model.Items;
import hudson.model.PeriodicWork;
import hudson.model.Project;
import hudson.model.TopLevelItem;
import hudson.model.TopLevelItemDescriptor;
import hudson.scheduler.CronTabList;
import hudson.scheduler.Hash;
import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Timer;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.triggers.TriggeredItem;
import jenkins.util.SystemProperties;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Triggers a {@link Build}.
 *
 * <p>
 * To register a custom {@link Trigger} from a plugin,
 * put {@link Extension} on your {@link TriggerDescriptor} class.
 *
 * @author Kohsuke Kawaguchi
 * @see TriggeredItem
 */
public abstract class Trigger<J extends Item> implements Describable<Trigger<?>>, ExtensionPoint {

    /**
     * Called when a {@link Trigger} is loaded into memory and started.
     *
     * @param project
     *      given so that the persisted form of this object won't have to have a back pointer.
     * @param newInstance
     *      True if this may be a newly created trigger first attached to the {@link Project} (generally if the project is being created or configured).
     *      False if this is invoked for a {@link Project} loaded from disk.
     * @see Items#currentlyUpdatingByXml
     */
    public void start(J project, boolean newInstance) {
        LOGGER.finer(() -> "Starting " + this + " on " + project);
        this.job = project;

        try { // reparse the tabs with the job as the hash
            if (spec != null) {
                this.tabs = CronTabList.create(spec, Hash.from(project.getFullName()));
            } else {
                LOGGER.log(Level.WARNING, "The job {0} has a null crontab spec which is incorrect", job.getFullName());
            }
        } catch (IllegalArgumentException e) {
            // this shouldn't fail because we've already parsed stuff in the constructor,
            // so if it fails, use whatever 'tabs' that we already have.
            LOGGER.log(Level.WARNING, String.format("Failed to parse crontab spec %s in job %s", spec, project.getFullName()), e);
        }
    }

    /**
     * Executes the triggered task.
     *
     * This method is invoked when {@link #Trigger(String)} is used
     * to create an instance, and the crontab matches the current time.
     * <p>
     * Maybe run even before {@link #start(hudson.model.Item, boolean)}, prepare for it.
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
     *
     * @deprecated as of 1.341
     *      Use {@link #getProjectActions()} instead.
     */
    @Deprecated
    public Action getProjectAction() {
        return null;
    }

    /**
     * {@link Action}s to be displayed in the job page.
     *
     * @return
     *      can be empty but never null
     * @since 1.341
     */
    public Collection<? extends Action> getProjectActions() {
        // delegate to getJobAction (singular) for backward compatible behavior
        Action a = getProjectAction();
        if (a == null)    return Collections.emptyList();
        return List.of(a);
    }

    @Override
    public TriggerDescriptor getDescriptor() {
        return (TriggerDescriptor) Jenkins.get().getDescriptorOrDie(getClass());
    }



    protected final String spec;
    protected transient CronTabList tabs;
    @CheckForNull
    protected transient J job;

    /**
     * Creates a new {@link Trigger} that gets {@link #run() run}
     * periodically. This is useful when your trigger does
     * some polling work.
     *
     * @param cronTabSpec the crontab entry to be parsed
     * @throws IllegalArgumentException if the crontab entry cannot be parsed
     */
    protected Trigger(@NonNull String cronTabSpec) {
        this.spec = cronTabSpec;
        this.tabs = CronTabList.create(cronTabSpec);
    }

    /**
     * Creates a new {@link Trigger} without using cron.
     */
    protected Trigger() {
        this.spec = "";
        this.tabs = new CronTabList(Collections.emptyList());
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
        } catch (IllegalArgumentException e) {
            InvalidObjectException x = new InvalidObjectException(e.getMessage());
            x.initCause(e);
            throw x;
        }
        return this;
    }

    @Override
    public String toString() {
        return super.toString() + "[" + spec + "]";
    }

    /**
     * Runs every minute to check {@link TimerTrigger} and schedules build.
     */
    @Extension @Symbol("cron")
    public static class Cron extends PeriodicWork {
        private final Calendar cal = new GregorianCalendar();

        public Cron() {
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
        }

        @Override
        public long getRecurrencePeriod() {
            return MIN;
        }

        @Override
        public long getInitialDelay() {
            return MIN - TimeUnit.SECONDS.toMillis(Calendar.getInstance().get(Calendar.SECOND));
        }

        @Override
        public void doRun() {
            while (new Date().getTime() >= cal.getTimeInMillis()) {
                LOGGER.log(Level.FINE, "cron checking {0}", cal.getTime());
                try {
                    checkTriggers(cal);
                } catch (Throwable e) {
                    LOGGER.log(Level.WARNING, "Cron thread throw an exception", e);
                    // SafeTimerTask.run would also catch this, but be sure to increment cal too.
                }

                cal.add(Calendar.MINUTE, 1);
            }
        }
    }

    private static Future previousSynchronousPolling;

    public static void checkTriggers(final Calendar cal) {
        Jenkins inst = Jenkins.get();

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
                    @Override
                    public void run(AbstractProject p) {
                        for (Trigger t : (Collection<Trigger>) p.getTriggers().values()) {
                            if (t instanceof SCMTrigger) {
                                if (t.job != null) {
                                    LOGGER.fine("synchronously triggering SCMTrigger for project " + t.job.getName());
                                } else {
                                    LOGGER.fine("synchronously triggering SCMTrigger for unknown project");
                                }
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
        for (TriggeredItem p : inst.allItems(TriggeredItem.class)) {
            LOGGER.finer(() -> "considering " + p);
            for (Trigger t : p.getTriggers().values()) {
                LOGGER.finer(() -> "found trigger " + t);
                if (!(p instanceof AbstractProject && t instanceof SCMTrigger && scmd.synchronousPolling)) {
                    if (t != null && t.spec != null && t.tabs != null) {
                        LOGGER.log(Level.FINE, "cron checking {0} with spec ‘{1}’", new Object[]{p, t.spec.trim()});

                        if (t.tabs.check(cal)) {
                            LOGGER.log(Level.CONFIG, "cron triggered {0}", p);
                            try {
                                long begin_time = System.currentTimeMillis();
                                if (t.job == null) {
                                    LOGGER.fine(() -> t + " not yet started on " + p + " but trying to run anyway");
                                }
                                t.run();
                                long end_time = System.currentTimeMillis();
                                if (end_time - begin_time > CRON_THRESHOLD * 1000) {
                                    TriggerDescriptor descriptor = t.getDescriptor();
                                    String name = descriptor.getDisplayName();
                                    final String msg = String.format("Trigger '%s' triggered by '%s' (%s) spent too much time (%s) in its execution, other timers could be delayed.",
                                            name, p.getFullDisplayName(), p.getFullName(), Util.getTimeSpanString(end_time - begin_time));
                                    LOGGER.log(Level.WARNING, msg);
                                    SlowTriggerAdminMonitor.getInstance().report(descriptor.getClass(), p.getFullName(), end_time - begin_time);
                                }
                            } catch (Throwable e) {
                                // t.run() is a plugin, and some of them throw RuntimeException and other things.
                                // don't let that cancel the polling activity. report and move on.
                                LOGGER.log(Level.WARNING, t.getClass().getName() + ".run() failed for " + p, e);
                            }
                        } else {
                            LOGGER.log(Level.FINER, "did not trigger {0}", p);
                        }
                    } else {
                        LOGGER.log(Level.WARNING, "The job {0} has a syntactically incorrect config and is missing the cron spec for a trigger", p.getFullName());
                    }
                }
            }
        }
    }

    /**
     * Used to be milliseconds, now is seconds since Jenkins 2.289.
     */
    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "for script console")
    @Restricted(NoExternalUse.class)
    @RestrictedSince("2.289")
    public static /* non-final for Groovy */ long CRON_THRESHOLD = SystemProperties.getLong(Trigger.class.getName() + ".CRON_THRESHOLD", 30L); // Default threshold 30s

    private static final Logger LOGGER = Logger.getLogger(Trigger.class.getName());

    /**
     * This timer is available for all the components inside Hudson to schedule
     * some work.
     *
     * Initialized and cleaned up by {@link jenkins.model.Jenkins}, but value kept here for compatibility.
     *
     * If plugins want to run periodic jobs, they should implement {@link PeriodicWork}.
     *
     * @deprecated Use {@link jenkins.util.Timer#get()} instead.
     */
    @SuppressFBWarnings(value = "MS_CANNOT_BE_FINAL", justification = "for backward compatibility")
    @Deprecated
    public static @CheckForNull Timer timer;

    /**
     * Returns all the registered {@link Trigger} descriptors.
     */
    public static DescriptorExtensionList<Trigger<?>, TriggerDescriptor> all() {
        return (DescriptorExtensionList) Jenkins.get().getDescriptorList(Trigger.class);
    }

    /**
     * Returns a subset of {@link TriggerDescriptor}s that applies to the given item.
     */
    public static List<TriggerDescriptor> for_(Item i) {
        List<TriggerDescriptor> r = new ArrayList<>();
        for (TriggerDescriptor t : all()) {
            if (!t.isApplicable(i))  continue;

            if (i instanceof TopLevelItem) { // ugly
                TopLevelItemDescriptor tld = ((TopLevelItem) i).getDescriptor();
                // tld shouldn't be really null in contract, but we often write test Describables that
                // doesn't have a Descriptor.
                if (tld != null && !tld.isApplicable(t))    continue;
            }

            r.add(t);
        }
        return r;
    }
}
