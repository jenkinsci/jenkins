package hudson.maven;

import hudson.ExtensionPoint;
import org.apache.maven.project.MavenProject;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Extension point in Hudson to find additional dependencies from {@link MavenProject}.
 *
 * <p>
 * Maven plugin configurations often have additional configuration entries to specify
 * artifacts that a build depends on. Plugins can contribute an implementation of
 * this interface to find such hidden dependencies.
 *
 * <p>
 *
 *
 * @author Kohsuke Kawaguchi
 * @since 1.264
 * @see HUDSON-2685
 */
public abstract class ModuleDependencyLocator implements ExtensionPoint {
    /**
     * Discovers hidden dependencies.
     *
     * @param project
     *      In memory representation of Maven project, from which the hidden dependencies will be extracted.
     *      Never null.
     * @param pomInfo
     *      Partially filled {@link PomInfo} object. Dependencies returned from this method will be
     *      added to this object by the caller.
     */
    public abstract Collection<ModuleDependency> find(MavenProject project, PomInfo pomInfo);

    /**
     * Facade of {@link ModuleDependencyLocator}.
     */
    /*package*/ static class ModuleDependencyLocatorFacade extends ModuleDependencyLocator {
        private final List<ModuleDependencyLocator> members = new CopyOnWriteArrayList<ModuleDependencyLocator>();

        @Override
        public Collection<ModuleDependency> find(MavenProject project, PomInfo pomInfo) {
            Set<ModuleDependency> r = new HashSet<ModuleDependency>();
            for (ModuleDependencyLocator m : members)
                r.addAll(m.find(project,pomInfo));
            return r;
        }
    }
}
