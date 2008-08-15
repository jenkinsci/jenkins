package org.jvnet.hudson.test.recipes;

import org.jvnet.hudson.test.HudsonTestCase;

import java.lang.annotation.Documented;
import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Target;
import java.lang.annotation.Annotation;

import junit.framework.TestCase;

/**
 * Meta-annotation for recipe annotations, which controls
 * the test environment set up.
 *
 * @author Kohsuke Kawaguchi
 */
@Retention(RUNTIME)
@Documented
@Target(ANNOTATION_TYPE)
public @interface Recipe {
    /**
     * Specifies the class that sets up the test environment.
     *
     * <p>
     * When a recipe annotation is placed on a test method, 
     */
    Class<? extends Runner> value();

    /**
     * The code that implements the recipe semantics.
     *
     * @param <T>
     *      The recipe annotation associated with this runner.
     */
    public abstract class Runner<T extends Annotation> {
        /**
         * Called during {@link TestCase#setUp()} to prepare the test environment.
         */
        public void setup(HudsonTestCase testCase, T recipe) throws Exception {}
        /**
         * Called during {@link TestCase#tearDown()} to shut down the test environment.
         */
        public void tearDown(HudsonTestCase testCase, T recipe) throws Exception {}
    }
}
