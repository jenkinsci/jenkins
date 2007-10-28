package hudson.tasks;

import hudson.model.AbstractProject;
import hudson.model.Descriptor;

/**
 * {@link Descriptor} for {@link BuildWrapper}.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.150
 * @see BuildWrappers#WRAPPERS
 */
public abstract class BuildWrapperDescriptor extends Descriptor<BuildWrapper> {
    protected BuildWrapperDescriptor(Class<? extends BuildWrapper> clazz) {
        super(clazz);
    }

    /**
     * Returns true if this task is applicable to the given project.
     *
     * @return
     *      true to allow user to configure this post-promotion task for the given project.
     */
    public abstract boolean isApplicable(AbstractProject<?,?> item);
}

