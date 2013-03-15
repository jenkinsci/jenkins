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
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.DirectoryBrowserSupport;
import hudson.model.Job;
import hudson.model.Run;
import hudson.tasks.ArtifactArchiver;
import java.io.IOException;
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
     * (For example you can limit support to instances of {@link AbstractBuild}, for which {@link AbstractBuild#getWorkspace} is defined.)
     * @param build a running build ready for {@link #archive} (the choice of manager will be remembered via {@link #id})
     * @return true to handle this build, false to continue the search
     */
    public abstract boolean appliesTo(Run<?,?> build);

    /**
     * Archive all configured artifacts from a build.
     * @param build the build which may have produced archivable files
     * @param launcher a launcher to use if external processes need to be forked
     * @param listener a way to print messages about progress or problems
     * @param artifacts comma- or space-separated list of patterns of files/directories to be archived (Ant format, any variables already substituted)
     * @param excludes patterns of files to be excluded from the artifact list (Ant format, may be null for no excludes)
     * @return the number of files actually archived (may be zero)
     * @throws IOException if transfer or copying failed in any way
     * @see ArtifactArchiver#perform(AbstractBuild, Launcher, BuildListener)
     */
    public abstract int archive(Run<?,?> build, Launcher launcher, BuildListener listener, String artifacts, @CheckForNull String excludes) throws IOException, InterruptedException;

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

    // XXX way to open an Artifact as e.g. an InputStream

}
