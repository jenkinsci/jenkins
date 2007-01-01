package hudson.scm;

import hudson.ExtensionPoint;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.Describable;
import hudson.model.Project;
import hudson.model.TaskListener;
import hudson.model.AbstractProject;
import hudson.model.AbstractBuild;

import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * Captures the configuration information in it.
 *
 * <p>
 * To register a custom {@link SCM} implementation from a plugin,
 * add it to {@link SCMS#SCMS}.
 *
 * @author Kohsuke Kawaguchi
 */
public interface SCM extends Describable<SCM>, ExtensionPoint {

    /**
     * Checks if there has been any changes to this module in the repository.
     *
     * TODO: we need to figure out a better way to communicate an error back,
     * so that we won't keep retrying the same node (for example a slave might be down.)
     *
     * @param project
     *      The project to check for updates
     * @param launcher
     *      Abstraction of the machine where the polling will take place.
     * @param workspace
     *      The workspace directory that contains baseline files.
     * @param listener
     *      Logs during the polling should be sent here.
     *
     * @return true
     *      if the change is detected.
     *
     * @throws InterruptedException
     *      interruption is usually caused by the user aborting the computation.
     *      this exception should be simply propagated all the way up.
     */
    boolean pollChanges(AbstractProject project, Launcher launcher, FilePath workspace, TaskListener listener) throws IOException, InterruptedException;

    /**
     * Obtains a fresh workspace of the module(s) into the specified directory
     * of the specified machine.
     *
     * <p>
     * The "update" operation can be performed instead of a fresh checkout if
     * feasible.
     *
     * <p>
     * This operation should also capture the information necessary to tag the workspace later.
     *
     * @param launcher
     *      Abstracts away the machine that the files will be checked out.
     * @param workspace
     *      a directory to check out the source code. May contain left-over
     *      from the previous build.
     * @param changelogFile
     *      Upon a successful return, this file should capture the changelog.
     *      When there's no change, this file should contain an empty entry.
     *      See {@link AbstractCVSFamilySCM#createEmptyChangeLog(File, BuildListener, String)}.
     * @return
     *      false if the operation fails. The error should be reported to the listener.
     *      Otherwise return the changes included in this update (if this was an update.)
     *
     * @throws InterruptedException
     *      interruption is usually caused by the user aborting the build.
     *      this exception will cause the build to fail.
     */
    boolean checkout(AbstractBuild build, Launcher launcher, FilePath workspace, BuildListener listener, File changelogFile) throws IOException, InterruptedException;

    /**
     * Adds environmental variables for the builds to the given map.
     */
    void buildEnvVars(Map<String,String> env);

    /**
     * Gets the top directory of the checked out module.
     * @param workspace
     */
    FilePath getModuleRoot(FilePath workspace);

    /**
     * The returned object will be used to parse <tt>changelog.xml</tt>.
     */
    ChangeLogParser createChangeLogParser();
}
