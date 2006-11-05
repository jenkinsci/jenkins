package hudson.model;

/**
 * Classes that are described by {@link Descriptor}.
 *
 * @author Kohsuke Kawaguchi
 */
public interface Describable<T extends Describable<T>> {
    /**
     * Gets the descriptor for this instance.
     */
    Descriptor<T> getDescriptor();
}
