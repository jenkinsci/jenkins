package hudson.tasks;

import hudson.model.Describable;
import hudson.model.Action;
import hudson.model.Project;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.ExtensionPoint;

/**
 * {@link BuildStep}s that perform the actual build.
 *
 * <p>
 * To register a custom {@link Builder} from a plugin,
 * add it to {@link BuildStep#BUILDERS}.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class Builder implements BuildStep, Describable<Builder>, ExtensionPoint {
    /**
     * Default implementation that does nothing.
     */
    public boolean prebuild(Build build, BuildListener listener) {
        return true;
    }

    /**
     * Default implementation that does nothing.
     */
    public Action getProjectAction(Project project) {
        return null;
    }
}
