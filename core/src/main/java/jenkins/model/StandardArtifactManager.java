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

import com.google.common.collect.AbstractIterator;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.BuildListener;
import hudson.model.Run;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

/**
 * Default artifact manager which transfers files over the remoting channel and stores them inside the build directory.
 * May be subclassed to provide an artifact manager which uses the standard storage but which only overrides {@link #archive}.
 * @since TODO
 */
public class StandardArtifactManager extends ArtifactManager {

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
            return false;
        }
        Util.deleteRecursive(ad);
        return true;
    }

    @Override public final Iterator<Map.Entry<String,Long>> iterator() {
        final File base = getArtifactsDir();
        return new AbstractIterator<Map.Entry<String,Long>>() {
            Queue<String> paths = new LinkedList<String>(Collections.singleton("/"));
            @Override protected Map.Entry<String,Long> computeNext() {
                while (true) {
                    String path = paths.poll();
                    if (path == null) {
                        return endOfData();
                    }
                    File f = new File(base, path);
                    if (f.isDirectory()) {
                        String[] kids = f.list();
                        if (kids != null) {
                            Arrays.sort(kids, String.CASE_INSENSITIVE_ORDER);
                            for (String kid : kids) {
                                paths.add(path + '/' + kid);
                            }
                        }
                    } else {
                        // TODO Java 6: AbstractMap.SimpleImmutableEntry
                        return Collections.singletonMap(path, f.length()).entrySet().iterator().next();
                    }
                }
            }
        };
    }

    @Override public final InputStream load(String artifact) throws IOException {
        return new FileInputStream(new File(getArtifactsDir(), artifact));
    }

    @SuppressWarnings("deprecation")
    private File getArtifactsDir() {
        return build.getArtifactsDir();
    }

}
