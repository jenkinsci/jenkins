package hudson.triggers;

import antlr.ANTLRException;
import hudson.Util;
import hudson.model.Action;
import hudson.model.Build;
import hudson.model.Descriptor;
import hudson.model.Project;
import hudson.model.TaskListener;
import hudson.util.StreamTaskListener;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.kohsuke.stapler.StaplerRequest;

/**
 * {@link Trigger} that checks for SCM updates periodically.
 *
 * @author Kohsuke Kawaguchi
 */
public class SCMTrigger extends Trigger {
    /**
     * Non-null if the polling is in progress.
     * @guardedBy this
     */
    private transient boolean pollingScheduled;

    /**
     * Non-null if the polling is in progress.
     * @guardedBy this
     */
    private transient Thread pollingThread;

    /**
     * Signal to the polling thread to abort now.
     */
    private transient boolean abortNow;

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
        if(pollingThread!=null && pollingThread.isAlive()) {
            System.out.println("killing polling");
            abortNow = true;
            pollingThread.interrupt();
            pollingThread.join();
            abortNow = false;
        }
    }

    /**
     * Start polling if it's scheduled.
     */
    public synchronized void startPolling() {
        Build b = project.getLastBuild();

        if(b!=null && b.isBuilding())
            return; // build in progress

        if(pollingThread!=null && pollingThread.isAlive())
            return; // polling already in progress

        if(!pollingScheduled)
            return; // not scheduled

        pollingScheduled = false;
        pollingThread = new Thread() {
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
                        logger.println("Done. Took "+Util.getTimeSpanString(System.currentTimeMillis()-start));
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
                boolean repeat;
                do {
                    if(runPolling()) {
                        LOGGER.info("SCM changes detected in "+project.getName());
                        project.scheduleBuild();
                    }
                    if(abortNow)
                        return; // terminate now

                    synchronized(SCMTrigger.this) {
                        repeat = pollingScheduled;
                        pollingScheduled = false;
                    }
                } while(repeat);
            }
        };
        pollingThread.start();
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

    public static final Descriptor<Trigger> DESCRIPTOR = new Descriptor<Trigger>(SCMTrigger.class) {
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
    };

    /**
     * Action object for {@link Project}. Used to display the polling log.
     */
    public final class SCMAction implements Action {
        public Project getOwner() {
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
}
