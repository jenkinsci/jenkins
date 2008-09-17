package hudson;

import hudson.model.Descriptor;
import hudson.model.TaskListener;
import hudson.model.AbstractBuild;

import java.io.IOException;

/**
 * {@link Descriptor} for {@link FileSystemProvisioner}.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class FileSystemProvisionerDescriptor extends Descriptor<FileSystemProvisioner> implements ExtensionPoint {
    protected FileSystemProvisionerDescriptor(Class<? extends FileSystemProvisioner> clazz) {
        super(clazz);
    }

    /**
     * Called to clean up a workspace that may potentially belong to this {@link FileSystemProvisioner}.
     *
     * <p>
     * Because users may modify the file system behind Hudson, and slaves may come and go when
     * configuration changes hapen, in general case Hudson is unable to keep track of which jobs
     * have workspaces in which slaves.
     *
     * <p>
     * So instead we rey on a garbage collection mechanism, to look at workspaces left in the file system
     * without the contextual information of the owner project, and try to clean that up.
     *
     * <p>
     * This method is called to do this, after Hudson determines that the workspace should be deleted
     * to reclaim disk space. The implementation of this method is expected to sniff the contents of
     * the workspace, and if it looks like the one created by {@link FileSystemProvisioner#prepareWorkspace(AbstractBuild, TaskListener)},
     * perform the necessary deletion operation, and return <tt>true</tt>.
     *
     * <p>
     * If the workspace isn't the one created by this {@link FileSystemProvisioner}, or if the
     * workspace can be simply deleted by {@link FilePath#deleteRecursive()}, then simply
     * return <tt>false</tt> to give other {@link FileSystemProvisionerDescriptor}s a chance to
     * discard them.
     *
     * @param ws
     *      The workspace directory to be removed.
     * @param listener
     *      The status of the operation, error message, etc., should go here.
     * @return
     *      true if this {@link FileSystemProvisionerDescriptor} is responsible for de-alocating the workspace.
     *      false otherwise, in which case the other {@link FileSystemProvisionerDescriptor}s are asked to
     *      clean up the workspace.
     */
    public abstract boolean discard(FilePath ws, TaskListener listener) throws IOException, InterruptedException;
}
