package org.jvnet.hudson.test;

import org.jvnet.hudson.test.recipes.Recipe;
import org.jvnet.hudson.test.recipes.WithPlugin;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * An annotation for test methods that do not require the {@link JenkinsRule}/{@link HudsonTestCase} to create and tear down the jenkins
 * instance.
 */
@Retention(RUNTIME)
@Documented
@Target(METHOD)
@Recipe(WithoutJenkins.RunnerImpl.class)
public @interface WithoutJenkins {
    class RunnerImpl extends Recipe.Runner<WithPlugin> {
        // bogus. this recipe is handled differently by HudsonTestCase
    }
}
