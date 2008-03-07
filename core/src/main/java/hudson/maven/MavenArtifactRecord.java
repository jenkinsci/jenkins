package hudson.maven;

import hudson.model.Action;

/**
 * Glue code to hook {@link MavenArtifact} to {@link MavenBuild}.
 *
 * @author Kohsuke Kawaguchi
 */
public class MavenArtifactRecord implements Action {
    /**
     * The build to which this record belongs.
     */
    public final MavenBuild parent;
    /**
     * The record.
     */
    public final MavenArtifact artifact;

    public MavenArtifactRecord(MavenBuild parent, MavenArtifact artifact) {
        this.parent = parent;
        this.artifact = artifact;
    }

    // TODO: show UI for manual redeployment
    public String getIconFileName() {
        return null;
    }

    public String getDisplayName() {
        return null;
    }

    public String getUrlName() {
        return null;
    }
}
