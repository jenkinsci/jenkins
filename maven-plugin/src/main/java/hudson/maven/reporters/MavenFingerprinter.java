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

import hudson.Extension;
import hudson.FilePath;
import hudson.maven.MavenBuild;
import hudson.maven.MavenBuildInformation;
import hudson.maven.MavenBuildProxy;
import hudson.maven.MavenBuildProxy.BuildCallable;
import hudson.maven.MavenModule;
import hudson.maven.MavenModuleSetBuild;
import hudson.maven.MavenReporter;
import hudson.maven.MavenReporterDescriptor;
import hudson.maven.MojoInfo;
import hudson.model.BuildListener;
import hudson.model.FingerprintMap;
import hudson.tasks.Fingerprinter.FingerprintAction;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import jenkins.model.Jenkins;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilderConfiguration;

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
        // TODO (kutzi, 2011/09/06): it should be perfectly safe to move all these records to the
        // postBuild method as artifacts should only be added by mojos, but never removed/modified.
		record(pom.getArtifacts(),used);
        record(pom.getArtifact(),produced);
        record(pom.getAttachedArtifacts(),produced);
        record(pom.getGroupId() + ":" + pom.getArtifactId(),pom.getFile(),produced);

        return true;
    }

    /**
     * Sends the collected fingerprints over to the master and record them.
     */
    public boolean postBuild(MavenBuildProxy build, MavenProject pom, BuildListener listener) throws InterruptedException, IOException {
        
        recordParents(build, pom, listener);
        
        build.executeAsync(new BuildCallable<Void,IOException>() {
            private static final long serialVersionUID = -1360161848504044869L;

            // record is transient, so needs to make a copy first
            private final Map<String,String> u = used;
            private final Map<String,String> p = produced;

            public Void call(MavenBuild build) throws IOException, InterruptedException {
                FingerprintMap map = Jenkins.getInstance().getFingerprintMap();

                for (Entry<String, String> e : p.entrySet())
                    map.getOrCreate(build, e.getKey(), e.getValue()).add(build);
                for (Entry<String, String> e : u.entrySet())
                    map.getOrCreate(null, e.getKey(), e.getValue()).add(build);

                Map<String,String> all = new HashMap<String, String>(u);
                all.putAll(p);

                // add action
                FingerprintAction fa = build.getAction(FingerprintAction.class);
                if (fa!=null)   fa.add(all);
                else            build.getActions().add(new FingerprintAction(build,all));
                return null;
            }
        });
        return true;
    }

	private void recordParents(MavenBuildProxy build, MavenProject pom, BuildListener listener) throws IOException, InterruptedException {
        Map<String,String> modelParents = build.getMavenBuildInformation().modelParents;
        ArtifactRepository localRepository = getLocalRepository(build.getMavenBuildInformation(), pom);
        if (localRepository == null) {
            listener.error("Could not find local repository for " + build.getMavenBuildInformation().getMavenVersion());
            return;
        }
		String parent = modelParents.get(pom.getId());
		while (parent != null) {
            String[] parts = parent.split(":");
            assert parts.length == 4 : parent;
            // Maven 2.x lacks DefaultArtifact constructor with String version and ArtifactRepository.find:
            Artifact parentArtifact = new DefaultArtifact(parts[0], parts[1], VersionRange.createFromVersion(parts[3]), null, parts[2], null, new DefaultArtifactHandler(parts[2]));
            File parentFile = new File(localRepository.getBasedir(), localRepository.pathOf(parentArtifact));
            // we need to include the artifact Id for poms as well, otherwise a project with the same groupId would override its parent's fingerprint
            record(parts[0] + ":" + parts[1], parentFile, used);
			parent = modelParents.get(parent);
		}
	}

    private ArtifactRepository getLocalRepository(MavenBuildInformation mavenBuildInformation, MavenProject pom) {
        if (mavenBuildInformation.isMaven3OrLater()) {
            return pom.getProjectBuildingRequest().getLocalRepository();
        } else if (mavenBuildInformation.isAtLeastMavenVersion("2.2")) {
            // principally this should also work with Maven 2.1, but it's not tested, so err on the safe side
            return getArtifactRepositoryMaven21(pom);
        } else if (mavenBuildInformation.isAtLeastMavenVersion("2.0")) {
         // Maven 2.0 has no corresponding mechanism
            return null;
		} else {
		    LOGGER.warning("Unknown Maven version: "+mavenBuildInformation.getMavenVersion());
		    return null;
		}
	}

	@SuppressWarnings("deprecation")
    private ArtifactRepository getArtifactRepositoryMaven21(MavenProject pom) {
	    ProjectBuilderConfiguration projectBuilderConfiguration;
        try {
            // Since maven-plugin is compiled against maven-core-3x, we need to retrieve 
            // this maven 2 object via reflection
            Method method = MavenProject.class.getMethod("getProjectBuilderConfiguration");
            projectBuilderConfiguration = (ProjectBuilderConfiguration) method.invoke(pom);
            if (projectBuilderConfiguration == null) {
                return null;
            }
            return projectBuilderConfiguration.getLocalRepository();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Could not retrieve BuilderConfigration", e);
            return null;
        }
    }

    private void record(Collection<Artifact> artifacts, Map<String,String> record) throws IOException, InterruptedException {
        for (Artifact a : artifacts)
            record(a,record);
    }

    /**
     * Records the fingerprint of the given {@link Artifact}.
     */
    private void record(Artifact a, Map<String,String> record) throws IOException, InterruptedException {
        File f = a.getFile();
        record(a.getGroupId(), f, record);
    }

    /**
     * Records the fingerprint of the given file.
     *
     * <p>
     * This method contains the logic to avoid doubly recording the fingerprint
     * of the same file.
     */
    private void record(String fileNamePrefix, File f, Map<String, String> record) throws IOException, InterruptedException {
        if(f==null || files.contains(f) || !f.isFile())
            return;

        // new file
        files.add(f);
        String digest = new FilePath(f).digest();
        record.put(fileNamePrefix+':'+f.getName(),digest);
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
            FingerprintMap map = Jenkins.getInstance().getFingerprintMap();
            for (Entry<String, String> e : records.entrySet())
                map.getOrCreate(null, e.getKey(), e.getValue()).add(mmsb);
            mmsb.addAction(new FingerprintAction(mmsb,records));
        }
    }

    private static final long serialVersionUID = 1L;
    
    private static final Logger LOGGER = Logger.getLogger(MavenFingerprinter.class.getName());
}
