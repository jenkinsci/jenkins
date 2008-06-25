package hudson.scm;

import hudson.ExtensionPoint;
import hudson.FilePath;
import hudson.Launcher;
import hudson.security.PermissionGroup;
import hudson.security.Permission;
import hudson.tasks.Builder;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Describable;
import hudson.model.TaskListener;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;

/**
 * Captures the configuration information in it.
 *
 * <p>
 * To register a custom {@link SCM} implementation from a plugin,
 * add it to {@link SCMS#SCMS}.
 *
 * <p>
 * Use the "project-changes" view to render change list to be displayed
 * at the project level. The default implementation simply aggregates
 * change lists from builds, but your SCM can provide different views.
 * The view gets the "builds" variable which is a list of builds that are
 * selected for the display.
 *
 * <p>
 * If you are interested in writing a subclass in a plugin,
 * also take a look at <a href="http://hudson.gotdns.com/wiki/display/HUDSON/Writing+an+SCM+plugin">
 * "Writing an SCM plugin"</a> wiki article.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class SCM implements Describable<SCM>, ExtensionPoint {
    /**
     * Stores {@link AutoBrowserHolder}. Lazily created.
     */
    private transient AutoBrowserHolder autoBrowserHolder;

    /**
     * Returns the {@link RepositoryBrowser} for files
     * controlled by this {@link SCM}.
     *
     * @return
     *      null to indicate that there's no explicitly configured browser
     *      for this SCM instance.
     *
     * @see #getEffectiveBrowser()
     */
    public RepositoryBrowser getBrowser() {
        return null;
    }

    /**
     * Returns the applicable {@link RepositoryBrowser} for files
     * controlled by this {@link SCM}.
     *
     * <p>
     * This method attempts to find applicable browser
     * from other job configurations.
     */
    public final RepositoryBrowser getEffectiveBrowser() {
        RepositoryBrowser b = getBrowser();
        if(b!=null)
            return b;
        if(autoBrowserHolder==null)
            autoBrowserHolder = new AutoBrowserHolder(this);
        return autoBrowserHolder.get();

    }

    /**
     * Returns true if this SCM supports
     * {@link #pollChanges(AbstractProject, Launcher, FilePath, TaskListener) polling}.
     *
     * @since 1.105
     */
    public boolean supportsPolling() {
        return true;
    }
    
    /**
     * Returns true if this SCM requires a checked out workspace for doing polling.
     *
     * <p>
     * This flag affects the behavior of Hudson when a job lost its workspace
     * (typically due to a slave outage.) If this method returns false and
     * polling is configured, then that would immediately trigger a new build.
     *
     * <p>
     * The default implementation returns true.
     *
     * <p>
     * See issue #1348 for more discussion of this feature.
     *
     * @since 1.196
     */
    public boolean requiresWorkspaceForPolling() {
    	return true;
    }

    /**
     * Checks if there has been any changes to this module in the repository.
     *
     * TODO: we need to figure out a better way to communicate an error back,
     * so that we won't keep retrying the same node (for example a slave might be down.)
     *
     * <p>
     * If the SCM doesn't implement polling, have the {@link #supportsPolling()} method
     * return false.
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
     *
     * @see #supportsPolling()
     */
    public abstract boolean pollChanges(AbstractProject project, Launcher launcher, FilePath workspace, TaskListener listener) throws IOException, InterruptedException;

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
     *      See {@link #createEmptyChangeLog(File, BuildListener, String)}.
     * @return
     *      false if the operation fails. The error should be reported to the listener.
     *      Otherwise return the changes included in this update (if this was an update.)
     *
     * @throws InterruptedException
     *      interruption is usually caused by the user aborting the build.
     *      this exception will cause the build to fail.
     */
    public abstract boolean checkout(AbstractBuild build, Launcher launcher, FilePath workspace, BuildListener listener, File changelogFile) throws IOException, InterruptedException;

    /**
     * Adds environmental variables for the builds to the given map.
     *
     * <p>
     * This can be used to propagate information from SCM to builds
     * (for example, SVN revision number.)
     */
    public void buildEnvVars(AbstractBuild build, Map<String, String> env) {
        // default implementation is noop.
    }

    /**
     * Gets the top directory of the checked out module.
     *
     * <p>
     * Often SCMs have to create a directory inside a workspace, which
     * creates directory layout like this:
     *
     * <pre>
     * workspace  <- workspace root
     *  +- xyz    <- directory checked out by SCM
     *      +- CVS
     *      +- build.xml  <- user file
     * </pre>
     *
     * <p>
     * Many builders, like Ant or Maven, works off the specific user file
     * at the top of the checked out module (in the above case, that would
     * be <tt>xyz/build.xml</tt>), yet the builder doesn't know the "xyz"
     * part; that comes from SCM.
     *
     * <p>
     * Collaboration between {@link Builder} and {@link SCM} allows
     * Hudson to find build.xml wihout asking the user to enter "xyz" again.
     *
     * <p>
     * This method is for this purpose. It takes the workspace
     * root as a parameter, and expected to return the directory
     * that was checked out from SCM.
     *
     * <p>
     * If this SCM is configured to create a directory, try to
     * return that directory so that builders can work seamlessly.
     *
     * <p>
     * If SCM doesn't need to create any directory inside workspace,
     * or in any other tricky cases, it should revert to the default
     * implementation, which is to just return the parameter.
     *
     * @param workspace
     *      The workspace root directory.
     */
    public FilePath getModuleRoot(FilePath workspace) {
        return workspace;
    }

    /**
     * Gets the top directories of all the checked out modules.
     *
     * <p>
     * Some SCMs support checking out multiple modules inside a workspace, which
     * creates directory layout like this:
     *
     * <pre>
     * workspace  <- workspace root
     *  +- xyz    <- directory checked out by SCM
     *      +- .svn
     *      +- build.xml  <- user file
     *  +- abc    <- second module from different SCM root
     *      +- .svn
     *      +- build.xml  <- user file
     * </pre>
     *
     * This method takes the workspace root as a parameter, and is expected to return
     * all the module roots that were checked out from SCM.
     *
     * <p>
     * For normal SCMs, the array will be of length <code>1</code> and it's contents
     * will be identical to calling {@link getModuleRoot(FilePath)}.
     *
     * @param workspace The workspace root directory
     * @return An array of all module roots.
     */
    public FilePath[] getModuleRoots(FilePath workspace) {
        return new FilePath[] { getModuleRoot(workspace), };
    }

    /**
     * The returned object will be used to parse <tt>changelog.xml</tt>.
     */
    public abstract ChangeLogParser createChangeLogParser();

    public abstract SCMDescriptor<?> getDescriptor();

    protected final boolean createEmptyChangeLog(File changelogFile, BuildListener listener, String rootTag) {
        try {
            FileWriter w = new FileWriter(changelogFile);
            w.write("<"+rootTag +"/>");
            w.close();
            return true;
        } catch (IOException e) {
            e.printStackTrace(listener.error(e.getMessage()));
            return false;
        }
    }

    protected final String nullify(String s) {
        if(s==null)     return null;
        if(s.trim().length()==0)    return null;
        return s;
    }

    public static final PermissionGroup PERMISSIONS = new PermissionGroup(SCM.class, Messages._SCM_Permissions_Title());
    /**
     * Permission to create new tags.
     * @since 1.171
     */
    public static final Permission TAG = new Permission(PERMISSIONS,"Tag", Permission.CREATE);
}
