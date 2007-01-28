package hudson.maven;

import org.apache.maven.project.MavenProject;
import org.apache.maven.model.Dependency;

import java.io.Serializable;
import java.util.Set;
import java.util.HashSet;
import java.util.List;

/**
 * Serialziable representation of the key information obtained from Maven POM.
 *
 * <p>
 * This is used for the master to introspect POM, which is only available
 * as {@link MavenProject} object on slaves.
 *
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

    /**
     * Dependency of this project.
     */
    public final Set<ModuleName> dependencies = new HashSet<ModuleName>();

    public PomInfo(MavenProject project, String relPath) {
        this.name = new ModuleName(project.getGroupId(),project.getArtifactId());
        this.displayName = project.getName();
        this.relativePath = relPath;

        for (Dependency dep : (List<Dependency>)project.getDependencies())
            dependencies.add(new ModuleName(dep.getGroupId(),dep.getArtifactId()));
    }

    private static final long serialVersionUID = 1L;
}
