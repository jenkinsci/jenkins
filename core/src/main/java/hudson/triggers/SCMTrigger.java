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
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.File;
import java.io.IOException;
import java.io.ObjectStreamException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.locks.ReentrantLock;
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
    /**
     * If we'd like to run another polling run, this is set to true.
     *
     * <p>
     * To avoid submitting more than one polling jobs (which could flood the queue),
     * we first use the boolean flag.
     *
     * @guardedBy this
     */
    private transient volatile boolean pollingScheduled;

    /**
     * This lock is used to control the mutual exclusion of the SCM activity,
     * so that the build and polling don't happen at the same time.
     */
    private transient ReentrantLock lock;

    @DataBoundConstructor
    public SCMTrigger(String scmpoll_spec) throws ANTLRException {
        super(scmpoll_spec);
        lock = new ReentrantLock();
    }

    public ReentrantLock getLock() {
        return lock;
    }

    protected Object readResolve() throws ObjectStreamException {
        lock = new ReentrantLock();
        return super.readResolve();
    }

    public void run() {
        if(pollingScheduled || Hudson.getInstance().isQuietingDown())
            return; // noop
        pollingScheduled = true;
        LOGGER.fine("Scheduling a polling for "+job);

        DescriptorImpl d = getDescriptor();

        if (d.synchronousPolling) {
        	LOGGER.fine("Running the trigger directly without threading, " +
        			"as it's already taken care of by Trigger.Cron");
            new Runner().run();
        } else {
            // schedule the polling.
            // even if we end up submitting this too many times, that's OK.
            // the real exclusion control happens inside Runner.
        	LOGGER.fine("scheduling the trigger to (asynchronously) run");
            d.getExecutor().submit(new Runner());
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
         */
        transient volatile ThreadPoolExecutor executor;

        /**
         * Whether the projects should be polled all in one go in the order of dependencies. The default behavior is
         * that each project polls for changes independently.
         */
        public boolean synchronousPolling = false;

        /**
         * Jobs that are being polled. The value is useful for trouble-shooting.
         */
        final transient Set<Runner> items = Collections.synchronizedSet(new HashSet<Runner>());

        /**
         * Max number of threads for SCM polling.
         * 0 for unbounded.
         */
        private int maximumThreads;

        public DescriptorImpl() {
            load();
            /*
             * Need to resize the thread pool here in case there is no existing configuration file for SCMTrigger as
             * setPollingThreadCount() is not called in this case
             */
            resizeThreadPool();
        }

        public boolean isApplicable(Item item) {
            return item instanceof SCMedItem;
        }

        public ExecutorService getExecutor() {
            return executor;
        }

        /**
         * Returns true if the SCM polling thread queue has too many jobs
         * than it can handle.
         */
        public boolean isClogged() {
            for( Runnable r : executor.getQueue() ) {
                if (r instanceof Runner) {// this should be always true, but let's be defensive.
                    Runner rr = (Runner) r;
                    if(rr.isStarving())
                        return true;
                }
            }
            return false;
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
            synchronized (items) {
                return Arrays.asList(items.toArray(new Runner[items.size()]));
            }
        }

        /**
         * Gets the snapshot of {@link SCMedItem}s that are being polled at this very moment.
         */
        public List<SCMedItem> getItemsBeingPolled() {
            synchronized (items) {
                List<SCMedItem> r = new ArrayList<SCMedItem>();
                for (Runner i : items)
                    r.add(i.getTarget());
                return r;
            }
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
            // swap to a new one, and shut down the old one gradually
            ThreadPoolExecutor newExec = (ThreadPoolExecutor)
                    (maximumThreads==0 ? Executors.newCachedThreadPool() : Executors.newFixedThreadPool(maximumThreads));
            ExecutorService old = executor;
            executor = newExec;
            if(old!=null)
                old.shutdown();
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
         * When was this object submitted to {@link DescriptorImpl#getExecutor()}?
         *
         * <p>
         * This field is used to check if the queue is clogged.
         */
        public final long submissionTime = System.currentTimeMillis();

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

        /**
         * Returns true if too much time is spent since this item is put into the queue.
         *
         * <p>
         * This property makes sense only when called before the task actually starts,
         * as the {@link #run()} method may take a long time to execute.
         */
        public boolean isStarving() {
            return System.currentTimeMillis()-submissionTime > STARVATION_THRESHOLD;
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
                while(pollingScheduled) {
                    getLock().lockInterruptibly();
                    boolean foundChanges=false;
                    try {
                        if(pollingScheduled) {
                            pollingScheduled = false;
                            startTime = System.currentTimeMillis();
                            getDescriptor().items.add(this);
                            try {
                                foundChanges = runPolling();
                            } finally {
                                getDescriptor().items.remove(this);
                            }
                        }
                    } finally {
                        getLock().unlock();
                    }
                    
                    if(foundChanges) {
                        String name = " #"+job.asProject().getNextBuildNumber();
                        if(job.scheduleBuild(new SCMTriggerCause())) {
                            LOGGER.info("SCM changes detected in "+ job.getName()+". Triggering "+name);
                        } else {
                            LOGGER.info("SCM changes detected in "+ job.getName()+". Job is already in the queue");
                        }
                    }
                }
            } catch (InterruptedException e) {
                LOGGER.info("Aborted");
            } finally {
                Thread.currentThread().setName(threadName);
            }
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
