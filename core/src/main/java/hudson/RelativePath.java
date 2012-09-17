package hudson;

import org.kohsuke.stapler.QueryParameter;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.*;

/**
 * Used in conjunction with {@link QueryParameter} to refer to
 * nearby parameters that belong to different parents.
 *
 * <p>
 * ".." refers
 * to values in the parent object, and "foo" refers to the child
 * object of the current object named "foo". They can be combined
 * with '/' like path, such as "../foo/bar", "../..", and etc.
 *
 * <p>
 * A good way to think about this is the file system structure.
 * {@code @RelativePath} is like the dirname, and {@code QueryParameter}
 * is like the basename. Together they form a relative path.
 * And because of the structured form submissions,
 * form elements are organized in a tree structure of JSON objects,
 * which is akin to directories and files.
 *
 * <p>
 * The relative path then points from the current input element
 * (for which you are doing form validation, for example) to the target
 * input element that you want to obtain the value.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.376
 */
@Documented
@Target(PARAMETER)
@Retention(RUNTIME)
public @interface RelativePath {
    String value();
}
