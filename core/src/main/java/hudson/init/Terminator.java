package hudson.init;

import org.jvnet.hudson.annotation_indexer.Indexed;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static hudson.init.TermMilestone.*;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * @author Kohsuke Kawaguchi
 */
@Indexed
@Documented
@Retention(RUNTIME)
@Target(METHOD)
public @interface Terminator {
    /**
     * Indicates that the specified milestone is necessary before executing this terminator.
     *
     * <p>
     * This has the identical purpose as {@link #requires()}, but it's separated to allow better type-safety
     * when using {@link TermMilestone} as a requirement (since enum member definitions need to be constant.)
     */
    TermMilestone after() default STARTED;

    /**
     * Indicates that this terminator is a necessary step before achieving the specified milestone.
     *
     * <p>
     * This has the identical purpose as {@link #attains()}. See {@link #after()} for why there are two things
     * to achieve the same goal.
     */
    TermMilestone before() default COMPLETED;

    /**
     * Indicates the milestones necessary before executing this terminator.
     */
    String[] requires() default {};

    /**
     * Indicates the milestones that this terminator contributes to.
     *
     * A milestone is considered attained if all the terminators that attains the given milestone
     * completes. So it works as a kind of join.
     */
    String[] attains() default {};

    /**
     * Key in <tt>Messages.properties</tt> that represents what this task is about. Used for rendering the progress.
     * Defaults to "${short class name}.${method Name}".
     */
    String displayName() default "";
}
