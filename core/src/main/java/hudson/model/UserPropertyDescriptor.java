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
     * with {@link User} that doesn't have any back up data store.
     *
     * @return null
     *      if the implementation choose not to add any proeprty object for such user.
     */
    public abstract UserProperty newInstance(User user);
}
