package hudson.maven;

import org.apache.maven.project.MavenProject;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.Extension;

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
     *
     * See Maven's ProjectSorter class for the definition of the 'dependencies' in Maven.
     */
    public final Set<ModuleName> dependencies = new HashSet<ModuleName>();

    public PomInfo(MavenProject project, String relPath) {
        this.name = new ModuleName(project);
        this.displayName = project.getName();
        this.relativePath = relPath;

        for (Dependency dep : (List<Dependency>)project.getDependencies())
            dependencies.add(new ModuleName(dep));

        MavenProject parent = project.getParent();
        if(parent!=null)
            dependencies.add(new ModuleName(parent));

        addPluginsAsDependencies(project.getBuildPlugins(),dependencies);
        addPluginsAsDependencies(project.getReportPlugins(),dependencies);

        List<Extension> extensions = project.getBuildExtensions();
        if(extensions!=null)
            for (Extension ext : extensions)
                dependencies.add(new ModuleName(ext));
    }

    private void addPluginsAsDependencies(List<Plugin> plugins, Set<ModuleName> dependencies) {
        if(plugins==null)   return;
        for (Plugin p : plugins)
            dependencies.add(new ModuleName(p));
    }

    private static final long serialVersionUID = 1L;
}
