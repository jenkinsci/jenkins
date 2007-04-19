package hudson.model;

import hudson.model.Queue.Task;

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
public interface BuildableItem extends Item, Task {
    boolean scheduleBuild();
}
