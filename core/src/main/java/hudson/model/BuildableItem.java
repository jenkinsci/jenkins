package hudson.model;

/**
 * {@link Item} that can be "built", for
 * whatever meaning of "build".
 *
 * <p>
 * This interface is used by utility code.
 *
 * @author Kohsuke Kawaguchi
 * @see BuildAuthorizationToken
 */
public interface BuildableItem extends Item {
    void scheduleBuild();
}
