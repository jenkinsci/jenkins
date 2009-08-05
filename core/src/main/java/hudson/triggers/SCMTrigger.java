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
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.Cause;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.model.Project;
import hudson.model.SCMedItem;
import hudson.model.AdministrativeMonitor;
import hudson.util.StreamTaskListener;
import hudson.util.TimeUnit2;
import hudson.util.SequentialExecutionQueue;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.text.DateFormat;

import net.sf.json.JSONObject;

/**
 * {@link Trigger} that checks for SCM updates periodically.
 *
 * @author Kohsuke Kawaguchi
 */
public class SCMTrigger extends Trigger<SCMedItem> {
    @DataBoundConstructor
    public SCMTrigger(String scmpoll_spec) throws ANTLRException {
        super(scmpoll_spec);
    }

    public void run() {
        if(Hudson.getInstance().isQuietingDown())
            return; // noop

        DescriptorImpl d = getDescriptor();

        LOGGER.fine("Scheduling a polling for "+job);
        if (d.synchronousPolling) {
        	LOGGER.fine("Running the trigger directly without threading, " +
        			"as it's already taken care of by Trigger.Cron");
            new Runner().run();
        } else {
            // schedule the polling.
            // even if we end up submitting this too many times, that's OK.
            // the real exclusion control happens inside Runner.
        	LOGGER.fine("scheduling the trigger to (asynchronously) run");
            d.queue.execute(new Runner());
            d.clogCheck();
        }
    }

    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    public Action getProjectAction() {
        return new SCMAction();
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

        public String getHelpFile() {
            return "/help/project-config/poll-scm.html";
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

        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
            String t = req.getParameter("poll_scm_threads");
            if(t==null || t.length()==0)
                setPollingThreadCount(0);
            else
                setPollingThreadCount(Integer.parseInt(t));

            // Save configuration
            save();

            return true;
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
     * Action object for {@link Project}. Used to display the polling log.
     */
    public final class SCMAction implements Action {
        public AbstractProject<?,?> getOwner() {
            return job.asProject();
        }

        public String getIconFileName() {
            return "clipboard.gif";
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
                    boolean result = job.pollSCMChanges(listener);
                    logger.println("Done. Took "+ Util.getTimeSpanString(System.currentTimeMillis()-start));
                    if(result)
                        logger.println("Changes found");
                    else
                        logger.println("No changes");
                    return result;
                } finally {
                    listener.close();
                }
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE,"Failed to record SCM polling",e);
                return false;
            } catch (RuntimeException e) {
                LOGGER.log(Level.SEVERE,"Failed to record SCM polling",e);
                throw e;
            } catch (Error e) {
                LOGGER.log(Level.SEVERE,"Failed to record SCM polling",e);
                throw e;
            }
        }

        public void run() {
            String threadName = Thread.currentThread().getName();
            Thread.currentThread().setName("SCM polling for "+job);
            try {
                startTime = System.currentTimeMillis();
                if(runPolling()) {
                    String name = " #"+job.asProject().getNextBuildNumber();
                    if(job.scheduleBuild(new SCMTriggerCause())) {
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
        public boolean equals(Object that) {
            return job()==((Runner)that).job();
        }

        @Override
        public int hashCode() {
            return job.hashCode();
        }
    }

    public static class SCMTriggerCause extends Cause {
        @Override
        public String getShortDescription() {
            return Messages.SCMTrigger_SCMTriggerCause_ShortDescription();
        }
    }

    /**
     * How long is too long for a polling activity to be in the queue?
     */
    public static long STARVATION_THRESHOLD =Long.getLong(SCMTrigger.class.getName()+".starvationThreshold", TimeUnit2.HOURS.toMillis(1));
}
