package hudson.model;

/**
 * Classes that are described by {@link Descriptor}.
 *
 * @author Kohsuke Kawaguchi
 */
public interface Describable<T extends Describable<T>> {
    /**
     * Gets the descriptor for this instance.
     *
     * <p>
     * {@link Descriptor} is a singleton for every concrete {@link Describable}
     * implementation, so if <tt>a.getClass()==b.getClass()</tt> then
     * <tt>a.getDescriptor()==b.getDescriptor()</tt> must hold.
     */
    Descriptor<T> getDescriptor();
}
