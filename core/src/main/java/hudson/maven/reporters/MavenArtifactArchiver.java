package hudson.maven.reporters;

import hudson.FilePath;
import hudson.maven.MavenBuild;
import hudson.maven.MavenBuildProxy;
import hudson.maven.MavenBuildProxy.BuildCallable;
import hudson.maven.MavenModule;
import hudson.maven.MavenReporter;
import hudson.maven.MavenReporterDescriptor;
import hudson.model.BuildListener;
import hudson.model.FingerprintMap;
import hudson.model.Hudson;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.MavenProject;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.io.File;
import java.util.HashSet;
import java.util.Set;

/**
 * Archives artifacts of the build.
 *
 * @author Kohsuke Kawaguchi
 */
public class MavenArtifactArchiver extends MavenReporter {
    public boolean postBuild(MavenBuildProxy build, MavenProject pom, BuildListener listener) throws InterruptedException, IOException {
        final Set<FilePath> archivedFiles = new HashSet<FilePath>();

        record(build,pom.getArtifact(),listener,archivedFiles);
        for( Object a : pom.getAttachedArtifacts() )
            record(build,(Artifact)a,listener,archivedFiles);

        // record fingerprints
        build.execute(new BuildCallable<Void,IOException>() {
            public Void call(MavenBuild build) throws IOException, InterruptedException {
                FingerprintMap map = Hudson.getInstance().getFingerprintMap();
                for (FilePath file : archivedFiles)
                    map.getOrCreate(build, file.getName(), file.digest());
                return null;
            }
        });

        return true;
    }

    /**
     * Archives the given {@link Artifact}.
     */
    private void record(MavenBuildProxy build, Artifact a, BuildListener listener, Set<FilePath> archivedFiles) throws IOException, InterruptedException {
        File file = a.getFile();
        if(file==null)
            return; // perhaps build failed and didn't leave an artifact
        if(file.isDirectory())
            return; // during a build maven sets a class folder instead of a jar file as artifact. ignore.

        listener.getLogger().println("Archiving "+ file);

        FilePath target = build.getArtifactsDir()
            .child(a.getGroupId())
            .child(a.getArtifactId())
            .child(a.getVersion())
            .child(a.getArtifactId() + '-' + a.getVersion() + (a.getClassifier() != null ? '-' + a.getClassifier() : "") + '.' + a.getType());

        new FilePath(file).copyTo(target);

        archivedFiles.add(target);
    }

    public DescriptorImpl getDescriptor() {
        return DescriptorImpl.DESCRIPTOR;
    }

    public static final class DescriptorImpl extends MavenReporterDescriptor {
        public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

        private DescriptorImpl() {
            super(MavenArtifactArchiver.class);
        }

        public String getDisplayName() {
            return "Archive the artifacts";
        }

        public MavenArtifactArchiver newInstance(StaplerRequest req) throws FormException {
            return new MavenArtifactArchiver();
        }

        public MavenReporter newAutoInstance(MavenModule module) {
            return new MavenArtifactArchiver();
        }
    }
}
