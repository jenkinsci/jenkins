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

import hudson.ExtensionPoint;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.DirectoryBrowserSupport;
import hudson.model.Executor;
import hudson.model.Job;
import hudson.model.Run;
import hudson.tasks.ArtifactArchiver;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import javax.annotation.CheckForNull;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Pluggable ability to manage transfer and/or storage of build artifacts.
 * @since XXX
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
     * (If called multiple times for the same build, do not delete the old artifacts but keep them all.)
     *
     * <p>
     * This method can be only invoked from the {@link Executor} thread that's running the build,
     * while the build is still in progress.
     *
     * @param build the build which may have produced archivable files
     * @param workspace the workspace from which to copy files
     * @param launcher a launcher to use if external processes need to be forked
     * @param listener a way to print messages about progress or problems
     * @param artifacts comma- or space-separated list of patterns of files/directories to be archived relative to the workspace (Ant format, any variables already substituted)
     * @param excludes patterns of files to be excluded from the artifact list (Ant format, may be null for no excludes)
     * @return the number of files actually archived (may be zero)
     * @throws IOException if transfer or copying failed in any way
     * @see ArtifactArchiver#perform(AbstractBuild, Launcher, BuildListener)
     */
    public abstract int archive(Run<?,?> build, FilePath workspace, Launcher launcher, BuildListener listener, String artifacts, @CheckForNull String excludes) throws IOException, InterruptedException;

    /**
     * Add a single file to the list of archives for a build.
     * For example, the XVNC plugin could use this to save {@code screenshot.jpg} if so configured.
     *
     * <p>
     * This method can be only invoked from the {@link Executor} thread that's running the build,
     * while the build is still in progress.
     *
     * @param build a build which may or may not already have archives
     * @param launcher a launcher to use if external processes need to be forked
     * @param listener a way to print messages about progress or problems
     * @param source a file to copy
     * @param target the full relative path to which this file should be archived, e.g. {@code subdir/something.jar}
     * @throws IOException if transfer or copying failed in any way
     */
    public abstract void archiveSingle(Run<?,?> build, Launcher launcher, BuildListener listener, FilePath source, String target) throws IOException, InterruptedException;

    /**
     * Delete all artifacts associated with an earlier build (if any).
     * @param build a build which may have been previously passed to {@link #archive}
     * @return true if there was actually anything to delete
     * @throws IOException if deletion could not be completed
     */
    public abstract boolean deleteArtifacts(Run<?,?> build) throws IOException, InterruptedException;

    /**
     * Displays all artifacts of a build in a web display.
     * (There is no need to check {@link Run#ARTIFACTS} permission.)
     * The response should also be capable of handling display of individual artifacts or subdirectories as per {@link StaplerRequest#getRestOfPath},
     * and serving {@code *fingerprint*} URLs as {@link DirectoryBrowserSupport} does.
     * @param build a build for which artifacts may have been archived
     * @return some web page
     */
    public abstract HttpResponse browseArtifacts(Run<?,?> build);

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

}
