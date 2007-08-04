package hudson.search;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Target;

/**
 * @author Kohsuke Kawaguchi
 */
@Retention(RUNTIME)
@Target({METHOD, FIELD})
public @interface QuickSilver {
    String[] value() default {};
}
