/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.init;

import hudson.Extension;
import org.jvnet.hudson.annotation_indexer.Indexed;
import org.jvnet.hudson.reactor.Task;

import java.lang.annotation.Documented;
import static java.lang.annotation.ElementType.METHOD;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Target;

import static hudson.init.InitMilestone.STARTED;
import static hudson.init.InitMilestone.COMPLETED;

/**
 * Placed on methods to indicate that this method is to be run during the Jenkins start up to perform
 * some sort of initialization tasks.
 *
 * <h3>Example</h3>
 * <pre>
   &#64;Initializer(after=JOB_LOADED)
   public static void init() throws IOException {
       ....
   }
 * </pre>
 *
 * <p>
 * The method in question can be either {@code static} or an instance method. When used with instance
 * methods, those methods have to be on a class annotated with {@link Extension} and marked as
 * {@link #after()} {@link InitMilestone#PLUGINS_PREPARED}.
 * 
 * @author Kohsuke Kawaguchi
 */
@Indexed
@Documented
@Retention(RUNTIME)
@Target(METHOD)
public @interface Initializer {
    /**
     * Indicates that the specified milestone is necessary before executing this initializer.
     *
     * <p>
     * This has the identical purpose as {@link #requires()}, but it's separated to allow better type-safety
     * when using {@link InitMilestone} as a requirement (since enum member definitions need to be constant.)
     */
    InitMilestone after() default STARTED;

    /**
     * Indicates that this initializer is a necessary step before achieving the specified milestone.
     *
     * <p>
     * This has the identical purpose as {@link #attains()}. See {@link #after()} for why there are two things
     * to achieve the same goal.
     */
    InitMilestone before() default COMPLETED;

    /**
     * Indicates the milestones necessary before executing this initializer.
     */
    String[] requires() default {};

    /**
     * Indicates the milestones that this initializer contributes to.
     *
     * A milestone is considered attained if all the initializers that attains the given milestone
     * completes. So it works as a kind of join.
     */
    String[] attains() default {};

    /**
     * Key in <tt>Messages.properties</tt> that represents what this task is about. Used for rendering the progress.
     * Defaults to "${short class name}.${method Name}".
     */
    String displayName() default "";

    /**
     * Should the failure in this task prevent Hudson from starting up?
     *
     * @see Task#failureIsFatal() 
     */
    boolean fatal() default true;
}
