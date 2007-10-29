package hudson.tasks;

import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.AbstractProject;

/**
 * {@link Descriptor} for {@link Builder} and {@link Publisher}.
 *
 * <p>
 * For compatibility reasons, plugins developed before 1.150 may not extend from this descriptor type.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.150
 */
public abstract class BuildStepDescriptor<T extends BuildStep & Describable<T>> extends Descriptor<T> {
    protected BuildStepDescriptor(Class<? extends T> clazz) {
        super(clazz);
    }

    /**
     * Returns true if this task is applicable to the given project.
     *
     * @return
     *      true to allow user to configure this post-promotion task for the given project.
     */
    public abstract boolean isApplicable(Class<? extends AbstractProject> jobType);
}
