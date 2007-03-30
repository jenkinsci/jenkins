package hudson.maven.reporters;

import hudson.FilePath;
import hudson.maven.MavenBuild;
import hudson.maven.MavenBuildProxy;
import hudson.maven.MavenBuildProxy.BuildCallable;
import hudson.maven.MavenModule;
import hudson.maven.MavenReporter;
import hudson.maven.MavenReporterDescriptor;
import hudson.maven.MojoInfo;
import hudson.model.Action;
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
import java.util.List;
import java.util.Map;
import java.util.Set;

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
     * Recorded fingerprints.
     */
    private transient Map<String,String> record;

    public boolean preBuild(MavenBuildProxy build, MavenProject pom, BuildListener listener) throws InterruptedException, IOException {
        files = new HashSet<File>();
        record = new HashMap<String,String>();
        return true;
    }

    public boolean postExecute(MavenBuildProxy build, MavenProject pom, MojoInfo mojo, BuildListener listener, Throwable error) throws InterruptedException, IOException {
        boolean updated = false;

        // really nice if we can do this in preExecute,
        // but dependency resolution only happens after preExecute.
        updated |= record(build,false,pom.getArtifacts());

        // try to pick up artifacts as soon as they are found.
        updated |= record(build,true,pom.getArtifact());
        updated |= record(build,true,pom.getAttachedArtifacts());

        if(updated) {
            build.execute(new BuildCallable<Void,IOException>() {
                // record is transient, so needs to make a copy first
                private final Map<String, String> r = record;

                public Void call(MavenBuild build) throws IOException, InterruptedException {
                    // update the build action with new fingerprints
                    FingerprintAction a = build.getAction(FingerprintAction.class);
                    List<Action> actions = build.getActions();
                    if(a!=null)
                        actions.remove(a);
                    actions.add(new FingerprintAction(build,r));
                    return null;
                }
            });
        }

        return true;
    }

    private boolean record(MavenBuildProxy build, boolean produced, Collection<Artifact> artifacts) throws IOException, InterruptedException {
        boolean updated = false;
        for (Artifact a : artifacts)
            updated |= record(build,produced,a);
        return updated;
    }

    /**
     * Records the fingerprint of the given {@link Artifact}.
     *
     * <p>
     * This method contains the logic to avoid doubly recording the fingerprint
     * of the same file.
     */
    private boolean record(MavenBuildProxy build, final boolean produced, Artifact a) throws IOException, InterruptedException {
        File f = a.getFile();
        if(f==null || !f.exists() || f.isDirectory() || !files.add(f))
            return false;

        // new file
        final String digest = new FilePath(f).digest();
        final String name = a.getGroupId()+':'+f.getName();
        record.put(name,digest);

        build.execute(new BuildCallable<Void,IOException>() {
            public Void call(MavenBuild build) throws IOException, InterruptedException {
                FingerprintMap map = Hudson.getInstance().getFingerprintMap();
                map.getOrCreate(produced?build:null, name, digest).add(build);
                return null;
            }
        });
        return true;
    }

    public DescriptorImpl getDescriptor() {
        return DescriptorImpl.DESCRIPTOR;
    }

    public static final class DescriptorImpl extends MavenReporterDescriptor {
        public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

        private DescriptorImpl() {
            super(MavenFingerprinter.class);
        }

        public String getDisplayName() {
            return "Record fingerprints";
        }

        public MavenReporter newAutoInstance(MavenModule module) {
            return new MavenFingerprinter();
        }
    }

    private static final long serialVersionUID = 1L;
}
