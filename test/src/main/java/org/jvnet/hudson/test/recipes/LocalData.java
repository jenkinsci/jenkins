package org.jvnet.hudson.test.recipes;

import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.HudsonHomeLoader.Local;

import java.lang.annotation.Documented;
import java.lang.annotation.Target;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static java.lang.annotation.ElementType.METHOD;

/**
 * Runs a test case with a data set local to test method or the test class.
 *
 * <p>
 * This recipe allows your test case to start with the preset <tt>HUDSON_HOME</tt> data loaded
 * either from your test method or from the test class.
 *
 * <p>
 * For example, if the test method if <tt>org.acme.FooTest.testBar()</tt>, then
 * you can have your test data in one of the following places in resources folder
 * (typically <tt>src/test/resources</tt>):
 *
 * <ol>
 * <li>
 * Under <tt>org/acme/FooTest/testBar</tt> directory (that is, you'll have
 * <tt>org/acme/FooTest/testBar/config.xml</tt>), in the same layout as in the real <tt>HUDSON_HOME</tt> directory.
 * <li>
 * In <tt>org/acme/FooTest/testBar.zip</tt> as a zip file.
 * <li>
 * Under <tt>org/acme/FooTest</tt> directory (that is, you'll have
 * <tt>org/acme/FooTest/config.xml</tt>), in the same layout as in the real <tt>HUDSON_HOME</tt> directory.
 * <li>
 * In <tt>org/acme/FooTest.zip</tt> as a zip file.
 * </ol>
 *
 * <p>
 * Search is performed in this specific order. The fall back mechanism allows you to write
 * one test class that interacts with different aspects of the same data set, by associating
 * the dataset with a test class, or have a data set local to a specific test method.
 *
 * <p>
 * The choice of zip and directory depends on the nature of the test data, as well as the size of it.
 *
 * @author Kohsuke Kawaguchi
 */
@Documented
@Recipe(LocalData.RunnerImpl.class)
@Target(METHOD)
@Retention(RUNTIME)
public @interface LocalData {
    public class RunnerImpl extends Recipe.Runner<LocalData> {
        public void setup(HudsonTestCase testCase, LocalData recipe) throws Exception {
            testCase.with(new Local(testCase.getClass().getMethod(testCase.getName())));
        }
    }
}
