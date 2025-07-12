package jenkins.model;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * An interface for objects that have a name and a parent, so exposing a full name.
 */
public interface FullyNameable {
    /**
     * Returns the full name of this object, which is a qualified name
     * that includes the names of all its ancestors, separated by '/'.
     *
     * @return the full name of this object.
     */
    @NonNull
    String getFullName();
}
