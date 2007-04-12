package hudson.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that the class has {@link Exposed} annotations
 * on its properties to indicate which properties are written
 * as values to the remote XML/JSON API.
 *
 * <p>
 * This annotation inherits, so it only needs to be placed on the base class.
 *
 * @author Kohsuke Kawaguchi
 * @see Exposed
 */
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@Target(ElementType.TYPE)
public @interface ExposedBean {
}
