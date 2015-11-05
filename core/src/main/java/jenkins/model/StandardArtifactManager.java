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
import hudson.Util;
import hudson.model.BuildListener;
import hudson.model.Run;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.util.VirtualFile;

/**
 * Default artifact manager which transfers files over the remoting channel and stores them inside the build directory.
 * May be subclassed to provide an artifact manager which uses the standard storage but which only overrides {@link #archive}.
 * @since 1.532
 */
public class StandardArtifactManager extends ArtifactManager {

    private static final Logger LOG = Logger.getLogger(StandardArtifactManager.class.getName());

    protected transient Run<?,?> build;

    public StandardArtifactManager(Run<?,?> build) {
        onLoad(build);
    }

    @Override public final void onLoad(Run<?,?> build) {
        this.build = build;
    }

    @Override public void archive(FilePath workspace, Launcher launcher, BuildListener listener, final Map<String,String> artifacts) throws IOException, InterruptedException {
        File dir = getArtifactsDir();
        String description = "transfer of " + artifacts.size() + " files"; // TODO improve when just one file
        workspace.copyRecursiveTo(new FilePath.ExplicitlySpecifiedDirScanner(artifacts), new FilePath(dir), description);
    }

    @Override public final boolean delete() throws IOException, InterruptedException {
        File ad = getArtifactsDir();
        if (!ad.exists()) {
            LOG.log(Level.FINE, "no such directory {0} to delete for {1}", new Object[] {ad, build});
            return false;
        }
        LOG.log(Level.FINE, "deleting {0} for {1}", new Object[] {ad, build});
        Util.deleteRecursive(ad);
        return true;
    }

    @Override public VirtualFile root() {
        return VirtualFile.forFile(getArtifactsDir());
    }

    @SuppressWarnings("deprecation")
    private File getArtifactsDir() {
        return build.getArtifactsDir();
    }

}
