package hudson.maven.reporters;

import hudson.FilePath;
import hudson.maven.MavenBuild;
import hudson.maven.MavenBuildProxy;
import hudson.model.BuildListener;
import hudson.model.FingerprintMap;
import hudson.model.Hudson;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.handler.ArtifactHandler;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;

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
public final class MavenArtifact implements Serializable {
    /**
     * Basic parameters of a Maven artifact.
     */
    public final String groupId, artifactId, version, classifier, type;

    /**
     * File name (without directory portion) of this artifact in the Hudson archive.
     * Remembered explicitly because some times this doesn't follow the
     * standard naming convention, due to &lt;finalName> setting in POM.
     *
     * <p>
     * This name is taken directly from the name of the file as used during the build
     * (thus POM would be most likely just <tt>pom.xml</tt> and artifacts would
     * use their <tt>finalName</tt> if one is configured.) This is often
     * different from {@link #canonicalName}.
     */
    public final String fileName;

    /**
     * The canonical artifact file name, used by Maven in the repository.
     * This is <tt>artifactId-version[-classifier].extension</tt>.
     *
     * <p>
     * The reason we persist this is that the extension is only available
     * through {@link ArtifactHandler}. 
     */
    public final String canonicalName;

    public MavenArtifact(Artifact a) {
        this.groupId = a.getGroupId();
        this.artifactId = a.getArtifactId();
        this.version = a.getVersion();
        this.classifier = a.getClassifier();
        this.type = a.getType();
        // TODO: on archive we need to follow the same format as Maven repository
        this.fileName = a.getFile().getName();

        String extension;
        if(a.getArtifactHandler()!=null) // don't know if this can be null, but just to be defensive.
            extension = a.getArtifactHandler().getExtension();
        else
            extension = a.getType();

        canonicalName = getSeed(extension);
    }

    public MavenArtifact(String groupId, String artifactId, String version, String classifier, String type, String fileName) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.classifier = classifier;
        this.type = type;
        this.fileName = fileName;
        this.canonicalName = getSeed(type);
    }

    /**
     * Convenience method to check if the given {@link Artifact} object contains
     * enough information suitable for recording, and if so, create {@link MavenArtifact}.
     */
    public static MavenArtifact create(Artifact a) {
        File file = a.getFile();
        if(file==null)
            return null; // perhaps build failed and didn't leave an artifact
        if(!file.exists() || file.isDirectory())
            return null; // during a build maven sets a class folder instead of a jar file as artifact. ignore.
        return new MavenArtifact(a);
    }

    public boolean isPOM() {
        return fileName.endsWith(".pom");   // hack
    }

    /**
     * Creates a Maven {@link Artifact} back from the persisted data.
     */
    public Artifact toArtifact(ArtifactFactory factory, MavenBuild build) throws IOException {
        Artifact a = factory.createArtifactWithClassifier(groupId, artifactId, version, type, classifier);
        a.setFile(getFile(build));
        return a;
    }

    /**
     * Computes the file name seed by taking &lt;finalName> POM entry into consideration.
     */
    private String getSeed(String extension) {
        String name = artifactId+'-'+version;
        if(classifier!=null)
            name += '-'+classifier;
        name += '.'+extension;
        return name;
    }

    /**
     * Obtains the {@link File} representing the archived artifact.
     */
    protected File getFile(MavenBuild build) throws IOException {
        File f = new File(new File(new File(new File(build.getArtifactsDir(), groupId), artifactId), version), fileName);
        if(!f.exists())
            throw new IOException("Archived artifact is missing: "+f);
        return f;
    }

    private FilePath getArtifactArchivePath(MavenBuildProxy build, String groupId, String artifactId, String version) {
        return build.getArtifactsDir().child(groupId).child(artifactId).child(version).child(fileName);
    }

    /**
     * Called from within Maven to archive an artifact in Hudson.
     */
    public void archive(MavenBuildProxy build, File file, BuildListener listener) throws IOException, InterruptedException {
        listener.getLogger().println("[HUDSON] Archiving "+ file);
        FilePath target = getArtifactArchivePath(build,groupId,artifactId,version);
        new FilePath(file).copyTo(target);
    }

    /**
     * Called from within the master to record fingerprint.
     */
    public void recordFingerprint(MavenBuild build) throws IOException {
        try {
            FingerprintMap map = Hudson.getInstance().getFingerprintMap();
            map.getOrCreate(build,fileName,new FilePath(getFile(build)).digest());
        } catch (InterruptedException e) {
            throw new AssertionError(); // this runs on the master, so this is impossible
        }
    }


    private static final long serialVersionUID = 1L;
}
