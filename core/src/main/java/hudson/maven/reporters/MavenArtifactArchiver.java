package hudson.maven.reporters;

import hudson.FilePath;
import hudson.maven.MavenBuildProxy;
import hudson.maven.MavenReporter;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.MavenProject;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;

/**
 * Archives artifacts of the build.
 * 
 * @author Kohsuke Kawaguchi
 */
public class MavenArtifactArchiver extends MavenReporter {
    public boolean postBuild(MavenBuildProxy build, MavenProject pom, BuildListener listener) throws InterruptedException, IOException {
        record(build,pom.getArtifact(),listener);
        for( Object a : pom.getAttachedArtifacts() )
            record(build,(Artifact)a,listener);
        return true;
    }

    /**
     * Archives the given {@link Artifact}.
     */
    private void record(MavenBuildProxy build, Artifact a, BuildListener listener) throws IOException, InterruptedException {
        if(a.getFile()==null)
            return; // perhaps build failed and didn't leave an artifact

        listener.getLogger().println("Archiving "+a.getFile());

        new FilePath(a.getFile()).copyTo(
            build.getArtifactsDir()
                .child(a.getGroupId())
                .child(a.getArtifactId())
                .child(a.getVersion())
                .child(a.getArtifactId()+'-'+a.getVersion()+(a.getClassifier()!=null?'-'+a.getClassifier():"")+'.'+a.getType()));
    }

    public DescriptorImpl getDescriptor() {
        return DescriptorImpl.DESCRIPTOR;
    }

    public static final class DescriptorImpl extends Descriptor<MavenReporter> {
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
    }
}
