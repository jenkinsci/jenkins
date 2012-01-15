package org.jvnet.hudson.test;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * An annotation for test methods that do not require the {@link JenkinsRule} to create and tear down the jenkins
 * instance.
 */
@Retention(RUNTIME)
@Documented
@Target(METHOD)
public @interface WithoutJenkins {
}
