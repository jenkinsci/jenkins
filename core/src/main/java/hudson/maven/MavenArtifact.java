package hudson.maven;

import hudson.model.TaskListener;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.deployer.ArtifactDeployer;
import org.apache.maven.artifact.deployer.ArtifactDeploymentException;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.embedder.MavenEmbedderException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.artifact.ProjectArtifactMetadata;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Captures information about an artifact created by Maven and archived by
 * Hudson, so that we can later deploy it to repositories of our choice.
 *
 * <p>
 * This object is created within the Maven process and sent back to the master,
 * so it shouldn't contain anything non-serializable as fields.
 *
 * <p>
 * Once it's constructed, the object should be considered final and immutable.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.189
 */
public final class MavenArtifact extends BaseArtifact {
    public final List<AttachedArtifact> attachedArtifacts = new ArrayList<AttachedArtifact>();

    /**
     * File name (without directory portion) of the POM file.
     */
    public final String pomFileName;

    public MavenArtifact(MavenProject project, Artifact a) {
        super(a);
        pomFileName = project.getFile().getName();
    }

    public void attach(Artifact a) {
        attachedArtifacts.add(new AttachedArtifact(a));
    }

    public static final class AttachedArtifact extends BaseArtifact {
        public AttachedArtifact(Artifact a) {
            super(a);
        }

        private static final long serialVersionUID = 1L;
    }

    public boolean isPOM() {
        return pomFileName.equals(fileName);
    }

    /**
     * Deploys the artifacts to the specified {@link ArtifactRepository}.
     *
     * @param embedder
     *      This component hosts all the Maven components we need to do the work.
     * @param deploymentRepository
     *      The remote repository to deploy to.
     * @param listener
     *      The status and error goes to this listener.
     */
    public void deploy(MavenEmbedder embedder, ArtifactRepository deploymentRepository, MavenBuild build, TaskListener listener) throws MavenEmbedderException, IOException, ComponentLookupException, ArtifactDeploymentException {
        ArtifactDeployer deployer = (ArtifactDeployer) embedder.lookup(ArtifactDeployer.ROLE);
        ArtifactFactory factory = (ArtifactFactory) embedder.lookup(ArtifactFactory.ROLE);
        PrintStream logger = listener.getLogger();

        Artifact main = toArtifact(factory,build);

        if(!isPOM())
            main.addMetadata(new ProjectArtifactMetadata( main, getFile(build,pomFileName)));

        // deploy the main artifact. This also deploys the POM
        logger.println(Messages.MavenArtifact_DeployingMainArtifact(main.getFile().getName()));
        deployer.deploy(main.getFile(),main,deploymentRepository,embedder.getLocalRepository());

        for (AttachedArtifact aa : attachedArtifacts) {
            logger.println(Messages.MavenArtifact_DeployingAttachedArtifact(main.getFile().getName()));
            Artifact a = aa.toArtifact(factory, build);
            deployer.deploy(a.getFile(),a,deploymentRepository,embedder.getLocalRepository());
        }
    }

    private static final long serialVersionUID = 1L;
}

/**
 * Common part between {@link MavenArtifact} and {@link MavenArtifact.AttachedArtifact}.
 */
abstract class BaseArtifact implements Serializable {
    /**
     * Basic parameters of a Maven artifact.
     */
    public final String groupId, artifactId, version, classifier, type;

    /**
     * File name (without directory portion) of this artifact in the Hudson archive.
     * Remembered explicitly because some times this doesn't follow the
     * standard naming convention, due to &lt;finalName> setting in POM.
     */
    public final String fileName;

    public BaseArtifact(Artifact a) {
        this.groupId = a.getGroupId();
        this.artifactId = a.getArtifactId();
        this.version = a.getVersion();
        this.classifier = a.getClassifier();
        this.type = a.getType();
        this.fileName = a.getFile().getName();
    }

    /**
     * Creates a Maven {@link Artifact} back from the persisted data.
     */
    public Artifact toArtifact(ArtifactFactory factory,MavenBuild build) throws IOException {
        Artifact a = factory.createArtifactWithClassifier(groupId, artifactId, version, type, classifier);
        a.setFile(getFile(build, fileName));
        return a;
    }

    /**
     * Obtains the {@link File} representing the archived artifact.
     */
    protected File getFile(MavenBuild build, String fileName) throws IOException {
        File f = new File(new File(new File(new File(build.getArtifactsDir(), groupId), artifactId), version), fileName);
        if(!f.exists())
            throw new IOException("Archived artifact is missing: "+f);
        return f;
    }

    private static final long serialVersionUID = 1L;
}
