package hudson.api;

import java.lang.annotation.Documented;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Mark the field or the getter method whose value gets exposed
 * to the remote API.
 *
 * @author Kohsuke Kawaguchi
 * @see ExposedBean
 */
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Target({FIELD, METHOD})
public @interface Exposed {
    /**
     * Controls how visible this property is.
     *
     * <p>
     * If the value is 1, which is the default, the property will be
     * visible only when the current model object is exposed as the
     * top-level object.
     * <p>
     * If the value is 2, in addition to above, the property will still
     * be visible if the current model object is exposed as the 2nd-level
     * object.
     * <p>
     * And the rest goes in the same way. If the value is N, the object
     * is exposed as the Nth level object.
     *
     * <p>
     * So bigger the number, more important the property is.  
     */
    int visibility() default 1;

    /**
     * Name of the exposed property.
     * <p>
     * This token is used as the XML element name or the JSON property name.
     * The default is to use the Java property name.
     */
    String name() default "";
}
