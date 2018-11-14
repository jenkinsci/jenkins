package jenkins.data;

/**
 * Abstracts away how to set a value to field or via a setter method.
 *
 * @author Kohsuke Kawaguchi
 */
@FunctionalInterface
public interface Getter<Target, T> {

    /**
     * Read value from method/field that this {@link Getter} encapsulates.
     */
    T get(Target instance) throws Exception;

}
