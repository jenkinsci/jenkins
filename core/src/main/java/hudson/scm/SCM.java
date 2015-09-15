/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Stephen Connolly, InfraDNA, Inc.
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
package hudson.scm;

import hudson.AbortException;
import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.ExtensionPoint;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.Api;
import hudson.model.BuildListener;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Job;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.TopLevelItemDescriptor;
import hudson.model.WorkspaceCleanupThread;
import hudson.security.Permission;
import hudson.security.PermissionGroup;
import hudson.security.PermissionScope;
import hudson.tasks.Builder;
import hudson.util.IOUtils;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

/**
 * Captures the configuration information in it.
 *
 * <p>
 * To register a custom {@link SCM} implementation from a plugin,
 * put {@link Extension} on your {@link SCMDescriptor}.
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
 * also take a look at <a href="http://wiki.jenkins-ci.org/display/JENKINS/Writing+an+SCM+plugin">
 * "Writing an SCM plugin"</a> wiki article.
 *
 * @author Kohsuke Kawaguchi
 */
@ExportedBean
public abstract class SCM implements Describable<SCM>, ExtensionPoint {
    /**
     * Stores {@link AutoBrowserHolder}. Lazily created.
     */
    private transient AutoBrowserHolder autoBrowserHolder;

    /**
     * Expose {@link SCM} to the remote API.
     */
    public Api getApi() {
        return new Api(this);
    }

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
    public @CheckForNull RepositoryBrowser<?> getBrowser() {
        return null;
    }

    /**
     * Type of this SCM.
     *
     * Exposed so that the client of the remote API can tell what SCM this is.
     */
    @Exported
    public String getType() {
        return getClass().getName();
    }

    /**
     * Returns the applicable {@link RepositoryBrowser} for files
     * controlled by this {@link SCM}.
     * @see #guessBrowser
     * @see SCMDescriptor#isBrowserReusable
     */
    @Exported(name="browser")
    public final @CheckForNull RepositoryBrowser<?> getEffectiveBrowser() {
        RepositoryBrowser<?> b = getBrowser();
        if(b!=null)
            return b;
        if(autoBrowserHolder==null)
            autoBrowserHolder = new AutoBrowserHolder(this);
        return autoBrowserHolder.get();

    }

    /**
     * Returns true if this SCM supports
     * {@link #poll(AbstractProject, Launcher, FilePath, TaskListener, SCMRevisionState) poling}.
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
     * This flag also affects the mutual exclusion control between builds and polling.
     * If this methods returns false, polling will continue asynchronously even
     * when a build is in progress, but otherwise the polling activity is blocked
     * if a build is currently using a workspace.
     *
     * <p>
     * The default implementation returns true. If polling without workspace is supported, then
     * ${@link #compareRemoteRevisionWith(Job, Launcher, TaskListener, SCMRevisionState)} shoulc
     * be overridden to implement workspace-free polling.
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
     * Called before a workspace is deleted on the given node, to provide SCM an opportunity to perform clean up.
     *
     * <p>
     * Hudson periodically scans through all the slaves and removes old workspaces that are deemed unnecessary.
     * This behavior is implemented in {@link WorkspaceCleanupThread}, and it is necessary to control the
     * disk consumption on slaves. If we don't do this, in a long run, all the slaves will have workspaces
     * for all the projects, which will be prohibitive in big Hudson.
     *
     * <p>
     * However, some SCM implementations require that the server be made aware of deletion of the local workspace,
     * and this method provides an opportunity for SCMs to perform such a clean-up act.
     *
     * <p>
     * This call back is invoked after Hudson determines that a workspace is unnecessary, but before the actual
     * recursive directory deletion happens.
     *
     * <p>
     * Note that this method does not guarantee that such a clean up will happen. For example, slaves can be
     * taken offline by being physically removed from the network, and in such a case there's no opportunity
     * to perform this clean up.
     *
     * <p>
     * This method is also invoked when the project is deleted.
     *
     * @param project
     *      The project that owns this {@link SCM}. This is always the same object for a particular instance
     *      of {@link SCM}. Just passed in here so that {@link SCM} itself doesn't have to remember the value.
     * @param workspace
     *      The workspace which is about to be deleted. This can be a remote file path.
     * @param node
     *      The node that hosts the workspace. SCM can use this information to determine the course of action.
     *
     * @return
     *      true if {@link SCM} is OK to let Hudson proceed with deleting the workspace.
     *      False to veto the workspace deletion.
     * 
     * @since 1.568
     */
    public boolean processWorkspaceBeforeDeletion(@Nonnull Job<?,?> project, @Nonnull FilePath workspace, @Nonnull Node node) throws IOException, InterruptedException {
        if (project instanceof AbstractProject) {
            return processWorkspaceBeforeDeletion((AbstractProject) project, workspace, node);
        } else {
            return true;
        }
    }

    @Deprecated
    public boolean processWorkspaceBeforeDeletion(AbstractProject<?,?> project, FilePath workspace, Node node) throws IOException, InterruptedException {
        if (Util.isOverridden(SCM.class, getClass(), "processWorkspaceBeforeDeletion", Job.class, FilePath.class, Node.class)) {
            return processWorkspaceBeforeDeletion((Job) project, workspace, node);
        } else {
            return true;
        }
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
     *      Abstraction of the machine where the polling will take place. If SCM declares
     *      that {@linkplain #requiresWorkspaceForPolling() the polling doesn't require a workspace}, this parameter is null.
     * @param workspace
     *      The workspace directory that contains baseline files. If SCM declares
     *      that {@linkplain #requiresWorkspaceForPolling() the polling doesn't require a workspace}, this parameter is null.
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
     *
     * @deprecated as of 1.345
     *      Override {@link #calcRevisionsFromBuild(AbstractBuild, Launcher, TaskListener)} and
     *      {@link #compareRemoteRevisionWith(AbstractProject, Launcher, FilePath, TaskListener, SCMRevisionState)} for implementation.
     *
     *      The implementation is now separated in two pieces, one that computes the revision of the current workspace,
     *      and the other that computes the revision of the remote repository.
     *
     *      Call {@link #poll(AbstractProject, Launcher, FilePath, TaskListener, SCMRevisionState)} for use instead.
     */
    @Deprecated
    public boolean pollChanges(AbstractProject<?,?> project, Launcher launcher, FilePath workspace, TaskListener listener) throws IOException, InterruptedException {
        // up until 1.336, this method was abstract, so everyone should have overridden this method
        // without calling super.pollChanges. So the compatibility implementation is purely for
        // new implementations that doesn't override this method.
        throw new AbstractMethodError("you must override compareRemoteRevisionWith");
    }

    /**
     * Calculates the {@link SCMRevisionState} that represents the state of the workspace of the given build.
     *
     * <p>
     * The returned object is then fed into the
     * {@link #compareRemoteRevisionWith(AbstractProject, Launcher, FilePath, TaskListener, SCMRevisionState)} method
     * as the baseline {@link SCMRevisionState} to determine if the build is necessary.
     *
     * <p>
     * This method is called after source code is checked out for the given build (that is, after
     * {@link SCM#checkout(Run, Launcher, FilePath, TaskListener, File)} has finished successfully.)
     *
     * <p>
     * The obtained object is added to the build as an {@link Action} for later retrieval. As an optimization,
     * {@link SCM} implementation can choose to compute {@link SCMRevisionState} and add it as an action
     * during check out, in which case this method will not called. 
     *
     * @param build
     *      The calculated {@link SCMRevisionState} is for the files checked out in this build.
     *      If {@link #requiresWorkspaceForPolling()} returns true, Hudson makes sure that the workspace of this
     *      build is available and accessible by the callee.
     * @param workspace the location of the checkout; normally not null, since this will normally be called immediately after checkout,
     *                  though could be null if data is being loaded from a very old version of Jenkins and the SCM declares that it does not require a workspace for polling
     * @param launcher
     *      Abstraction of the machine where the polling will take place. Nullness matches that of {@code workspace}.
     * @param listener
     *      Logs during the polling should be sent here.
     *
     * @throws InterruptedException
     *      interruption is usually caused by the user aborting the computation.
     *      this exception should be simply propagated all the way up. 
     * @since 1.568
     */
    public @CheckForNull SCMRevisionState calcRevisionsFromBuild(@Nonnull Run<?,?> build, @Nullable FilePath workspace, @Nullable Launcher launcher, @Nonnull TaskListener listener) throws IOException, InterruptedException {
        if (build instanceof AbstractBuild && Util.isOverridden(SCM.class, getClass(), "calcRevisionsFromBuild", AbstractBuild.class, Launcher.class, TaskListener.class)) {
            return calcRevisionsFromBuild((AbstractBuild) build, launcher, listener);
        } else {
            throw new AbstractMethodError("you must override the new calcRevisionsFromBuild overload");
        }
    }

    @Deprecated
    public SCMRevisionState calcRevisionsFromBuild(AbstractBuild<?,?> build, Launcher launcher, TaskListener listener) throws IOException, InterruptedException {
        return calcRevisionsFromBuild(build, launcher != null ? build.getWorkspace() : null, launcher, listener);
    }

    @Deprecated
    public SCMRevisionState _calcRevisionsFromBuild(AbstractBuild<?, ?> build, Launcher launcher, TaskListener listener) throws IOException, InterruptedException {
        return calcRevisionsFromBuild(build, launcher, listener);
    }


    /**
     * Compares the current state of the remote repository against the given baseline {@link SCMRevisionState}.
     * <p>
     * This method is intended to <i>estimate</i> if the remote repository state has changed compared to current
     * baseline, as a lightweight check, and as a result can trigger a finer grained comparison using
     * {@link #compareRemoteRevisionWith(Job, Launcher, FilePath, TaskListener, SCMRevisionState)} if the SCM
     * implementation do claim it require a workspace - see ${@link #requiresWorkspaceForPolling()}.
     * <p>
     * If the lightweight check can determine there's no change, returning ${@link PollingResult.Change#NONE}, then
     * the polling mechanism can just stop and won't need to acquire a workspace.
     * <p>
     * If the lightweight check can determine changes, returning ${@link PollingResult.Change#SIGNIFICANT}, and
     * ${@link #requiresWorkspaceForPolling()} return <code>false</code>, a full polling cycle will be executed,
     * invoking {@link #compareRemoteRevisionWith(Job, Launcher, FilePath, TaskListener, SCMRevisionState)}.
     * with a workspace.
     * @since TODO
     */
    public PollingResult compareRemoteRevisionWith(@Nonnull Job<?,?> project, @Nullable Launcher launcher, @Nonnull TaskListener listener, @Nonnull SCMRevisionState baseline) throws IOException, InterruptedException {
        if (requiresWorkspaceForPolling())
             return PollingResult.SIGNIFICANT;
        else
             // for backward compatibility, we invoke compareRemoteRevisionWith with workspace=null
             return compareRemoteRevisionWith(project, launcher, null, listener, baseline);
    }


    /**
     * Compares the current state of the remote repository against the given baseline {@link SCMRevisionState}.
     *
     * <p>
     * Conceptually, the act of polling is to take two states of the repository and to compare them to see
     * if there's any difference. In practice, however, comparing two arbitrary repository states is an expensive
     * operation, so in this abstraction, we chose to mix (1) the act of building up a repository state and
     * (2) the act of comparing it with the earlier state, so that SCM implementations can implement this
     * more easily. 
     *
     * <p>
     * Multiple invocations of this method may happen over time to make sure that the remote repository
     * is "quiet" before Hudson schedules a new build.
     *
     * @param project
     *      The project to check for updates
     * @param launcher
     *      Abstraction of the machine where the polling will take place. If SCM declares
     *      that {@linkplain #requiresWorkspaceForPolling() the polling doesn't require a workspace}, this parameter is null.
     * @param workspace
     *      The workspace directory that contains baseline files. Before 1.TODO workspace could be null to support polling
     *      without a workspace. If SCM implementation do support this feature, better override
     *      ${@link #compareRemoteRevisionWith(Job, Launcher, TaskListener, SCMRevisionState)}
     * @param listener
     *      Logs during the polling should be sent here.
     * @param baseline
     *      The baseline of the comparison. This object is the return value from earlier
     *      {@link #compareRemoteRevisionWith(AbstractProject, Launcher, FilePath, TaskListener, SCMRevisionState)} or
     *      {@link #calcRevisionsFromBuild(AbstractBuild, Launcher, TaskListener)}.
     *
     * @return
     *      This method returns multiple values that are bundled together into the {@link PollingResult} value type.
     *      {@link PollingResult#baseline} should be the value of the baseline parameter, {@link PollingResult#remote}
     *      is the current state of the remote repository (this object only needs to be understandable to the future
     *      invocations of this method),
     *      and {@link PollingResult#change} that indicates the degree of changes found during the comparison.
     *
     * @throws InterruptedException
     *      interruption is usually caused by the user aborting the computation.
     *      this exception should be simply propagated all the way up.
     * @since 1.568
     */
    public PollingResult compareRemoteRevisionWith(@Nonnull Job<?,?> project, @Nullable Launcher launcher, @Nonnull FilePath workspace, @Nonnull TaskListener listener, @Nonnull SCMRevisionState baseline) throws IOException, InterruptedException {
        if (project instanceof AbstractProject && Util.isOverridden(SCM.class, getClass(), "compareRemoteRevisionWith", AbstractProject.class, Launcher.class, FilePath.class, TaskListener.class, SCMRevisionState.class)) {
            return compareRemoteRevisionWith((AbstractProject) project, launcher, workspace, listener, baseline);
        } else {
            throw new AbstractMethodError("you must override the new overload of compareRemoteRevisionWith");
        }
    }

    @Deprecated
    protected PollingResult compareRemoteRevisionWith(AbstractProject<?,?> project, Launcher launcher, FilePath workspace, TaskListener listener, SCMRevisionState baseline) throws IOException, InterruptedException {
        return compareRemoteRevisionWith((Job) project, launcher, workspace, listener, baseline);
    }

    /**
     * Convenience method for the caller to handle the backward compatibility between pre 1.345 SCMs.
     */
    public final PollingResult poll(AbstractProject<?,?> project, Launcher launcher, FilePath workspace, TaskListener listener, SCMRevisionState baseline) throws IOException, InterruptedException {
        if (is1_346OrLater()) {
            // This is to work around HUDSON-5827 in a general way.
            // don't let the SCM.compareRemoteRevisionWith(...) see SCMRevisionState that it didn't produce.
            SCMRevisionState baseline2;
            if (baseline!=SCMRevisionState.NONE) {
                baseline2 = baseline;
            } else {
                baseline2 = calcRevisionsFromBuild(project.getLastBuild(), launcher, listener);
            }

            return compareRemoteRevisionWith(project, launcher, workspace, listener, baseline2);
        } else {
            return pollChanges(project,launcher,workspace,listener) ? PollingResult.SIGNIFICANT : PollingResult.NO_CHANGES;
        }
    }

    private boolean is1_346OrLater() {
        for (Class<?> c = getClass(); c != SCM.class; c = c.getSuperclass()) {
            try {
                c.getDeclaredMethod("compareRemoteRevisionWith", AbstractProject.class, Launcher.class, FilePath.class, TaskListener.class, SCMRevisionState.class);
                return true;
            } catch (NoSuchMethodException e) {
                try {
                    c.getDeclaredMethod("compareRemoteRevisionWith", Job.class, Launcher.class, FilePath.class, TaskListener.class, SCMRevisionState.class);
                    return true;
                } catch (NoSuchMethodException e2) {}
            }
        }
        return false;
    }

    /**
     * Should create a key by which this SCM configuration might be distinguished from others in the same project.
     * Should be invariable across builds but otherwise as distinctive as possible.
     * <p>Could include information such as the relative paths used in {@link #getModuleRoots(FilePath, AbstractBuild)},
     * and/or configured repository URLs and branch names, and/or labels set for this purpose by the user.
     * <p>The result may be used for various purposes, but it may be long and/or include URL-unsafe characters,
     * so to use in a URL path component you may need to first wrap it in {@link Util#getDigestOf(String)} or otherwise encode it.
     * @return by default, just {@link #getType}
     * @since 1.568
     */
    public @Nonnull String getKey() {
        return getType();
    }

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
     *      See {@link #createEmptyChangeLog(File, TaskListener, String)}.
     *      May be null, in which case no changelog was requested.
     * @param  baseline version from the previous build to use for changelog creation, if requested and available
     * @throws InterruptedException
     *      interruption is usually caused by the user aborting the build.
     *      this exception will cause the build to be aborted.
     * @throws AbortException in case of a routine failure
     * @since 1.568
     */
    public void checkout(@Nonnull Run<?,?> build, @Nonnull Launcher launcher, @Nonnull FilePath workspace, @Nonnull TaskListener listener, @CheckForNull File changelogFile, @CheckForNull SCMRevisionState baseline) throws IOException, InterruptedException {
        if (build instanceof AbstractBuild && listener instanceof BuildListener && Util.isOverridden(SCM.class, getClass(), "checkout", AbstractBuild.class, Launcher.class, FilePath.class, BuildListener.class, File.class)) {
            if (changelogFile == null) {
                changelogFile = File.createTempFile("changelog", ".xml");
                try {
                    if (!checkout((AbstractBuild) build, launcher, workspace, (BuildListener) listener, changelogFile)) {
                        throw new AbortException();
                    }
                } finally {
                    Util.deleteFile(changelogFile);
                }
            } else {
                if (!checkout((AbstractBuild) build, launcher, workspace, (BuildListener) listener, changelogFile)) {
                    throw new AbortException();
                }
            }
        } else {
            throw new AbstractMethodError("you must override the new overload of checkout");
        }
    }

    @Deprecated
    public boolean checkout(AbstractBuild<?,?> build, Launcher launcher, FilePath workspace, BuildListener listener, @Nonnull File changelogFile) throws IOException, InterruptedException {
        AbstractBuild<?,?> prev = build.getPreviousBuild();
        checkout((Run) build, launcher, workspace, listener, changelogFile, prev != null ? prev.getAction(SCMRevisionState.class) : null);
        return true;
    }

    /**
     * Get a chance to do operations after the workspace i checked out and the changelog is written.
     * @since 1.568
     */
    public void postCheckout(@Nonnull Run<?,?> build, @Nonnull Launcher launcher, @Nonnull FilePath workspace, @Nonnull TaskListener listener) throws IOException, InterruptedException {
        if (build instanceof AbstractBuild && listener instanceof BuildListener) {
            postCheckout((AbstractBuild) build, launcher, workspace, (BuildListener) listener);
        }
    }

    @Deprecated
    public void postCheckout(AbstractBuild<?,?> build, Launcher launcher, FilePath workspace, BuildListener listener) throws IOException, InterruptedException {
        if (Util.isOverridden(SCM.class, getClass(), "postCheckout", Run.class, Launcher.class, FilePath.class, TaskListener.class)) {
            postCheckout((Run) build, launcher, workspace, listener);
        }
        /* Default implementation is noop */
    }

    /**
     * Adds environmental variables for the builds to the given map.
     *
     * <p>
     * This can be used to propagate information from SCM to builds
     * (for example, SVN revision number.)
     *
     * <p>
     * This method is invoked whenever someone does {@link AbstractBuild#getEnvironment(TaskListener)}, which
     * can be before/after your checkout method is invoked. So if you are going to provide information about
     * check out (like SVN revision number that was checked out), be prepared for the possibility that the
     * check out hasn't happened yet.
     */
    // TODO is an equivalent for Run needed?
    public void buildEnvVars(AbstractBuild<?,?> build, Map<String, String> env) {
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
     * Hudson to find build.xml without asking the user to enter "xyz" again.
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
     * @param build
     *      The build for which the module root is desired.
     *      This parameter is null when existing legacy code calls deprecated {@link #getModuleRoot(FilePath)}.
     *      Handle this situation gracefully if your can, but otherwise you can just fail with an exception, too.
     *
     * @since 1.382
     */
    // TODO perhaps deprecate all replace with a single getModuleRoots(FilePath, Run)
    public FilePath getModuleRoot(FilePath workspace, AbstractBuild build) {
        // For backwards compatibility, call the one argument version of the method.
        return getModuleRoot(workspace);
    }
    
    /**
     * @deprecated since 1.382
     *      Use/override {@link #getModuleRoot(FilePath, AbstractBuild)} instead.
     */
    @Deprecated
    public FilePath getModuleRoot(FilePath workspace) {
        if (Util.isOverridden(SCM.class,getClass(),"getModuleRoot", FilePath.class,AbstractBuild.class))
            // if the subtype already implements newer getModuleRoot(FilePath,AbstractBuild), call that.
            return getModuleRoot(workspace,null);

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
     * will be identical to calling {@link #getModuleRoot(FilePath, AbstractBuild)}.
     *
     * @param workspace The workspace root directory
     * @param build
     *      The build for which the module roots are desired.
     *      This parameter is null when existing legacy code calls deprecated {@link #getModuleRoot(FilePath)}.
     *      Handle this situation gracefully if your can, but otherwise you can just fail with an exception, too.
     *
     * @return An array of all module roots.
     * @since 1.382
     */
    public FilePath[] getModuleRoots(FilePath workspace, AbstractBuild build) {
        if (Util.isOverridden(SCM.class,getClass(),"getModuleRoots", FilePath.class))
            // if the subtype derives legacy getModuleRoots(FilePath), delegate to it
            return getModuleRoots(workspace);

        // otherwise the default implementation
        return new FilePath[]{getModuleRoot(workspace,build)};
    }
    
    /**
     * @deprecated as of 1.382.
     *      Use/derive from {@link #getModuleRoots(FilePath, AbstractBuild)} instead.
     */
    @Deprecated
    public FilePath[] getModuleRoots(FilePath workspace) {
        if (Util.isOverridden(SCM.class,getClass(),"getModuleRoots", FilePath.class, AbstractBuild.class))
            // if the subtype already derives newer getModuleRoots(FilePath,AbstractBuild), delegate to it
            return getModuleRoots(workspace,null);

        // otherwise the default implementation
        return new FilePath[] { getModuleRoot(workspace), };
    }

    /**
     * The returned object will be used to parse <tt>changelog.xml</tt>.
     */
    public abstract ChangeLogParser createChangeLogParser();

    public SCMDescriptor<?> getDescriptor() {
        return (SCMDescriptor) Jenkins.getInstance().getDescriptorOrDie(getClass());
    }

//
// convenience methods
//

    @Deprecated
    protected final boolean createEmptyChangeLog(File changelogFile, BuildListener listener, String rootTag) {
        try {
            createEmptyChangeLog(changelogFile, (TaskListener) listener, rootTag);
            return true;
        } catch (IOException e) {
            e.printStackTrace(listener.error(e.getMessage()));
            return false;
        }
    }

    /**
     * @since 1.568
     */
    protected final void createEmptyChangeLog(@Nonnull File changelogFile, @Nonnull TaskListener listener, @Nonnull String rootTag) throws IOException {
        FileWriter w = null;
        try {
            w = new FileWriter(changelogFile);
            w.write("<"+rootTag +"/>");
            w.close();
        } finally {
            IOUtils.closeQuietly(w);
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
    public static final Permission TAG = new Permission(PERMISSIONS,"Tag",Messages._SCM_TagPermission_Description(),Permission.CREATE, PermissionScope.ITEM);

    /**
     * Returns all the registered {@link SCMDescriptor}s.
     */
    public static DescriptorExtensionList<SCM,SCMDescriptor<?>> all() {
        return Jenkins.getInstance().<SCM,SCMDescriptor<?>>getDescriptorList(SCM.class);
    }

    /**
     * Determines which kinds of SCMs are applicable to a given project.
     * @param project a project on which we might be configuring SCM, or null if unknown
     * @return all descriptors which {@link SCMDescriptor#isApplicable(Job)} to it, also filtered by {@link TopLevelItemDescriptor#isApplicable};
     *         or simply {@link #all} if there is no project
     * @since 1.568
     */
    public static List<SCMDescriptor<?>> _for(@CheckForNull final Job project) {
        if(project==null)   return all();
        
        final Descriptor pd = Jenkins.getInstance().getDescriptor((Class) project.getClass());
        List<SCMDescriptor<?>> r = new ArrayList<SCMDescriptor<?>>();
        for (SCMDescriptor<?> scmDescriptor : all()) {
            if(!scmDescriptor.isApplicable(project))    continue;

            if (pd instanceof TopLevelItemDescriptor) {
                TopLevelItemDescriptor apd = (TopLevelItemDescriptor) pd;
                if(!apd.isApplicable(scmDescriptor))    continue;
            }

            r.add(scmDescriptor);
        }

        return r;
    }

    @Deprecated
    public static List<SCMDescriptor<?>> _for(final AbstractProject project) {
        return _for((Job) project);
    }

    /**
     * Try to guess how a repository browser should be configured, based on URLs and the like.
     * Used when {@link #getBrowser} has not been explicitly configured.
     * @return a reasonable default value for {@link #getEffectiveBrowser}, or null
     * @since 1.568
     */
    public @CheckForNull RepositoryBrowser<?> guessBrowser() {
        return null;
    }

}
