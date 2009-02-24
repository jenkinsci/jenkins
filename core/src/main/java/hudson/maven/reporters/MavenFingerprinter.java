/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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
package hudson.maven.reporters;

import hudson.FilePath;
import hudson.Extension;
import hudson.maven.MavenBuild;
import hudson.maven.MavenBuildProxy;
import hudson.maven.MavenBuildProxy.BuildCallable;
import hudson.maven.MavenModule;
import hudson.maven.MavenReporter;
import hudson.maven.MavenReporterDescriptor;
import hudson.maven.MojoInfo;
import hudson.maven.MavenModuleSetBuild;
import hudson.model.BuildListener;
import hudson.model.FingerprintMap;
import hudson.model.Hudson;
import hudson.tasks.Fingerprinter.FingerprintAction;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.List;

/**
 * Records fingerprints of the builds to keep track of dependencies.
 *
 * @author Kohsuke Kawaguchi
 */
public class MavenFingerprinter extends MavenReporter {

    /**
     * Files whose fingerprints were already recorded.
     */
    private transient Set<File> files;
    /**
     * Fingerprints for files that were used.
     */
    private transient Map<String,String> used;
    /**
     * Fingerprints for files that were produced.
     */
    private transient Map<String,String> produced;

    public boolean preBuild(MavenBuildProxy build, MavenProject pom, BuildListener listener) throws InterruptedException, IOException {
        files = new HashSet<File>();
        used = new HashMap<String,String>();
        produced = new HashMap<String,String>();
        return true;
    }

    /**
     * Mojos perform different dependency resolution, so we need to check this for each mojo.
     */
    public boolean postExecute(MavenBuildProxy build, MavenProject pom, MojoInfo mojo, BuildListener listener, Throwable error) throws InterruptedException, IOException {
        record(pom.getArtifacts(),used);
        record(pom.getArtifact(),produced);
        record(pom.getAttachedArtifacts(),produced);
        record(pom.getGroupId(),pom.getFile(),produced);

        return true;
    }

    /**
     * Sends the collected fingerprints over to the master and record them.
     */
    public boolean postBuild(MavenBuildProxy build, MavenProject pom, BuildListener listener) throws InterruptedException, IOException {
        build.executeAsync(new BuildCallable<Void,IOException>() {
            // record is transient, so needs to make a copy first
            private final Map<String,String> u = used;
            private final Map<String,String> p = produced;

            public Void call(MavenBuild build) throws IOException, InterruptedException {
                FingerprintMap map = Hudson.getInstance().getFingerprintMap();

                for (Entry<String, String> e : p.entrySet())
                    map.getOrCreate(build, e.getKey(), e.getValue()).add(build);
                for (Entry<String, String> e : u.entrySet())
                    map.getOrCreate(null, e.getKey(), e.getValue()).add(build);

                Map<String,String> all = new HashMap<String, String>(u);
                all.putAll(p);

                // add action
                build.getActions().add(new FingerprintAction(build,all));
                return null;
            }
        });
        return true;
    }

    private void record(Collection<Artifact> artifacts, Map<String,String> record) throws IOException, InterruptedException {
        for (Artifact a : artifacts)
            record(a,record);
    }

    /**
     * Records the fingerprint of the given {@link Artifact}.
     *
     * <p>
     * This method contains the logic to avoid doubly recording the fingerprint
     * of the same file.
     */
    private void record(Artifact a, Map<String,String> record) throws IOException, InterruptedException {
        File f = a.getFile();
        if(files==null)
            throw new InternalError();
        record(a.getGroupId(), f, record);
    }

    private void record(String groupId, File f, Map<String, String> record) throws IOException, InterruptedException {
        if(f==null || !f.exists() || f.isDirectory() || !files.add(f))
            return;

        // new file
        String digest = new FilePath(f).digest();
        record.put(groupId+':'+f.getName(),digest);
    }

    @Extension
    public static final class DescriptorImpl extends MavenReporterDescriptor {
        public String getDisplayName() {
            return Messages.MavenFingerprinter_DisplayName();
        }

        public MavenReporter newAutoInstance(MavenModule module) {
            return new MavenFingerprinter();
        }
    }

    /**
     * Creates {@link FingerprintAction} for {@link MavenModuleSetBuild}
     * by aggregating all fingerprints from module builds.
     */
    public static void aggregate(MavenModuleSetBuild mmsb) throws IOException {
        Map<String,String> records = new HashMap<String, String>();
        for (List<MavenBuild> builds : mmsb.getModuleBuilds().values()) {
            for (MavenBuild build : builds) {
                FingerprintAction fa = build.getAction(FingerprintAction.class);
                if(fa!=null)
                    records.putAll(fa.getRecords());
            }
        }
        if(!records.isEmpty()) {
            FingerprintMap map = Hudson.getInstance().getFingerprintMap();
            for (Entry<String, String> e : records.entrySet())
                map.getOrCreate(null, e.getKey(), e.getValue()).add(mmsb);
            mmsb.addAction(new FingerprintAction(mmsb,records));
        }
    }

    private static final long serialVersionUID = 1L;
}
