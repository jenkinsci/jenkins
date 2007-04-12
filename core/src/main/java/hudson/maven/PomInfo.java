package hudson.maven;

import org.apache.maven.project.MavenProject;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.Extension;
import org.apache.maven.model.ReportPlugin;

import java.io.Serializable;
import java.util.Set;
import java.util.HashSet;
import java.util.List;

/**
 * Serializable representation of the key information obtained from Maven POM.
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

    /**
     * The default goal specified in POM or null.
     */
    public final String defaultGoal;

    /**
     * Parent module.
     */
    public final PomInfo parent;

    public PomInfo(MavenProject project, PomInfo parent, String relPath) {
        this.name = new ModuleName(project);
        this.displayName = project.getName();
        this.defaultGoal = project.getDefaultGoal();
        this.relativePath = relPath;
        this.parent = parent;

        for (Dependency dep : (List<Dependency>)project.getDependencies())
            dependencies.add(new ModuleName(dep));

        MavenProject parentProject = project.getParent();
        if(parentProject!=null)
            dependencies.add(new ModuleName(parentProject));

        addPluginsAsDependencies(project.getBuildPlugins(),dependencies);
        addReportPluginsAsDependencies(project.getReportPlugins(),dependencies);

        List<Extension> extensions = project.getBuildExtensions();
        if(extensions!=null)
            for (Extension ext : extensions)
                dependencies.add(new ModuleName(ext));

        // when the parent POM uses a plugin and builds a plugin at the same time,
        // the plugin module ends up depending on itself
        dependencies.remove(name);
    }

    private void addPluginsAsDependencies(List<Plugin> plugins, Set<ModuleName> dependencies) {
        if(plugins==null)   return;
        for (Plugin p : plugins)
            dependencies.add(new ModuleName(p));
    }

    private void addReportPluginsAsDependencies(List<ReportPlugin> plugins, Set<ModuleName> dependencies) {
        if(plugins==null)   return;
        for (ReportPlugin p : plugins)
            dependencies.add(new ModuleName(p));
    }

    /**
     * Avoids dependency cycles.
     *
     * <p>
     * People often write configuration in parent POMs that use the plugin
     * which is a part of the build. To avoid this kind of dependency,
     * make sure parent POMs don't depend on a child module.
     */
    /*package*/ void cutCycle() {
        for(PomInfo p=parent; p!=null; p=p.parent) {
            if(p.dependencies.contains(name))
                p.dependencies.remove(name);
        }
    }

    private static final long serialVersionUID = 1L;
}
