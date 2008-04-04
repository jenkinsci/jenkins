package hudson.model;

/**
 * {@link Descriptor} for {@link UserProperty}.
 * 
 * @author Kohsuke Kawaguchi
 */
public abstract class UserPropertyDescriptor extends Descriptor<UserProperty> {
    protected UserPropertyDescriptor(Class<? extends UserProperty> clazz) {
        super(clazz);
    }

    /**
     * Creates a default instance of {@link UserProperty} to be associated
     * with {@link User} object that wasn't created from a persisted XML data.
     *
     * <p>
     * See {@link User} class javadoc for more details about the life cycle
     * of {@link User} and when this method is invoked.
     *
     * @return null
     *      if the implementation choose not to add any property object for such user.
     */
    public abstract UserProperty newInstance(User user);

    /**
     * Whether or not the described property is enabled in the current context.
     * Defaults to true.  Over-ride in sub-classes as required.
     *
     * <p>
     * Returning false from this method essentially has the same effect of
     * making Hudson behaves as if this {@link UserPropertyDescriptor} is
     * not a part of {@link UserProperties#LIST}.
     *
     * <p>
     * This mechanism is useful if the availability of the property is
     * contingent of some other settings. 
     */
    public boolean isEnabled() {
        return true;
    }
}
