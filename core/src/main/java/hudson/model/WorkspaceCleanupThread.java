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

import hudson.Extension;
import hudson.ExtensionList;
import hudson.FilePath;
import hudson.Util;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import jenkins.model.Jenkins;
import jenkins.model.ModifiableTopLevelItemGroup;

/**
 * Clean up old left-over workspaces from agents.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class WorkspaceCleanupThread extends AsyncPeriodicWork {
    public WorkspaceCleanupThread() {
        super("Workspace clean-up");
    }

    @Override public long getRecurrencePeriod() {
        return recurrencePeriodHours * HOUR;
    }

    public static void invoke() {
        ExtensionList.lookup(AsyncPeriodicWork.class).get(WorkspaceCleanupThread.class).run();
    }

    @Override protected void execute(TaskListener listener) throws InterruptedException, IOException {
        if (disabled) {
            LOGGER.fine("Disabled. Skipping execution");
            return;
        }
        List<Node> nodes = new ArrayList<Node>();
        Jenkins j = Jenkins.getInstance();
        nodes.add(j);
        nodes.addAll(j.getNodes());
        for (TopLevelItem item : j.getAllItems(TopLevelItem.class)) {
            if (item instanceof ModifiableTopLevelItemGroup) { // no such thing as TopLevelItemGroup, and ItemGroup offers no access to its type parameter
                continue; // children will typically have their own workspaces as subdirectories; probably no real workspace of its own
            }
            listener.getLogger().println("Checking " + item.getFullDisplayName());
            for (Node node : nodes) {
                FilePath ws = node.getWorkspaceFor(item);
                if (ws == null) {
                    continue; // offline, fine
                }
                boolean check;
                try {
                    check = shouldBeDeleted(item, ws, node);
                } catch (IOException x) {
                    x.printStackTrace(listener.error("Failed to check " + node.getDisplayName()));
                    continue;
                } catch (InterruptedException x) {
                    x.printStackTrace(listener.error("Failed to check " + node.getDisplayName()));
                    continue;
                }
                if (check) {
                    listener.getLogger().println("Deleting " + ws + " on " + node.getDisplayName());
                    try {
                        ws.deleteRecursive();
                    } catch (IOException x) {
                        x.printStackTrace(listener.error("Failed to delete " + ws + " on " + node.getDisplayName()));
                    } catch (InterruptedException x) {
                        x.printStackTrace(listener.error("Failed to delete " + ws + " on " + node.getDisplayName()));
                    }
                }
            }
        }
    }

    private boolean shouldBeDeleted(@Nonnull TopLevelItem item, FilePath dir, @Nonnull Node n) throws IOException, InterruptedException {
        // TODO: the use of remoting is not optimal.
        // One remoting can execute "exists", "lastModified", and "delete" all at once.
        // (Could even invert master loop so that one FileCallable takes care of all known items.)
        if(!dir.exists()) {
            LOGGER.log(Level.FINE, "Directory {0} does not exist", dir);
            return false;
        }

        // if younger than a month, keep it
        long now = new Date().getTime();
        if(dir.lastModified() + retainForDays * DAY > now) {
            LOGGER.log(Level.FINE, "Directory {0} is only {1} old, so not deleting", new Object[] {dir, Util.getTimeSpanString(now-dir.lastModified())});
            return false;
        }

        // TODO could also be good to add checkbox that lets users configure a workspace to never be auto-cleaned.

        // TODO check instead for SCMTriggerItem:
        if (item instanceof AbstractProject<?,?>) {
            AbstractProject<?,?> p = (AbstractProject<?,?>) item;
            Node lb = p.getLastBuiltOn();
            LOGGER.log(Level.FINER, "Directory {0} is last built on {1}", new Object[] {dir, lb});
            if(lb!=null && lb.equals(n)) {
                // this is the active workspace. keep it.
                LOGGER.log(Level.FINE, "Directory {0} is the last workspace for {1}", new Object[] {dir, p});
                return false;
            }
            
            if(!p.getScm().processWorkspaceBeforeDeletion((Job<?, ?>) p,dir,n)) {
                LOGGER.log(Level.FINE, "Directory deletion of {0} is vetoed by SCM", dir);
                return false;
            }
        }

        LOGGER.log(Level.FINER, "Going to delete directory {0}", dir);
        return true;
    }

    private static final Logger LOGGER = Logger.getLogger(WorkspaceCleanupThread.class.getName());

    /**
     * Can be used to disable workspace clean up.
     */
    public static boolean disabled = Boolean.getBoolean(WorkspaceCleanupThread.class.getName()+".disabled");

    /**
     * How often the clean up should run. This is final as Jenkins will not reflect changes anyway.
     */
    public static final int recurrencePeriodHours = Integer.getInteger(WorkspaceCleanupThread.class.getName()+".recurrencePeriodHours", 24);

    /**
     * Number of days workspaces should be retained.
     */
    public static int retainForDays = Integer.getInteger(WorkspaceCleanupThread.class.getName()+".retainForDays", 30);
}
