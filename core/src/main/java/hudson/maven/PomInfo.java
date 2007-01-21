package hudson.maven;

import org.apache.maven.project.MavenProject;

import java.io.Serializable;

/**
 * @author Kohsuke Kawaguchi
 */
final class PomInfo implements Serializable {

    public final ModuleName name;

    /**
     * This is a human readable name of the POM. Not necessarily unique
     * or file system safe.
     *
     * @see MavenProject#getName() 
     */
    public final String displayName;

    public PomInfo(MavenProject project) {
        this.name = new ModuleName(project.getGroupId(),project.getArtifactId());
        this.displayName = project.getName();
    }

    private static final long serialVersionUID = 1L;
}
