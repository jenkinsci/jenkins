package org.jvnet.hudson.test.recipes;

import org.jvnet.hudson.test.HudsonTestCase;

import java.lang.annotation.Documented;
import static java.lang.annotation.ElementType.METHOD;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Target;

/**
 * Runs a test case with one of the preset HUDSON_HOME data set.
 *
 * @author Kohsuke Kawaguchi
 */
@Documented
@Recipe(PresetData.RunnerImpl.class)
@Target(METHOD)
@Retention(RUNTIME)
public @interface PresetData {
    /**
     * One of the preset data to choose from.
     */
    DataSet value();

    public enum DataSet {
        /**
         * Secured Hudson that has no anonymous read access.
         * Any logged in user can do anything.
         */
        NO_ANONYMOUS_READACCESS
    }

    public class RunnerImpl extends Recipe.Runner<PresetData> {
        public void setup(HudsonTestCase testCase, PresetData recipe) {
            testCase.withPresetData(recipe.value().name().toLowerCase().replace('_','-'));
        }
    }
}
