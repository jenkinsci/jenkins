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

    /**
     * Relative path from the root directory of the root POM to
     * the root directory of this module.
     *
     * Strings like "" (if this is the root), "abc", "foo/bar/zot".
     */
    public final String relativePath;

    public PomInfo(MavenProject project, String relPath) {
        this.name = new ModuleName(project.getGroupId(),project.getArtifactId());
        this.displayName = project.getName();
        this.relativePath = relPath;
    }

    private static final long serialVersionUID = 1L;
}
