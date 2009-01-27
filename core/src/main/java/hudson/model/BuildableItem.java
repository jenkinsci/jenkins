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
 */
public interface BuildableItem extends Item, Task {
    boolean scheduleBuild(String triggeredBy);
    boolean scheduleBuild(int quietPeriod, String triggeredBy);
    /** @deprecated since 1.279 */
    @Deprecated boolean scheduleBuild();
    /** @deprecated since 1.279 */
    @Deprecated boolean scheduleBuild(int quietPeriod);
}
