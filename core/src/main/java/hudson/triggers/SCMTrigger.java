/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Brian Westrich, Jean-Baptiste Quenot, id:cactusman
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

import static java.util.logging.Level.WARNING;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.Functions;
import hudson.Util;
import hudson.console.AnnotatedLargeText;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.AdministrativeMonitor;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.Item;
import hudson.model.PersistentDescriptor;
import hudson.model.Run;
import hudson.scm.SCM;
import hudson.scm.SCMDescriptor;
import hudson.util.DaemonThreadFactory;
import hudson.util.FlushProofOutputStream;
import hudson.util.FormValidation;
import hudson.util.NamingThreadFactory;
import hudson.util.SequentialExecutionQueue;
import hudson.util.StreamTaskListener;
import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.model.RunAction2;
import jenkins.scm.SCMDecisionHandler;
import jenkins.triggers.SCMTriggerItem;
import jenkins.util.SystemProperties;
import net.sf.json.JSONObject;
import org.apache.commons.jelly.XMLOutput;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;

/**
 * {@link Trigger} that checks for SCM updates periodically.
 *
 * You can add UI elements under the SCM section by creating a
 * config.jelly or config.groovy in the resources area for
 * your class that inherits from SCMTrigger and has the
 * {@link Extension} annotation. The UI should
 * be wrapped in an f:section element to denote it.
 *
 * @author Kohsuke Kawaguchi
 */
public class SCMTrigger extends Trigger<Item> {

    private boolean ignorePostCommitHooks;

    @DataBoundConstructor
    public SCMTrigger(String scmpoll_spec) {
        super(scmpoll_spec);
    }

    /**
     * Backwards-compatibility constructor.
     *
     * @param scmpoll_spec
     *     The spec to poll with.
     * @param ignorePostCommitHooks
     *     Whether to ignore post commit hooks.
     *
     * @deprecated since 2.21
     */
    @Deprecated
    public SCMTrigger(String scmpoll_spec, boolean ignorePostCommitHooks) {
        super(scmpoll_spec);
        this.ignorePostCommitHooks = ignorePostCommitHooks;
    }

    /**
     * This trigger wants to ignore post-commit hooks.
     * <p>
     * SCM plugins must respect this and not run this trigger for post-commit notifications.
     *
     * @since 1.493
     */
    public boolean isIgnorePostCommitHooks() {
        return this.ignorePostCommitHooks;
    }

    /**
     * Data-bound setter for ignoring post commit hooks.
     *
     * @param ignorePostCommitHooks
     *     True if we should ignore post commit hooks, false otherwise.
     *
     * @since 2.22
     */
    @DataBoundSetter
    public void setIgnorePostCommitHooks(boolean ignorePostCommitHooks) {
        this.ignorePostCommitHooks = ignorePostCommitHooks;
    }

    public String getScmpoll_spec() {
        return super.getSpec();
    }

    @Override
    public void run() {
        if (job == null) {
            return;
        }

        run(null);
    }

    /**
     * Run the SCM trigger with additional build actions. Used by SubversionRepositoryStatus
     * to trigger a build at a specific revision number.
     *
     * @since 1.375
     */
    public void run(Action[] additionalActions) {
        if (job == null) {
            return;
        }

        DescriptorImpl d = getDescriptor();

        LOGGER.fine("Scheduling a polling for " + job);
        if (d.synchronousPolling) {
            LOGGER.fine("Running the trigger directly without threading, " +
                    "as it's already taken care of by Trigger.Cron");
            new Runner(additionalActions).run();
        } else {
            // schedule the polling.
            // even if we end up submitting this too many times, that's OK.
            // the real exclusion control happens inside Runner.
            LOGGER.fine("scheduling the trigger to (asynchronously) run");
            d.queue.execute(new Runner(additionalActions));
            d.clogCheck();
        }
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Override
    public Collection<? extends Action> getProjectActions() {
        if (job == null) {
            return Collections.emptyList();
        }

        return Set.of(new SCMAction());
    }

    /**
     * Returns the file that records the last/current polling activity.
     */
    public File getLogFile() {
        return new File(Objects.requireNonNull(job).getRootDir(), "scm-polling.log");
    }

    @Extension @Symbol("pollSCM")
    public static class DescriptorImpl extends TriggerDescriptor implements PersistentDescriptor {

        private static ThreadFactory threadFactory() {
            return new NamingThreadFactory(new DaemonThreadFactory(), "SCMTrigger");
        }

        /**
         * Used to control the execution of the polling tasks.
         * <p>
         * This executor implementation has a semantics suitable for polling. Namely, no two threads will try to poll the same project
         * at once, and multiple polling requests to the same job will be combined into one. Note that because executor isn't aware
         * of a potential workspace lock between a build and a polling, we may end up using executor threads unwisely --- they
         * may block.
         */
        private final transient SequentialExecutionQueue queue = new SequentialExecutionQueue(Executors.newSingleThreadExecutor(threadFactory()));

        /**
         * Whether the projects should be polled all in one go in the order of dependencies. The default behavior is
         * that each project polls for changes independently.
         */
        public boolean synchronousPolling = false;

        /**
         * Max number of threads for SCM polling.
         */
        private int maximumThreads = 10;

        private static final int THREADS_LOWER_BOUND = 5;
        private static final int THREADS_UPPER_BOUND = 100;
        private static final int THREADS_DEFAULT = 10;

        @Override
        public boolean isApplicable(Item item) {
            return SCMTriggerItem.SCMTriggerItems.asSCMTriggerItem(item) != null;
        }

        public ExecutorService getExecutor() {
            return queue.getExecutors();
        }

        /**
         * Returns true if the SCM polling thread queue has too many jobs
         * than it can handle.
         */
        public boolean isClogged() {
            return queue.isStarving(STARVATION_THRESHOLD);
        }

        /**
         * Checks if the queue is clogged, and if so,
         * activate {@link AdministrativeMonitorImpl}.
         */
        public void clogCheck() {
            AdministrativeMonitor.all().get(AdministrativeMonitorImpl.class).on = isClogged();
        }

        /**
         * Gets the snapshot of {@link Runner}s that are performing polling.
         */
        public List<Runner> getRunners() {
            return Util.filter(queue.getInProgress(), Runner.class);
        }

         // originally List<SCMedItem> but known to be used only for logging, in which case the instances are not actually cast to SCMedItem anyway
        public List<SCMTriggerItem> getItemsBeingPolled() {
            List<SCMTriggerItem> r = new ArrayList<>();
            for (Runner i : getRunners())
                r.add(i.getTarget());
            return r;
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.SCMTrigger_DisplayName();
        }

        /**
         * Gets the number of concurrent threads used for polling.
         *
         */
        public int getPollingThreadCount() {
            return maximumThreads;
        }

        /**
         * Sets the number of concurrent threads used for SCM polling and resizes the thread pool accordingly
         * @param n number of concurrent threads in the range 5..100, outside values will set the to the nearest bound
         */
        public void setPollingThreadCount(int n) {
            // fool proof
            if (n < THREADS_LOWER_BOUND) {
                n = THREADS_LOWER_BOUND;
            }
            if (n > THREADS_UPPER_BOUND) {
                n = THREADS_UPPER_BOUND;
            }

            maximumThreads = n;

            resizeThreadPool();
        }

        @Restricted(NoExternalUse.class)
        public boolean isPollingThreadCountOptionVisible() {
            if (getPollingThreadCount() != THREADS_DEFAULT) {
                // this is a user who already configured the option
                return true;
            }
            // unless you have a fair number of projects, this option is likely pointless.
            // so let's hide this option for new users to avoid confusing them
            // unless it was already changed
            int count = 0;
            // we are faster walking some items with a lazy iterator than building a list of all items just to query
            // the size. This also lets us check against SCMTriggerItem rather than AbstractProject
            for (Item item : Jenkins.get().allItems(Item.class)) {
                if (item instanceof SCMTriggerItem) {
                    if (++count > 10) {
                        return true;
                    }
                }
            }
            return false;
        }

        /**
         * Update the {@link ExecutorService} instance.
         */
        @PostConstruct
        /*package*/ synchronized void resizeThreadPool() {
            queue.setExecutors(Executors.newFixedThreadPool(maximumThreads, threadFactory()));
        }

        @Override
        public boolean configure(StaplerRequest2 req, JSONObject json) throws FormException {
            String t = json.optString("pollingThreadCount", null);
            if (doCheckPollingThreadCount(t).kind != FormValidation.Kind.OK) {
                setPollingThreadCount(THREADS_DEFAULT);
            } else {
                setPollingThreadCount(Integer.parseInt(t));
            }

            // Save configuration
            save();

            return true;
        }

        public FormValidation doCheckPollingThreadCount(@QueryParameter String value) {
            return FormValidation.validateIntegerInRange(value, THREADS_LOWER_BOUND, THREADS_UPPER_BOUND);
        }

        /**
         * Performs syntax check.
         */
        public FormValidation doCheckScmpoll_spec(@QueryParameter String value,
                                                  @QueryParameter boolean ignorePostCommitHooks,
                                                  @AncestorInPath Item item) {
            if (value == null || value.isBlank()) {
                if (ignorePostCommitHooks) {
                    return FormValidation.ok(Messages.SCMTrigger_no_schedules_no_hooks());
                } else {
                    return FormValidation.ok(Messages.SCMTrigger_no_schedules_hooks());
                }
            } else {
                return Jenkins.get().getDescriptorByType(TimerTrigger.DescriptorImpl.class)
                        .doCheckSpec(value, item);
            }
        }
    }

    @Extension
    public static final class AdministrativeMonitorImpl extends AdministrativeMonitor {

        @Override
        public String getDisplayName() {
            return Messages.SCMTrigger_AdministrativeMonitorImpl_DisplayName();
        }

        private boolean on;

        @Override
        public boolean isActivated() {
            return on;
        }
    }

    /**
     * Associated with {@link Run} to show the polling log
     * that triggered that build.
     *
     * @since 1.376
     */
    public static class BuildAction implements RunAction2 {
        private transient /*final*/ Run<?, ?> run;
        @Deprecated
        @SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD", justification = "for backward compatibility")
        public transient /*final*/ AbstractBuild build;

        /**
         * @since 1.568
         */
        public BuildAction(Run<?, ?> run) {
            this.run = run;
            build = run instanceof AbstractBuild ? (AbstractBuild) run : null;
        }

        @Deprecated
        public BuildAction(AbstractBuild build) {
            this((Run) build);
        }

        /**
         * @since 1.568
         */
        public Run<?, ?> getRun() {
            return run;
        }

        /**
         * Polling log that triggered the build.
         */
        public File getPollingLogFile() {
            return new File(run.getRootDir(), "polling.log");
        }

        @Override
        public String getIconFileName() {
            return "clipboard.png";
        }

        @Override
        public String getDisplayName() {
            return Messages.SCMTrigger_BuildAction_DisplayName();
        }

        @Override
        public String getUrlName() {
            return "pollingLog";
        }

        /**
         * Sends out the raw polling log output.
         */
        public void doPollingLog(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException {
            rsp.setContentType("text/plain;charset=UTF-8");
            try (OutputStream os = rsp.getOutputStream();
                 // Prevent jelly from flushing stream so Content-Length header can be added afterwards
                 FlushProofOutputStream out = new FlushProofOutputStream(os)) {
                getPollingLogText().writeLogTo(0, out);
            }
        }

        public AnnotatedLargeText getPollingLogText() {
            return new AnnotatedLargeText<>(getPollingLogFile(), Charset.defaultCharset(), true, this);
        }

        /**
         * Used from {@code polling.jelly} to write annotated polling log to the given output.
         */
        @SuppressFBWarnings(value = "RV_RETURN_VALUE_IGNORED", justification = "method signature does not permit plumbing through the return value")
        public void writePollingLogTo(long offset, XMLOutput out) throws IOException {
            // TODO: resurrect compressed log file support
            getPollingLogText().writeHtmlTo(offset, out.asWriter());
        }

        @Override public void onAttached(Run<?, ?> r) {
            // unnecessary, existing constructor does this
        }

        @Override public void onLoad(Run<?, ?> r) {
            run = r;
            build = run instanceof AbstractBuild ? (AbstractBuild) run : null;
        }
    }

    /**
     * Action object for job. Used to display the last polling log.
     */
    public final class SCMAction implements Action {
        public AbstractProject<?, ?> getOwner() {
            Item item = getItem();
            return item instanceof AbstractProject ? (AbstractProject) item : null;
        }

        /**
         * @since 1.568
         */
        public Item getItem() {
            return job().asItem();
        }

        @Override
        public String getIconFileName() {
            return "clipboard.png";
        }

        @Override
        public String getDisplayName() {
            Set<SCMDescriptor<?>> descriptors = new HashSet<>();
            for (SCM scm : job().getSCMs()) {
                descriptors.add(scm.getDescriptor());
            }
            return descriptors.size() == 1 ? Messages.SCMTrigger_getDisplayName(descriptors.iterator().next().getDisplayName()) : Messages.SCMTrigger_BuildAction_DisplayName();
        }

        @Override
        public String getUrlName() {
            return "scmPollLog";
        }

        public String getLog() throws IOException {
            return Util.loadFile(getLogFile(), Charset.defaultCharset());
        }

        /**
         * Writes the annotated log to the given output.
         * @since 1.350
         */
        @SuppressFBWarnings(value = "RV_RETURN_VALUE_IGNORED", justification = "method signature does not permit plumbing through the return value")
        public void writeLogTo(XMLOutput out) throws IOException {
            new AnnotatedLargeText<>(getLogFile(), Charset.defaultCharset(), true, this).writeHtmlTo(0, out.asWriter());
        }
    }

    private static final Logger LOGGER = Logger.getLogger(SCMTrigger.class.getName());

    /**
     * {@link Runnable} that actually performs polling.
     */
    public class Runner implements Runnable {

        /**
         * When did the polling start?
         */
        private volatile long startTime;

        private Action[] additionalActions;

        public Runner() {
            this(null);
        }

        public Runner(Action[] actions) {
            Objects.requireNonNull(job, "Runner can't be instantiated when job is null");

            if (actions == null) {
                additionalActions = new Action[0];
            } else {
                additionalActions = Arrays.copyOf(actions, actions.length);
            }
        }

        /**
         * Where the log file is written.
         */
        public File getLogFile() {
            return SCMTrigger.this.getLogFile();
        }

        /**
         * For which {@link Item} are we polling?
         * @since 1.568
         */
        public SCMTriggerItem getTarget() {
            return job();
        }

        /**
         * When was this polling started?
         */
        public long getStartTime() {
            return startTime;
        }

        /**
         * Human readable string of when this polling is started.
         */
        public String getDuration() {
            return Util.getTimeSpanString(System.currentTimeMillis() - startTime);
        }

        private boolean runPolling() {
            try {
                // to make sure that the log file contains up-to-date text,
                // don't do buffering.
                StreamTaskListener listener = new StreamTaskListener(getLogFile(), Charset.defaultCharset());

                try {
                    PrintStream logger = listener.getLogger();
                    long start = System.currentTimeMillis();
                    logger.println("Started on " + DateFormat.getDateTimeInstance().format(new Date()));
                    boolean result = job().poll(listener).hasChanges();
                    logger.println("Done. Took " + Util.getTimeSpanString(System.currentTimeMillis() - start));
                    if (result)
                        logger.println("Changes found");
                    else
                        logger.println("No changes");
                    return result;
                } catch (Error | RuntimeException e) {
                    Functions.printStackTrace(e, listener.error("Failed to record SCM polling for " + job));
                    LOGGER.log(Level.SEVERE, "Failed to record SCM polling for " + job, e);
                    throw e;
                } finally {
                    listener.close();
                }
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Failed to record SCM polling for " + job, e);
                return false;
            }
        }

        @Override
        public void run() {
            if (job == null) {
                return;
            }
            // we can preemptively check the SCMDecisionHandler instances here
            // note that job().poll(listener) should also check this
            SCMDecisionHandler veto = SCMDecisionHandler.firstShouldPollVeto(job);
            if (veto != null) {
                try (StreamTaskListener listener = new StreamTaskListener(getLogFile(), Charset.defaultCharset())) {
                    listener.getLogger().println(
                            "Skipping polling on " + DateFormat.getDateTimeInstance().format(new Date())
                                    + " due to veto from " + veto);
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, "Failed to record SCM polling for " + job, e);
                }

                LOGGER.log(Level.FINE, "Skipping polling for {0} due to veto from {1}",
                        new Object[]{job.getFullDisplayName(), veto}
                );
                return;
            }

            String threadName = Thread.currentThread().getName();
            Thread.currentThread().setName("SCM polling for " + job);
            try {
                startTime = System.currentTimeMillis();
                if (runPolling()) {
                    SCMTriggerItem p = job();
                    String name = " #" + p.getNextBuildNumber();
                    SCMTriggerCause cause;
                    try {
                        cause = new SCMTriggerCause(getLogFile());
                    } catch (IOException e) {
                        LOGGER.log(WARNING, "Failed to parse the polling log", e);
                        cause = new SCMTriggerCause();
                    }
                    Action[] queueActions = new Action[additionalActions.length + 1];
                    queueActions[0] = new CauseAction(cause);
                    System.arraycopy(additionalActions, 0, queueActions, 1, additionalActions.length);
                    if (p.scheduleBuild2(p.getQuietPeriod(), queueActions) != null) {
                        LOGGER.info("SCM changes detected in " + job.getFullDisplayName() + ". Triggering " + name);
                    } else {
                        LOGGER.info("SCM changes detected in " + job.getFullDisplayName() + ". Job is already in the queue");
                    }
                }
            } finally {
                Thread.currentThread().setName(threadName);
            }
        }

        // as per the requirement of SequentialExecutionQueue, value equality is necessary
        @Override
        public boolean equals(Object that) {
            return that instanceof Runner && job == ((Runner) that)._job();
        }

        private Item _job() {
            return job;
        }

        @Override
        public int hashCode() {
            return Objects.requireNonNull(job).hashCode();
        }
    }

    private SCMTriggerItem job() {
        return SCMTriggerItem.SCMTriggerItems.asSCMTriggerItem(job);
    }

    public static class SCMTriggerCause extends Cause {
        /**
         * Only used while ths cause is in the queue.
         * Once attached to the build, we'll move this into a file to reduce the memory footprint.
         */
        @CheckForNull
        private String pollingLog;

        private transient Run run;

        public SCMTriggerCause(File logFile) throws IOException {
            // TODO: charset of this log file?
            this(Files.readString(Util.fileToPath(logFile), Charset.defaultCharset()));
        }

        public SCMTriggerCause(String pollingLog) {
            this.pollingLog = pollingLog;
        }

        /**
         * @deprecated
         *      Use {@link SCMTrigger.SCMTriggerCause#SCMTriggerCause(String)}.
         */
        @Deprecated
        public SCMTriggerCause() {
            this("");
        }

        @Override
        public void onLoad(Run run) {
            this.run = run;
        }

        @Override
        public void onAddedTo(Run build) {
            this.run = build;
            try {
                BuildAction a = new BuildAction(build);
                // pollingLog can be null when rebuilding a job that was initially triggered by polling.
                if (pollingLog != null) {
                    Files.writeString(Util.fileToPath(a.getPollingLogFile()), pollingLog, Charset.defaultCharset());
                }
                build.replaceAction(a);
            } catch (IOException e) {
                LOGGER.log(WARNING, "Failed to persist the polling log", e);
            }
            pollingLog = null;
        }

        @Override
        public String getShortDescription() {
            return Messages.SCMTrigger_SCMTriggerCause_ShortDescription();
        }

        @Restricted(DoNotUse.class)
        public Run getRun() {
            return this.run;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof SCMTriggerCause;
        }

        @Override
        public int hashCode() {
            return 3;
        }
    }

    /**
     * How long is too long for a polling activity to be in the queue?
     */
    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "for script console")
    public static long STARVATION_THRESHOLD = SystemProperties.getLong(SCMTrigger.class.getName() + ".starvationThreshold", TimeUnit.HOURS.toMillis(1));
}
