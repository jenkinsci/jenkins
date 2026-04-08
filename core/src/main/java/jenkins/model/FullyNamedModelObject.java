package jenkins.model;

import hudson.model.ModelObject;
import jenkins.security.stapler.StaplerAccessibleType;

/**
 * A model object that has a human-readable full name. This is usually valid when nested as part of an object hierarchy.
 *
 * <p>
 * This interface is used to mark objects that can be qualified in the context of a Jenkins instance.
 * It is typically used for objects that are part of the Jenkins model and can be referenced by their names.
 *
 * @see ModelObject
 */
@StaplerAccessibleType
public interface FullyNamedModelObject extends ModelObject {
    /**
     * Works like {@link #getDisplayName()} but return
     * the full path that includes all the display names
     * of the ancestors in an unspecified format.
     */
    String getFullDisplayName();
}
