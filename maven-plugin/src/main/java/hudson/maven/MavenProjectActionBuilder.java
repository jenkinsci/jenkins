package hudson.maven;

import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.tasks.BuildStep;

import java.util.Collection;

/**
 * Can contribute to project actions.
 * 
 *
 * @author Kohsuke Kawaguchi
 * @see MavenBuildProxy#registerAsProjectAction(MavenProjectActionBuilder)
 */
public interface MavenProjectActionBuilder {
    /**
     * Equivalent of {@link BuildStep#getProjectActions(AbstractProject)}.
     *
     * <p>
     * Registers a transient action to {@link MavenModule} when it's rendered.
     * This is useful if you'd like to display an action at the module level.
     *
     * <p>
     * Since this contributes a transient action, the returned {@link Action}
     * will not be serialized.
     *
     * <p>
     * For this method to be invoked, call
     * {@link MavenBuildProxy#registerAsProjectAction(MavenProjectActionBuilder)} during the build.
     *
     * @return
     *      can be empty but never null.
     * @since 1.341
     */
    public Collection<? extends Action> getProjectActions(MavenModule module);
}
