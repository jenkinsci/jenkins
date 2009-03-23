package hudson.model;

/**
 * Adds {@link #getDescriptorByName(String)} to bind {@link Descriptor}s to URL.
 * Binding them at some specific object (instead of {@link Hudson}), allows
 * {@link Descriptor}s to perform context-specific form field validation.
 *
 * <p>
 * {@link Descriptor#getCheckUrl(String)} finds an ancestor with this interface
 * and generates URLs against it.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.294
 * @see Descriptor#getCheckUrl(String)
 */
public interface DescriptorByNameOwner extends ModelObject {
    /**
     * Exposes all {@link Descriptor}s by its name to URL.
     *
     * <p>
     * Implementation should always delegate to {@link Hudson#getDescriptorByName(String)}.
     *
     * @param className
     *      Either fully qualified class name (recommended) or the short name.
     */
    Descriptor getDescriptorByName(String className);    
}
