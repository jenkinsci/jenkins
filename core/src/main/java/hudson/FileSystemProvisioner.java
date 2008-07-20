package hudson;

import hudson.matrix.MatrixBuild;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Computer;
import hudson.model.Job;

import java.io.IOException;
import java.io.File;

/**
 * Prepares and provisions workspaces for {@link AbstractProject}s.
 *
 * <p>
 *
 *
 * <p>
 * STILL A WORK IN PROGRESS. SUBJECT TO CHANGE!
 *
 * TODO: is this per {@link Computer}? Per {@link Job}?
 *
 * @author Kohsuke Kawaguchi
 * @since 1.235
 */
public abstract class FileSystemProvisioner implements ExtensionPoint {
    /**
     * Called very early in the build (before a build places any files
     * in the workspace, such as SCM checkout) to provision a workspace
     * for the build.
     *
     * <p>
     * This method can prepare the underlying file system in preparation
     * for the later {@link #snapshot(AbstractBuild)}.
     * 
     *
     * TODO: the method needs to be able to see the snapshot would
     * be later needed. In fact, perhaps we should only call this method
     * when Hudson knows that a snapshot is later needed?
     */
    public abstract void prepareWorkspace(AbstractBuild<?,?> build) throws IOException;

    public abstract void discardWorkspace(AbstractProject<?,?> project) throws IOException;

    public abstract void moveWorkspace(AbstractProject<?,?> project, File oldWorkspace, File newWorkspace) throws IOException;

    /**
     * Obtains the snapshot of the workspace of the given build.
     *
     * <p>
     * The state of the build when this method is invoked depends on
     * the project type. Most would call this at the end of the build,
     * but for example {@link MatrixBuild} would call this after
     * SCM check out so that the state of the fresh workspace
     * can be then propagated to elsewhere.
     */
    public abstract WorkspaceSnapshot snapshot(AbstractBuild<?,?> build) throws IOException;

}
