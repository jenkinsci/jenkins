package hudson.maven.reporters;

import hudson.model.Descriptor;
import hudson.model.BuildListener;
import hudson.FilePath;
import hudson.maven.MavenReporter;
import hudson.maven.MavenBuildProxy;
import hudson.maven.MojoInfo;
import org.kohsuke.stapler.StaplerRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.artifact.Artifact;

import java.io.IOException;

/**
 * Archives artifacts of the build.
 * 
 * @author Kohsuke Kawaguchi
 */
public class MavenArtifactArchiver extends MavenReporter {

    public void postExecute(MavenBuildProxy build, MavenProject pom, MojoInfo mojo, BuildListener listener) throws InterruptedException, IOException {
        record(build,pom.getArtifact());
        for( Object a : pom.getAttachedArtifacts() )
            record(build,(Artifact)a);
    }

    /**
     * Archives the given {@link Artifact}.
     */
    private void record(MavenBuildProxy build, Artifact a) throws IOException, InterruptedException {
        if(a.getFile()==null)
            return; // perhaps build failed and didn't leave an artifact

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
