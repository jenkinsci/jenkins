package jenkins.model;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Document a property is actually managed by another component, and the annotated field/setter shouldn't be used
 * directly.
 * Typical usage is for {@link Jenkins} fields being managed by some {@link GlobalConfiguration}.
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
@Retention(RUNTIME)
@Target({FIELD, METHOD})
@Documented
public @interface ManagedBy {

    Class value();
}
