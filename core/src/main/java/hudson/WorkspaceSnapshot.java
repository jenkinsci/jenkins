package hudson;

import hudson.model.Action;
import hudson.model.TaskListener;
import hudson.model.AbstractBuild;

import java.io.IOException;

/**
 * Represents a workspace snapshot created by {@link FileSystemProvisioner}.
 *
 * <p>
 * This class encapsulates a logic to use the snapshot elsewhere.
 * The instance will be persisted with the {@link AbstractBuild} object
 * as an {@link Action}.
 *
 * <p>
 * TODO: how to garbage-collect this object, especially for zfs?
 * perhaps when a new build is started?
 *
 * @see FileSystemProvisioner
 * @author Kohsuke Kawaguchi
 */
public abstract class WorkspaceSnapshot implements Action {
    /**
     * Restores the snapshot to the given file system location.
     *
     * @param owner
     *      The build that owns this action. It's always the same value for any given {@link WorkspaceSnapshot},
     *      but passed in separately so that implementations don't need to keep them in fields.
     * @param dst
     *      The file path to which the snapshot shall be restored to.
     * @param listener
     *      Send the progress of the restoration to this listener. Never null.
     */
    public abstract void restoreTo(AbstractBuild<?,?> owner, FilePath dst, TaskListener listener) throws IOException, InterruptedException;

    public String getIconFileName() {
        // by default, hide from the UI
        return null;
    }

    public String getDisplayName() {
        return "Workspace";
    }

    public String getUrlName() {
        return "workspace";
    }
}
