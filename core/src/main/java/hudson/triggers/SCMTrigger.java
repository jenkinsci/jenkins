package hudson.triggers;

import antlr.ANTLRException;
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.model.Project;
import hudson.model.SCMedItem;
import hudson.util.StreamTaskListener;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

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

        if (DESCRIPTOR.synchronousPolling) {
        	LOGGER.fine("Running the trigger directly without threading, " +
        			"as it's already taken care of by Trigger.Cron");
            new Runner().run();
        } else {
            // schedule the polling.
            // even if we end up submitting this too many times, that's OK.
            // the real exclusion control happens inside Runner.
        	LOGGER.fine("scheduling the trigger to (asynchronously) run");
            DESCRIPTOR.getExecutor().submit(new Runner());
        }
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

    public DescriptorImpl getDescriptor() {
        return DESCRIPTOR;
    }

    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    public static final class DescriptorImpl extends TriggerDescriptor {
        /**
         * Used to control the execution of the polling tasks.
         */
        transient volatile ExecutorService executor;

        /**
         * Whether the projects should be polled all in one go in the order of dependencies. The default behavior is
         * that each project polls for changes independently.
         */
        public boolean synchronousPolling = false;

        /**
         * Jobs that are being polled. The value is useful for trouble-shooting.
         */
        final transient Set<SCMedItem> items = Collections.synchronizedSet(new HashSet<SCMedItem>());

        /**
         * Max number of threads for SCM polling.
         * 0 for unbounded.
         */
        private int maximumThreads;

        DescriptorImpl() {
            super(SCMTrigger.class);
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
         * Gets the snapshot of {@link SCMedItem}s that are being polled at this very moment.
         * Designed for trouble-shooting probe.
         */
        public List<SCMedItem> getItemsBeingPolled() {
            return Arrays.asList(items.toArray(new SCMedItem[0]));
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
            ExecutorService newExec = maximumThreads==0 ? Executors.newCachedThreadPool() : Executors.newFixedThreadPool(maximumThreads);
            ExecutorService old = executor;
            executor = newExec;
            if(old!=null)
                old.shutdown();
        }

        public boolean configure(StaplerRequest req) throws FormException {
            String t = req.getParameter("poll_scm_threads");
            if(t==null || t.length()==0)
                setPollingThreadCount(0);
            else
                setPollingThreadCount(Integer.parseInt(t));

            // Save configuration
            save();

            return super.configure(req);
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
    private class Runner implements Runnable {
        private boolean runPolling() {
            try {
                // to make sure that the log file contains up-to-date text,
                // don't do buffering.
                StreamTaskListener listener = new StreamTaskListener(getLogFile());

                try {
                    PrintStream logger = listener.getLogger();
                    long start = System.currentTimeMillis();
                    logger.println("Started on "+new Date().toLocaleString());
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
            try {
                while(pollingScheduled) {
                    getLock().lockInterruptibly();
                    boolean foundChanges=false;
                    try {
                        if(pollingScheduled) {
                            pollingScheduled = false;
                            getDescriptor().items.add(job);
                            try {
                                foundChanges = runPolling();
                            } finally {
                                getDescriptor().items.remove(job);
                            }
                        }
                    } finally {
                        getLock().unlock();
                    }
                    
                    if(foundChanges) {
                        String name = " #"+job.asProject().getNextBuildNumber();
                        if(job.scheduleBuild()) {
                            LOGGER.info("SCM changes detected in "+ job.getName()+". Triggering "+name);
                        } else {
                            LOGGER.info("SCM changes detected in "+ job.getName()+". Job is already in the queue");
                        }
                    }
                }
            } catch (InterruptedException e) {
                LOGGER.info("Aborted");
            }
        }
    }
}
