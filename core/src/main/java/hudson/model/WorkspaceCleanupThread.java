/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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
package hudson.model;

import hudson.FilePath;
import hudson.Util;
import hudson.util.StreamTaskListener;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

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

    private StreamTaskListener listener;

    protected void execute() {
        Hudson h = Hudson.getInstance();
        try {
            // don't buffer this, so that the log shows what the worker thread is up to in real time
            try {
                listener = new StreamTaskListener(new File(h.getRootDir(),"workspace-cleanup.log"));

                for (Slave s : h.getSlaves())
                    process(s);

                process(h);
            } catch (InterruptedException e) {
                e.printStackTrace(listener.fatalError("aborted"));
            } finally {
                if(listener!=null)
                    listener.close();
                listener = null;
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to access log file",e);
        }
    }

    private void process(Hudson h) throws IOException, InterruptedException {
        File jobs = new File(h.getRootDir(), "jobs");
        File[] dirs = jobs.listFiles(DIR_FILTER);
        if(dirs==null)      return;
        for (File dir : dirs) {
            FilePath ws = new FilePath(new File(dir, "workspace"));
            if(shouldBeDeleted(dir.getName(),ws,h)) {
                delete(ws);
            }
        }
    }

    private boolean shouldBeDeleted(String jobName, FilePath dir, Node n) throws IOException, InterruptedException {
        // TODO: the use of remoting is not optimal.
        // One remoting can execute "exists", "lastModified", and "delete" all at once.
        TopLevelItem item = Hudson.getInstance().getItem(jobName);
        if(item==null) {
            // no such project anymore
            LOGGER.fine("Directory "+dir+" is not owned by any project");
            return true;
        }

        if(!dir.exists())
            return false;

        // if younger than a month, keep it
        long now = new Date().getTime();
        if(dir.lastModified() + 30 * DAY > now) {
            LOGGER.fine("Directory "+dir+" is only "+ Util.getTimeSpanString(now-dir.lastModified())+" old, so not deleting");
            return false;
        }

        if (item instanceof AbstractProject) {
            AbstractProject p = (AbstractProject) item;
            Node lb = p.getLastBuiltOn();
            LOGGER.finer("Directory "+dir+" is last built on "+lb);
            if(lb!=null && lb.equals(n)) {
                // this is the active workspace. keep it.
                LOGGER.fine("Directory "+dir+" is the last workspace for "+p);
                return false;
            }
            
            if(!p.getScm().processWorkspaceBeforeDeletion(p,dir,n)) {
                LOGGER.fine("Directory deletion of "+dir+" is vetoed by SCM");
                return false;
            }
        }

        LOGGER.finer("Going to delete directory "+dir);
        return true;
    }

    private void process(Slave s) throws InterruptedException {
        listener.getLogger().println("Scanning "+s.getNodeName());

        try {
            FilePath path = s.getWorkspaceRoot();
            if(path==null)  return;

            List<FilePath> dirs = path.list(DIR_FILTER);
            if(dirs ==null) return;
            for (FilePath dir : dirs) {
                if(shouldBeDeleted(dir.getName(),dir,s))
                    delete(dir);
            }
        } catch (IOException e) {
            e.printStackTrace(listener.error("Failed on "+s.getNodeName()));
        }
    }

    private void delete(FilePath dir) throws InterruptedException {
        try {
            listener.getLogger().println("Deleting "+dir);
            dir.deleteRecursive();
        } catch (IOException e) {
            e.printStackTrace(listener.error("Failed to delete "+dir));
        }
    }


    private static class DirectoryFilter implements FileFilter, Serializable {
        public boolean accept(File f) {
            return f.isDirectory();
        }
        private static final long serialVersionUID = 1L;
    }
    private static final FileFilter DIR_FILTER = new DirectoryFilter();

    private static final long DAY = 1000*60*60*24;

    private static final Logger LOGGER = Logger.getLogger(WorkspaceCleanupThread.class.getName());
}
