package hudson.triggers;

import antlr.ANTLRException;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.Descriptor;
import hudson.model.Project;
import hudson.model.TaskListener;
import hudson.util.StreamTaskListener;
import org.kohsuke.stapler.StaplerRequest;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Date;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link Trigger} that checks for SCM updates periodically.
 *
 * @author Kohsuke Kawaguchi
 */
public class SCMTrigger extends Trigger {
    /**
     * If we'd like to run another polling run, this is set to true.
     *
     * <p>
     * To avoid submitting more than one polling jobs (which could flood the queue),
     * we first use the boolean flag.
     *
     * @guardedBy this
     */
    private transient boolean pollingScheduled;

    /**
     * Signal to the polling thread to abort now.
     */
    private transient boolean abortNow;

    /**
     * Pending polling activity in progress.
     * There's at most one polling activity per project at any given point.
     *
     * @guardedBy this
     */
    private transient Future<?> polling;

    public SCMTrigger(String cronTabSpec) throws ANTLRException {
        super(cronTabSpec);
    }

    protected synchronized void run() {
        if(pollingScheduled)
            return; // noop
        pollingScheduled = true;

        // otherwise do it now
        startPolling();
    }

    public Action getProjectAction() {
        return new SCMAction();
    }

    /**
     * Makes sure that the polling is aborted.
     */
    public synchronized void abort() throws InterruptedException {
        if(polling!=null && !polling.isDone()) {
            System.out.println("killing polling");

            abortNow = true;
            polling.cancel(true);
            try {
                polling.get();
            } catch (ExecutionException e) {
                LOGGER.log(Level.WARNING, "Failed to poll",e);
            } catch (CancellationException e) {
                // this is fine
            }
            abortNow = false;
        }
    }

    /**
     * Start polling if it's scheduled.
     */
    public synchronized void startPolling() {
        AbstractBuild b = (AbstractBuild)project.getLastBuild();

        if(b!=null && b.isBuilding())
            return; // build in progress

        if(polling!=null && !polling.isDone())
            return; // polling already in progress

        if(!pollingScheduled)
            return; // not scheduled

        pollingScheduled = false;
        polling = DESCRIPTOR.getExecutor().submit(new Runner());
    }

    /**
     * Returns the file that records the last/current polling activity.
     */
    public File getLogFile() {
        return new File(project.getRootDir(),"scm-polling.log");
    }

    public Descriptor<Trigger> getDescriptor() {
        return DESCRIPTOR;
    }

    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    public static final class DescriptorImpl extends Descriptor<Trigger> {
        /**
         * Used to control the execution of the polling tasks.
         */
        transient volatile ExecutorService executor;

        /**
         * Max number of threads for SCM polling.
         * 0 for unbounded.
         */
        private int maximumThreads;

        DescriptorImpl() {
            super(SCMTrigger.class);
            load();
            // create an executor
            update();
        }

        public ExecutorService getExecutor() {
            return executor;
        }

        public String getDisplayName() {
            return "Poll SCM";
        }

        public String getHelpFile() {
            return "/help/project-config/poll-scm.html";
        }

        public Trigger newInstance(StaplerRequest req) throws FormException {
            try {
                return new SCMTrigger(req.getParameter("scmpoll_spec"));
            } catch (ANTLRException e) {
                throw new FormException(e.toString(),e,"scmpoll_spec");
            }
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

        public void setPollingThreadCount(int n) {
            // fool proof
            if(n<0)     n=0;
            if(n>100)   n=100;

            maximumThreads = n;
            save();
        }

        /**
         * Update the {@link ExecutorService} instance.
         */
        /*package*/ synchronized void update() {
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
            return super.configure(req);
        }
    }

    /**
     * Action object for {@link Project}. Used to display the polling log.
     */
    public final class SCMAction implements Action {
        public AbstractProject<?,?> getOwner() {
            return project;
        }

        public String getIconFileName() {
            return "clipboard.gif";
        }

        public String getDisplayName() {
            return project.getScm().getDescriptor().getDisplayName()+" Polling Log";
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
                OutputStream fos = new FileOutputStream(getLogFile());
                TaskListener listener = new StreamTaskListener(fos);

                try {
                    LOGGER.info("Polling SCM changes of "+project.getName());

                    PrintStream logger = listener.getLogger();
                    long start = System.currentTimeMillis();
                    logger.println("Started on "+new Date().toLocaleString());
                    boolean result = project.pollSCMChanges(listener);
                    logger.println("Done. Took "+ Util.getTimeSpanString(System.currentTimeMillis()-start));
                    if(result)
                        logger.println("Changes found");
                    else
                        logger.println("No changes");
                    return result;
                } finally {
                    fos.close();
                }
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE,"Failed to record SCM polling",e);
                return false;
            }
        }

        public void run() {
            if(runPolling()) {
                LOGGER.info("SCM changes detected in "+project.getName());
                project.scheduleBuild();
            }

            synchronized(SCMTrigger.this) {
                if(abortNow)
                    return; // terminate now without queueing the next one.
                
                if(pollingScheduled) {
                    // schedule a next run
                    polling = DESCRIPTOR.getExecutor().submit(new Runner());
                }
                pollingScheduled = false;
            }
        }
    }
}
