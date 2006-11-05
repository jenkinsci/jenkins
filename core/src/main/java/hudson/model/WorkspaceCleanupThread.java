package hudson.model;

import hudson.Util;
import hudson.util.StreamTaskListener;

import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Date;
import java.util.logging.Level;

/**
 * Clean up old left-over workspaces from slaves.
 *
 * @author Kohsuke Kawaguchi
 */
public class WorkspaceCleanupThread extends PeriodicWork {
    private static WorkspaceCleanupThread theInstance;

    public WorkspaceCleanupThread() {
        super("Workspace clean-up");
        theInstance = this;
    }

    public static void invoke() {
        theInstance.run();
    }

    private TaskListener listener;

    protected void execute() {
        Hudson h = Hudson.getInstance();
        try {
            // don't buffer this, so that the log shows what the worker thread is up to in real time
            OutputStream os = new FileOutputStream(
                new File(h.getRootDir(),"workspace-cleanup.log"));
            try {
                listener = new StreamTaskListener(os);

                for (Slave s : h.getSlaves()) {
                    process(s);
                }

                process(h);
            } finally {
                os.close();
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to access log file",e);
        }
    }

    private void process(Hudson h) {
        File jobs = new File(h.getRootDir(), "jobs");
        File[] dirs = jobs.listFiles(DIR_FILTER);
        if(dirs==null)      return;
        for (File dir : dirs) {
            File ws = new File(dir, "workspace");
            if(shouldBeDeleted(dir.getName(),ws,h)) {
                delete(ws);
            }
        }
    }

    private boolean shouldBeDeleted(String jobName, File dir, Node n) {
        Job job = Hudson.getInstance().getJob(jobName);
        if(job==null)
            // no such project anymore
            return true;

        if(!dir.exists())
            return false;

        if (job instanceof Project) {
            Project p = (Project) job;
            Node lb = p.getLastBuiltOn();
            if(lb!=null && lb.equals(n))
                // this is the active workspace. keep it.
                return false;
        }

        // if older than a month, delete
        return dir.lastModified() + 30 * DAY < new Date().getTime();

    }

    private void process(Slave s) {
        // TODO: we should be using launcher to execute remote rm -rf

        listener.getLogger().println("Scanning "+s.getNodeName());

        File[] dirs = s.getWorkspaceRoot().getLocal().listFiles(DIR_FILTER);
        if(dirs ==null)     return;
        for (File dir : dirs) {
            if(shouldBeDeleted(dir.getName(),dir,s))
                delete(dir);
        }
    }

    private void delete(File dir) {
        try {
            listener.getLogger().println("Deleting "+dir);
            Util.deleteRecursive(dir);
        } catch (IOException e) {
            e.printStackTrace(listener.error("Failed to delete "+dir));
        }
    }


    private static final FileFilter DIR_FILTER = new FileFilter() {
        public boolean accept(File f) {
            return f.isDirectory();
        }
    };

    private static final long DAY = 1000*60*60*24;
}
