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
import hudson.model.DirectoryBrowserSupport;
import hudson.model.Run;
import hudson.util.DirScanner;
import hudson.util.FileVisitor;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import hudson.util.HttpResponses;
import java.util.Map;

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
        workspace.copyRecursiveTo(new ExplicitlySpecifiedDirScanner(artifacts), new FilePath(dir), description);
    }
    private static class ExplicitlySpecifiedDirScanner extends DirScanner {
        private static final long serialVersionUID = 1;
        private final Map<String,String> artifacts;
        ExplicitlySpecifiedDirScanner(Map<String,String> artifacts) {
            this.artifacts = artifacts;
        }
        @Override public void scan(File dir, FileVisitor visitor) throws IOException {
            for (Map.Entry<String,String> entry : artifacts.entrySet()) {
                String archivedPath = entry.getKey();
                assert archivedPath.indexOf('\\') == -1;
                String workspacePath = entry.getValue();
                assert workspacePath.indexOf('\\') == -1;
                scanSingle(new File(dir, workspacePath), archivedPath, visitor);
            }
        }
    }

    @Override public boolean deleteArtifacts() throws IOException, InterruptedException {
        File ad = getArtifactsDir();
        if (!ad.exists()) {
            return false;
        }
        Util.deleteRecursive(ad);
        return true;
    }

    @Override public Object browseArtifacts() {
        throw HttpResponses._throw(new DirectoryBrowserSupport(build, new FilePath(getArtifactsDir()),
                build.getParent().getDisplayName() + ' ' + build.getDisplayName(), "package.png", true));
    }

    @SuppressWarnings("rawtypes") // super told me to
    @Override public Run.ArtifactList getArtifactsUpTo(int n) {
        Run.ArtifactList r = build.new ArtifactList();
        addArtifacts(build, getArtifactsDir(), "", "", r, null, n, new AtomicInteger());
        r.computeDisplayName();
        return r;
    }

    @SuppressWarnings({"rawtypes", "unchecked"}) // trying to type-check this mess just seems hopeless
    private static int addArtifacts(Run build, File dir, String path, String pathHref, Run.ArtifactList r, Run.Artifact parent, int upTo, AtomicInteger idSeq) {
        String[] children = dir.list();
        if(children==null)  return 0;
        Arrays.sort(children, String.CASE_INSENSITIVE_ORDER);

        int n = 0;
        for (String child : children) {
            String childPath = path + child;
            String childHref = pathHref + Util.rawEncode(child);
            File sub = new File(dir, child);
            String length = sub.isFile() ? String.valueOf(sub.length()) : "";
            boolean collapsed = (children.length==1 && parent!=null);
            Run.Artifact a;
            if (collapsed) {
                // Collapse single items into parent node where possible:
                a = build.new Artifact(parent.getFileName() + '/' + child, childPath,
                                 sub.isDirectory() ? null : childHref, length,
                                 parent.getTreeNodeId());
                r.getTree().put(a, r.getTree().remove(parent));
            } else {
                // Use null href for a directory:
                a = build.new Artifact(child, childPath,
                                 sub.isDirectory() ? null : childHref, length,
                                 "n" + idSeq.incrementAndGet());
                r.getTree().put(a, parent!=null ? parent.getTreeNodeId() : null);
            }
            if (sub.isDirectory()) {
                n += addArtifacts(build, sub, childPath + '/', childHref + '/', r, a, upTo-n, idSeq);
                if (n>=upTo) break;
            } else {
                // Don't store collapsed path in ArrayList (for correct data in external API)
                r.add(collapsed ? build.new Artifact(child, a.relativePath, a.getHref(), length, a.getTreeNodeId()) : a);
                if (++n>=upTo) break;
            }
        }
        return n;
    }

    @Override public InputStream loadArtifact(String artifact) throws IOException {
        return new FileInputStream(new File(getArtifactsDir(), artifact));
    }

    @SuppressWarnings("deprecation")
    private File getArtifactsDir() {
        return build.getArtifactsDir();
    }

}
