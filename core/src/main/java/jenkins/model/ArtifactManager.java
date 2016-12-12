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

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.ArtifactArchiver;
import java.io.IOException;
import java.util.Map;
import jenkins.util.VirtualFile;

/**
 * Manager of artifacts for one build.
 * @see ArtifactManagerFactory
 * @since 1.532
 */
public abstract class ArtifactManager {

    /**
     * Called when this manager is loaded from disk.
     * The selected manager will be persisted inside a build, so the build reference should be {@code transient} (quasi-{@code final}) and restored here.
     * @param build a historical build with which this manager was associated
     */
    public abstract void onLoad(Run<?,?> build);

    /**
     * Archive all configured artifacts from a build.
     * <p>If called multiple times for the same build, do not delete the old artifacts but keep them all, unless overwritten.
     * For example, the XVNC plugin could use this to save {@code screenshot.jpg} if so configured.
     * <p>This method is typically invoked on a running build, though e.g. in the case of Maven module builds,
     * the build may actually be {@link hudson.model.Run.State#COMPLETED} when this is called
     * (since it is the parent build which is still running and performing archiving).
     * @param workspace the root directory from which to copy files (typically {@link AbstractBuild#getWorkspace} but not necessarily)
     * @param launcher a launcher to use if external processes need to be forked
     * @param listener a way to print messages about progress or problems
     * @param artifacts map from paths in the archive area to paths relative to {@code workspace} (all paths {@code /}-separated)
     * @throws IOException if transfer or copying failed in any way
     * @throws InterruptedException if transfer was interrupted
     * @see ArtifactArchiver#perform(Run, FilePath, Launcher, TaskListener)
     */
    public abstract void archive(FilePath workspace, Launcher launcher, BuildListener listener, Map<String,String> artifacts) throws IOException, InterruptedException;

    /**
     * Delete all artifacts associated with an earlier build (if any).
     * @return true if there was actually anything to delete
     * @throws IOException if deletion could not be completed
     * @throws InterruptedException if deletion was interrupted
     */
    public abstract boolean delete() throws IOException, InterruptedException;

    /**
     * Returns a representation of the root directory of archived artifacts.
     * @return the archive root
     */
    public abstract VirtualFile root();

}
