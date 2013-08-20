/*
 * The MIT License
 *
 * Copyright 2013 Jesse Glick.
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

package jenkins.model;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.DirectoryBrowserSupport;
import hudson.model.Job;
import hudson.model.Run;
import hudson.tasks.ArtifactArchiver;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import hudson.util.HttpResponses;
import java.util.Map;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Pluggable ability to manage transfer and/or storage of build artifacts.
 * @since TODO
 */
public abstract class ArtifactManager implements ExtensionPoint {

    /**
     * A unique ID for this manager among all registered managers.
     * @return by default, the class name
     * @see #appliesTo
     */
    public String id() {
        return getClass().getName();
    }

    /**
     * Permits the manager to restrict its operation to certain kinds of projects, slaves, etc.
     * @param build a running build ready for {@link #archive} (the choice of manager will be remembered via {@link #id})
     * @return true to handle this build (the default), false to continue the search
     */
    public boolean appliesTo(Run<?,?> build) {
        return true;
    }

    /**
     * Archive all configured artifacts from a build.
     * <p>If called multiple times for the same build, do not delete the old artifacts but keep them all, unless overwritten.
     * For example, the XVNC plugin could use this to save {@code screenshot.jpg} if so configured.
     * <p>This method is typically invoked on a running build, though e.g. in the case of Maven module builds,
     * the build may actually be {@link hudson.model.Run.State#COMPLETED} when this is called
     * (since it is the parent build which is still running and performing archiving).
     * @param build the build which may have produced archivable files
     * @param workspace the root directory from which to copy files (typically {@link AbstractBuild#getWorkspace} but not necessarily)
     * @param launcher a launcher to use if external processes need to be forked
     * @param listener a way to print messages about progress or problems
     * @param artifacts map from paths in the archive area to paths relative to {@code workspace} (all paths {@code /}-separated)
     * @throws IOException if transfer or copying failed in any way
     * @see ArtifactArchiver#perform(AbstractBuild, Launcher, BuildListener)
     */
    public abstract void archive(Run<?,?> build, FilePath workspace, Launcher launcher, BuildListener listener, Map<String,String> artifacts) throws IOException, InterruptedException;

    /**
     * Delete all artifacts associated with an earlier build (if any).
     * @param build a build which may have been previously passed to {@link #archive}
     * @return true if there was actually anything to delete
     * @throws IOException if deletion could not be completed
     */
    public abstract boolean deleteArtifacts(Run<?,?> build) throws IOException, InterruptedException;

    /**
     * Returns an object that gets bound to URL under "job/JOB/15/artifact".
     *
     * <p>
     * This object should displays all artifacts of a build in a web display.
     * (The caller is responsible for checking {@link Run#ARTIFACTS} permission.)
     *
     * <p>
     * The response should also be capable of handling display of individual artifacts or subdirectories as per {@link StaplerRequest#getRestOfPath},
     * and serving {@code *fingerprint*} URLs as {@link DirectoryBrowserSupport} does.
     *
     * @param build a build for which artifacts may have been archived
     * @return
     *      the stapler-bound object to render the page.
     * @throws HttpResponse
     *      if the implementation wants to redirect to elsewhere, throw any exception that implements
     *      {@link HttpResponse} to have Jenkins return a redirect (or any other HTTP request.)
     *      See {@link HttpResponses#redirectTo(String)} for example.
     */
    public abstract Object browseArtifacts(Run<?,?> build);

    /**
     * Loads a manifest of some or all artifact records.
     * @param build a build which may have artifacts
     * @param n a maximum number of items to return
     * @return a list of artifact records
     */
    public abstract <JobT extends Job<JobT,RunT>,RunT extends Run<JobT,RunT>> Run<JobT,RunT>.ArtifactList getArtifactsUpTo(Run<JobT,RunT> build, int n);

    /**
     * Load the contents of an artifact as a stream.
     * Useful especially in conjunction with {@link #archiveSingle} to serve previously stored content.
     * @param build a build which may have artifacts
     * @param artifact the relative path of the artifact, e.g. {@code subdir/something.jar}
     * @return the contents of the artifact
     * @throws FileNotFoundException if no such artifact exists in this build
     * @throws IOException in case of some other problem
     */
    public abstract InputStream loadArtifact(Run<?,?> build, String artifact) throws IOException;

    /** All registered managers. */
    public static ExtensionList<ArtifactManager> all() {
        return Jenkins.getInstance().getExtensionList(ArtifactManager.class);
    }

}
