package hudson.maven.reporters;

import hudson.FilePath;
import hudson.maven.MavenBuild;
import hudson.maven.MavenBuildProxy;
import hudson.maven.MavenBuildProxy.BuildCallable;
import hudson.maven.MavenEmbedder;
import hudson.maven.MavenModule;
import hudson.maven.MavenReporter;
import hudson.maven.MavenReporterDescriptor;
import hudson.maven.MavenUtil;
import hudson.maven.MojoInfo;
import hudson.model.BuildListener;
import hudson.model.FingerprintMap;
import hudson.model.Hudson;
import hudson.model.Result;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.installer.ArtifactInstallationException;
import org.apache.maven.artifact.installer.ArtifactInstaller;
import org.apache.maven.embedder.MavenEmbedderException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.artifact.ProjectArtifactMetadata;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

/**
 * Archives artifacts of the build.
 *
 * <p>
 * Archive will be created in two places. One is inside the build directory,
 * to be served from Hudson. The other is to the local repository of the master,
 * so that artifacts can be shared in maven builds happening in other slaves.
 *
 * @author Kohsuke Kawaguchi
 */
public class MavenArtifactArchiver extends MavenReporter {
    private transient boolean installed;

    public boolean postExecute(MavenBuildProxy build, MavenProject pom, MojoInfo mojo, BuildListener listener, Throwable error) throws InterruptedException, IOException {
        if(!mojo.pluginName.matches("org.apache.maven.plugins","maven-install-plugin"))
            return true;
        if(!mojo.getGoal().equals("install"))
            return true;

        this.installed = true;

        return true;
    }

    public boolean postBuild(MavenBuildProxy build, MavenProject pom, final BuildListener listener) throws InterruptedException, IOException {
        final Set<ArtifactInfo> archivedFiles = new HashSet<ArtifactInfo>();

        if(pom.getFile()!=null) {// goals like 'clean' runs without loading POM, apparently.

            // record POM
            listener.getLogger().println("[HUDSON] Archiving "+ pom.getFile());
            final FilePath archivedPom = getArtifactArchivePath(build, pom.getGroupId(), pom.getArtifactId(), pom.getVersion())
                .child(pom.getArtifactId() + '-' + pom.getVersion() + ".pom");
            new FilePath(pom.getFile()).copyTo(archivedPom);

            // record artifacts
            record(build,pom.getArtifact(),listener,archivedFiles,true);
            for( Object a : pom.getAttachedArtifacts() )
                record(build,(Artifact)a,listener,archivedFiles,false);

            final boolean installed = this.installed;
            final boolean builtOnSlave = archivedPom.isRemote();

            if(!archivedFiles.isEmpty()) {
                build.execute(new BuildCallable<Void,IOException>() {
                    public Void call(MavenBuild build) throws IOException, InterruptedException {
                        // record fingerprints
                        FingerprintMap map = Hudson.getInstance().getFingerprintMap();
                        for (ArtifactInfo a : archivedFiles)
                            map.getOrCreate(build, a.path.getName(), a.path.digest());

                        // install files on the master
                        if(installed && builtOnSlave) {
                            try {
                                MavenEmbedder embedder = MavenUtil.createEmbedder(listener);
                                ArtifactInstaller installer = (ArtifactInstaller) embedder.getContainer().lookup(ArtifactInstaller.class.getName());
                                ArtifactFactory factory = (ArtifactFactory) embedder.getContainer().lookup(ArtifactFactory.class.getName());
                                for (ArtifactInfo a : archivedFiles) {
                                    Artifact artifact = a.toArtifact(factory);
                                    if(a.isPrimary)
                                        artifact.addMetadata( new ProjectArtifactMetadata( artifact, new File(archivedPom.getRemote()) ) );
                                    installer.install(new File(a.path.getRemote()), artifact,embedder.getLocalRepository());
                                }
                                embedder.stop();
                            } catch (MavenEmbedderException e) {
                                e.printStackTrace(listener.error("Failed to install artifact to the master"));
                                build.setResult(Result.FAILURE);
                            } catch (ComponentLookupException e) {
                                e.printStackTrace(listener.error("Failed to install artifact to the master"));
                                build.setResult(Result.FAILURE);
                            } catch (ArtifactInstallationException e) {
                                e.printStackTrace(listener.error("Failed to install artifact to the master"));
                                build.setResult(Result.FAILURE);
                            }
                        }

                        return null;
                    }
                });
            }
        }

        return true;
    }

    /**
     * Archives the given {@link Artifact}.
     */
    private void record(MavenBuildProxy build, Artifact a, BuildListener listener, Set<ArtifactInfo> archivedFiles, boolean primary) throws IOException, InterruptedException {
        File file = a.getFile();
        if(file==null)
            return; // perhaps build failed and didn't leave an artifact
        if(!file.exists() || file.isDirectory())
            return; // during a build maven sets a class folder instead of a jar file as artifact. ignore.

        listener.getLogger().println("[HUDSON] Archiving "+ file);

        String extension;
        if(a.getArtifactHandler()!=null) // don't know if this can be null, but just to be defensive.
            extension = a.getArtifactHandler().getExtension();
        else
            extension = a.getType();

        FilePath target = getArtifactArchivePath(build, a.getGroupId(), a.getArtifactId(), a.getVersion())
            .child(a.getArtifactId() + '-' + a.getVersion() + (a.getClassifier() != null ? '-' + a.getClassifier() : "") + '.' + extension);

        new FilePath(file).copyTo(target);

        archivedFiles.add(new ArtifactInfo(a,target,primary));
    }

    private FilePath getArtifactArchivePath(MavenBuildProxy build, String groupId, String artifactId, String version) {
        return build.getArtifactsDir().child(groupId).child(artifactId).child(version);
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

        public MavenReporter newAutoInstance(MavenModule module) {
            return new MavenArtifactArchiver();
        }
    }

    /**
     * Serializable datatype that captures {@link Artifact} so that it can be
     * sent from the slave to the master.
     */
    static final class ArtifactInfo implements Serializable {
        private final String groupId, artifactId, version, classifier, type;
        /**
         * Archived artifact on the master.
         */
        final FilePath path;
        /**
         * True for the main artifact. Maven install mojo only seems to associate POM
         * with the primary artifact, hence this flag.
         */
        final boolean isPrimary;

        ArtifactInfo(Artifact a, FilePath path, boolean primary) {
            this.groupId    = a.getGroupId();
            this.artifactId = a.getArtifactId();
            this.version    = a.getVersion();
            this.classifier = a.getClassifier();
            this.type       = a.getType();
            this.path       = path;
            this.isPrimary = primary;
        }

        Artifact toArtifact(ArtifactFactory factory) {
            return factory.createArtifactWithClassifier(groupId, artifactId, version, type, classifier);
        }

        private static final long serialVersionUID = 1L;
    }

    private static final long serialVersionUID = 1L;
}
