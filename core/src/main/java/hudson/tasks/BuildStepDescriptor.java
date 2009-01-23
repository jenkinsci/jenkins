package hudson.tasks;

import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.AbstractProject;

import java.util.List;
import java.util.ArrayList;

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
     * Infers the type of the corresponding {@link BuildStep} from the outer class.
     * This version works when you follow the common convention, where a descriptor
     * is written as the static nested class of the describable class.
     *
     * @since 1.278
     */
    protected BuildStepDescriptor() {
    }

    /**
     * Returns true if this task is applicable to the given project.
     *
     * @return
     *      true to allow user to configure this post-promotion task for the given project.
     */
    public abstract boolean isApplicable(Class<? extends AbstractProject> jobType);


    /**
     * Fiters a descriptor for {@link BuildStep}s by using {@link BuildStepDescriptor#isApplicable(Class)}.
     */
    public static <T extends BuildStep&Describable<T>>
    List<Descriptor<T>> filter(List<Descriptor<T>> base, Class<? extends AbstractProject> type) {
        List<Descriptor<T>> r = new ArrayList<Descriptor<T>>(base.size());
        for (Descriptor<T> d : base) {
            if (d instanceof BuildStepDescriptor) {
                BuildStepDescriptor<T> bd = (BuildStepDescriptor<T>) d;
                if(bd.isApplicable(type))
                    r.add(bd);
            } else {
                // old plugins built before 1.150 may not implement BuildStepDescriptor
                r.add(d);
            }
        }
        return r;
    }

}
