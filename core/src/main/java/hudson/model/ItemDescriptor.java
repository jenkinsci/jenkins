package hudson.model;

/**
 * {@link Descriptor} for {@link Item}s.
 *
 * <p>
 * Historically, {@link TopLevelItemDescriptor} came into being first, so this descriptor is parametered
 * to retain that signature identical.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.296
 */
public abstract class ItemDescriptor<T extends Item&Describable<T>> extends Descriptor<T> {
    protected ItemDescriptor(Class<? extends T> clazz) {
        super(clazz);
    }

    protected ItemDescriptor() {
    }

    /**
     * {@link ItemDescriptor}s often uses other descriptors to decorate itself.
     * This method allows the subtype of {@link ItemDescriptor}s to filter them out.
     *
     * <p>
     * This is useful for a workflow/company specific job type that wants to eliminate
     * options that the user would see.
     */
    public boolean isApplicable(Descriptor descriptor) {
        return true;
    }
}
