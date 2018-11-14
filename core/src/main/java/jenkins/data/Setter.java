package jenkins.data;

/**
 * Abstracts away how to set a value to field or via a setter method.
 *
 * @author Kohsuke Kawaguchi
 */
@FunctionalInterface
public interface Setter<Target, T> {

    /**
     * Sets the given value to the method/field that this {@link Setter} encapsulates.
     */
    void set(Target instance, T value) throws Exception;

}
