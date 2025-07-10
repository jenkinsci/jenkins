package jenkins.model;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * An object that has a name.
 * <p>
 * This interface is used to provide a consistent way to retrieve the name of an object in Jenkins.
 * It is typically implemented by objects that need to be identified by a name, such as tasks, nodes, or other model objects.
 */
public interface Nameable {
    /**
     * Returns the name of this object.
     *
     * @return the name of this object, never null.
     */
    @NonNull
    String getName();
}
