/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Brian Westrich, Jean-Baptiste Quenot, id:cactusman
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
import hudson.Util;
import hudson.Extension;
import hudson.console.AnnotatedLargeText;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.Cause;
import jenkins.model.Jenkins;
import hudson.model.Item;
import hudson.model.Project;
import hudson.model.SCMedItem;
import hudson.model.AdministrativeMonitor;
import hudson.util.FlushProofOutputStream;
import hudson.util.FormValidation;
import hudson.util.StreamTaskListener;
import hudson.util.TimeUnit2;
import hudson.util.SequentialExecutionQueue;
import org.apache.commons.io.FileUtils;
import org.apache.commons.jelly.XMLOutput;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.text.DateFormat;

import net.sf.json.JSONObject;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerResponse;

import static java.util.logging.Level.*;

/**
 * {@link Trigger} that checks for SCM updates periodically.
 *
 * @author Kohsuke Kawaguchi
 */
public class SCMTrigger extends Trigger<SCMedItem> {
    
    private boolean ignorePostCommitHooks;
    
    public SCMTrigger(String scmpoll_spec) throws ANTLRException {
        this(scmpoll_spec, false);
    }
    
    @DataBoundConstructor
    public SCMTrigger(String scmpoll_spec, boolean ignorePostCommitHooks) throws ANTLRException {
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

    @Override
    public void run() {
        run(null);
    }

    /**
     * Run the SCM trigger with additional build actions. Used by SubversionRepositoryStatus
     * to trigger a build at a specific revisionn number.
     * 
     * @param additionalActions
     * @since 1.375
     */
    public void run(Action[] additionalActions) {
        if(Jenkins.getInstance().isQuietingDown())
            return; // noop

        DescriptorImpl d = getDescriptor();

        LOGGER.fine("Scheduling a polling for "+job);
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
        return (DescriptorImpl)super.getDescriptor();
    }

    @Override
    public Collection<? extends Action> getProjectActions() {
        return Collections.singleton(new SCMAction());
    }

    /**
     * Returns the file that records the last/current polling activity.
     */
    public File getLogFile() {
        return new File(job.getRootDir(),"scm-polling.log");
    }

    @Extension
    public static class DescriptorImpl extends TriggerDescriptor {
        /**
         * Used to control the execution of the polling tasks.
         * <p>
         * This executor implementation has a semantics suitable for polling. Namely, no two threads will try to poll the same project
         * at once, and multiple polling requests to the same job will be combined into one. Note that because executor isn't aware
         * of a potential workspace lock between a build and a polling, we may end up using executor threads unwisely --- they
         * may block.
         */
        private transient final SequentialExecutionQueue queue = new SequentialExecutionQueue(Executors.newSingleThreadExecutor());

        /**
         * Whether the projects should be polled all in one go in the order of dependencies. The default behavior is
         * that each project polls for changes independently.
         */
        public boolean synchronousPolling = false;

        /**
         * Max number of threads for SCM polling.
         * 0 for unbounded.
         */
        private int maximumThreads;

        public DescriptorImpl() {
            load();
            resizeThreadPool();
        }

        public boolean isApplicable(Item item) {
            return item instanceof SCMedItem;
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
            return Util.filter(queue.getInProgress(),Runner.class);
        }

        /**
         * Gets the snapshot of {@link SCMedItem}s that are being polled at this very moment.
         */
        public List<SCMedItem> getItemsBeingPolled() {
            List<SCMedItem> r = new ArrayList<SCMedItem>();
            for (Runner i : getRunners())
                r.add(i.getTarget());
            return r;
        }

        public String getDisplayName() {
            return Messages.SCMTrigger_DisplayName();
        }

        /**
         * Gets the number of concurrent threads used for polling.
         *
         * @return
         *      0 if unlimited.
         */
        public int getPollingThreadCount() {
            return maximumThreads;
        }

        /**
         * Sets the number of concurrent threads used for SCM polling and resizes the thread pool accordingly
         * @param n number of concurrent threads, zero or less means unlimited, maximum is 100
         */
        public void setPollingThreadCount(int n) {
            // fool proof
            if(n<0)     n=0;
            if(n>100)   n=100;

            maximumThreads = n;

            resizeThreadPool();
        }

        /**
         * Update the {@link ExecutorService} instance.
         */
        /*package*/ synchronized void resizeThreadPool() {
            queue.setExecutors(
                    (maximumThreads==0 ? Executors.newCachedThreadPool() : Executors.newFixedThreadPool(maximumThreads)));
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
            String t = json.optString("pollingThreadCount",null);
            if(t==null || t.length()==0)
                setPollingThreadCount(0);
            else
                setPollingThreadCount(Integer.parseInt(t));

            // Save configuration
            save();

            return true;
        }

        public FormValidation doCheckPollingThreadCount(@QueryParameter String value) {
            if (value != null && "".equals(value.trim()))
                return FormValidation.ok();
            return FormValidation.validateNonNegativeInteger(value);
        }
    }

    @Extension
    public static final class AdministrativeMonitorImpl extends AdministrativeMonitor {
        private boolean on;

        public boolean isActivated() {
            return on;
        }
    }

    /**
     * Associated with {@link AbstractBuild} to show the polling log
     * that triggered that build.
     *
     * @since 1.376
     */
    public static class BuildAction implements Action {
        public final AbstractBuild build;

        public BuildAction(AbstractBuild build) {
            this.build = build;
        }

        /**
         * Polling log that triggered the build.
         */
        public File getPollingLogFile() {
            return new File(build.getRootDir(),"polling.log");
        }

        public String getIconFileName() {
            return "clipboard.png";
        }

        public String getDisplayName() {
            return Messages.SCMTrigger_BuildAction_DisplayName();
        }

        public String getUrlName() {
            return "pollingLog";
        }

        /**
         * Sends out the raw polling log output.
         */
        public void doPollingLog(StaplerRequest req, StaplerResponse rsp) throws IOException {
            rsp.setContentType("text/plain;charset=UTF-8");
            // Prevent jelly from flushing stream so Content-Length header can be added afterwards
            FlushProofOutputStream out = new FlushProofOutputStream(rsp.getCompressedOutputStream(req));
            getPollingLogText().writeLogTo(0, out);
            out.close();
        }

        public AnnotatedLargeText getPollingLogText() {
            return new AnnotatedLargeText<BuildAction>(getPollingLogFile(), Charset.defaultCharset(), true, this);
        }
        
        /**
         * Used from <tt>polling.jelly</tt> to write annotated polling log to the given output.
         */
        public void writePollingLogTo(long offset, XMLOutput out) throws IOException {
            // TODO: resurrect compressed log file support
            getPollingLogText().writeHtmlTo(offset, out.asWriter());
        }
    }

    /**
     * Action object for {@link Project}. Used to display the last polling log.
     */
    public final class SCMAction implements Action {
        public AbstractProject<?,?> getOwner() {
            return job.asProject();
        }

        public String getIconFileName() {
            return "clipboard.png";
        }

        public String getDisplayName() {
            return Messages.SCMTrigger_getDisplayName(job.getScm().getDescriptor().getDisplayName());
        }

        public String getUrlName() {
            return "scmPollLog";
        }

        public String getLog() throws IOException {
            return Util.loadFile(getLogFile());
        }

        /**
         * Writes the annotated log to the given output.
         * @since 1.350
         */
        public void writeLogTo(XMLOutput out) throws IOException {
            new AnnotatedLargeText<SCMAction>(getLogFile(),Charset.defaultCharset(),true,this).writeHtmlTo(0,out.asWriter());
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
            additionalActions = new Action[0];
        }
        
        public Runner(Action[] actions) {
            if (actions == null) {
                additionalActions = new Action[0];
            } else {
                additionalActions = actions;
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
         */
        public SCMedItem getTarget() {
            return job;
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
            return Util.getTimeSpanString(System.currentTimeMillis()-startTime);
        }

        private boolean runPolling() {
            try {
                // to make sure that the log file contains up-to-date text,
                // don't do buffering.
                StreamTaskListener listener = new StreamTaskListener(getLogFile());

                try {
                    PrintStream logger = listener.getLogger();
                    long start = System.currentTimeMillis();
                    logger.println("Started on "+ DateFormat.getDateTimeInstance().format(new Date()));
                    boolean result = job.poll(listener).hasChanges();
                    logger.println("Done. Took "+ Util.getTimeSpanString(System.currentTimeMillis()-start));
                    if(result)
                        logger.println("Changes found");
                    else
                        logger.println("No changes");
                    return result;
                } catch (Error e) {
                    e.printStackTrace(listener.error("Failed to record SCM polling for "+job));
                    LOGGER.log(Level.SEVERE,"Failed to record SCM polling for "+job,e);
                    throw e;
                } catch (RuntimeException e) {
                    e.printStackTrace(listener.error("Failed to record SCM polling for "+job));
                    LOGGER.log(Level.SEVERE,"Failed to record SCM polling for "+job,e);
                    throw e;
                } finally {
                    listener.close();
                }
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE,"Failed to record SCM polling for "+job,e);
                return false;
            }
        }

        public void run() {
            String threadName = Thread.currentThread().getName();
            Thread.currentThread().setName("SCM polling for "+job);
            try {
                startTime = System.currentTimeMillis();
                if(runPolling()) {
                    AbstractProject p = job.asProject();
                    String name = " #"+p.getNextBuildNumber();
                    SCMTriggerCause cause;
                    try {
                        cause = new SCMTriggerCause(getLogFile());
                    } catch (IOException e) {
                        LOGGER.log(WARNING, "Failed to parse the polling log",e);
                        cause = new SCMTriggerCause();
                    }
                    if(p.scheduleBuild(p.getQuietPeriod(), cause, additionalActions)) {
                        LOGGER.info("SCM changes detected in "+ job.getName()+". Triggering "+name);
                    } else {
                        LOGGER.info("SCM changes detected in "+ job.getName()+". Job is already in the queue");
                    }
                }
            } finally {
                Thread.currentThread().setName(threadName);
            }
        }

        private SCMedItem job() {
            return job;
        }

        // as per the requirement of SequentialExecutionQueue, value equality is necessary
        @Override
        public boolean equals(Object that) {
            return that instanceof Runner && job()==((Runner)that).job();
        }

        @Override
        public int hashCode() {
            return job.hashCode();
        }
    }

    public static class SCMTriggerCause extends Cause {
        /**
         * Only used while ths cause is in the queue.
         * Once attached to the build, we'll move this into a file to reduce the memory footprint.
         */
        private String pollingLog;

        public SCMTriggerCause(File logFile) throws IOException {
            // TODO: charset of this log file?
            this(FileUtils.readFileToString(logFile));
        }

        public SCMTriggerCause(String pollingLog) {
            this.pollingLog = pollingLog;
        }

        /**
         * @deprecated
         *      Use {@link #SCMTrigger.SCMTriggerCause(String)}.
         */
        public SCMTriggerCause() {
            this("");
        }

        @Override
        public void onAddedTo(AbstractBuild build) {
            BuildAction oldAction = build.getAction(BuildAction.class);
            if (oldAction != null) {
                build.getActions().remove(oldAction);
            }
            
            try {
                BuildAction a = new BuildAction(build);
                FileUtils.writeStringToFile(a.getPollingLogFile(),pollingLog);
                build.addAction(a);
            } catch (IOException e) {
                LOGGER.log(WARNING,"Failed to persist the polling log",e);
            }
            pollingLog = null;
        }

        @Override
        public String getShortDescription() {
            return Messages.SCMTrigger_SCMTriggerCause_ShortDescription();
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
    public static long STARVATION_THRESHOLD =Long.getLong(SCMTrigger.class.getName()+".starvationThreshold", TimeUnit2.HOURS.toMillis(1));
}
