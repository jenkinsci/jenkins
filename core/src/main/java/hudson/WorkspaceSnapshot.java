package hudson;

import hudson.model.Action;

/**
 * Represents a workspace snapshot created by {@link FileSystemProvisioner}.
 *
 * <p>
 * This class encapsulates a logic to use the snapshot elsewhere. 
 *
 * <p>
 * TODO: how to garbage-collect this object, especially for zfs?
 * perhaps when a new build is started?
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class WorkspaceSnapshot implements Action {
    public abstract void restoreTo(FilePath dst);
}
