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

import static hudson.Util.fileToPath;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.FilePath;
import hudson.Functions;
import hudson.Util;
import hudson.remoting.VirtualChannel;
import hudson.agents.WorkspaceList;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.MasterToAgentFileCallable;
import jenkins.model.Jenkins;
import jenkins.model.ModifiableTopLevelItemGroup;
import jenkins.util.SystemProperties;
import org.jenkinsci.Symbol;

/**
 * Clean up old left-over workspaces from agents.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension @Symbol("workspaceCleanup")
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
        List<Node> nodes = new ArrayList<>();
        Jenkins j = Jenkins.get();
        nodes.add(j);
        nodes.addAll(j.getNodes());
        for (TopLevelItem item : j.allItems(TopLevelItem.class)) {
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
                } catch (IOException | InterruptedException x) {
                    Functions.printStackTrace(x, listener.error("Failed to check " + node.getDisplayName()));
                    continue;
                }
                if (check) {
                    listener.getLogger().println("Deleting " + ws + " on " + node.getDisplayName());
                    try {
                        ws.act(new CleanupOldWorkspaces(retainForDays));
                    } catch (IOException | InterruptedException x) {
                        Functions.printStackTrace(x, listener.error("Failed to delete " + ws + " on " + node.getDisplayName()));
                    }
                }
            }
        }
    }

    private boolean shouldBeDeleted(@NonNull TopLevelItem item, FilePath dir, @NonNull Node n) throws IOException, InterruptedException {
        // TODO could also be good to add checkbox that lets users configure a workspace to never be auto-cleaned.

        // TODO check instead for SCMTriggerItem:
        if (item instanceof AbstractProject<?, ?>) {
            AbstractProject<?, ?> p = (AbstractProject<?, ?>) item;
            Node lb = p.getLastBuiltOn();
            LOGGER.log(Level.FINER, "Directory {0} is last built on {1}", new Object[] {dir, lb});
            if (lb != null && lb.equals(n)) {
                // this is the active workspace. keep it.
                LOGGER.log(Level.FINE, "Directory {0} is the last workspace for {1}", new Object[] {dir, p});
                return false;
            }

            if (!p.getScm().processWorkspaceBeforeDeletion((Job<?, ?>) p, dir, n)) {
                LOGGER.log(Level.FINE, "Directory deletion of {0} is vetoed by SCM", dir);
                return false;
            }
        }

        // TODO this may only check the last build in fact:
        if (item instanceof Job<?, ?>) {
            Job<?, ?> j = (Job<?, ?>) item;
            if (j.isBuilding()) {
                LOGGER.log(Level.FINE, "Job {0} is building, so not deleting", item.getFullDisplayName());
                return false;
            }
        }
        return true;
    }

    private static class CleanupOldWorkspaces extends MasterToAgentFileCallable<Void> {

        private final int retentionInDays;

        CleanupOldWorkspaces(int retentionInDays) {
            this.retentionInDays = retentionInDays;
        }

        @Override
        public Void invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
            File[] workspaces = null;
            File parentWs = f.getParentFile();
            if (parentWs != null) {
                workspaces = parentWs.listFiles(new ShouldBeDeletedFilter(this.retentionInDays, f.getName()));
            }

            if (workspaces != null) {
                for (File workspace : workspaces) {
                    LOGGER.log(Level.FINER, "Going to delete directory {0}", workspace);
                    Util.deleteRecursive(fileToPath(workspace), Path::toFile);
                }
            }
            return null;
        }
    }

    private static class ShouldBeDeletedFilter implements FileFilter, Serializable {

        private final int retentionInDays;

        private final String workspaceBaseName;

        ShouldBeDeletedFilter(int retentionInDays, String workspaceBaseName) {
            this.retentionInDays = retentionInDays;
            this.workspaceBaseName = workspaceBaseName;
        }

        @Override
        public boolean accept(File dir) {

            if (!dir.isDirectory()) {
                return false;
            }

            // if not the workspace or a workspace suffix
            if (!dir.getName().equals(workspaceBaseName) && !dir.getName().startsWith(workspaceBaseName + WorkspaceList.COMBINATOR)) {
                return false;
            }

            // if younger than a month, keep it
            long now = new Date().getTime();
            if (dir.lastModified() + this.retentionInDays * DAY > now) {
                LOGGER.log(Level.FINE, "Directory {0} is only {1} old, so not deleting", new Object[] {dir, Util.getTimeSpanString(now - dir.lastModified())});
                return false;
            }

            return true;
        }
    }

    private static final Logger LOGGER = Logger.getLogger(WorkspaceCleanupThread.class.getName());

    /**
     * Can be used to disable workspace clean up.
     */
    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "Accessible via System Groovy Scripts")
    public static boolean disabled = SystemProperties.getBoolean(WorkspaceCleanupThread.class.getName() + ".disabled");

    /**
     * How often the clean up should run. This is final as Jenkins will not reflect changes anyway.
     */
    public static final int recurrencePeriodHours = SystemProperties.getInteger(WorkspaceCleanupThread.class.getName() + ".recurrencePeriodHours", 24);

    /**
     * Number of days workspaces should be retained.
     */
    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "Accessible via System Groovy Scripts")
    public static int retainForDays = SystemProperties.getInteger(WorkspaceCleanupThread.class.getName() + ".retainForDays", 30);
}
