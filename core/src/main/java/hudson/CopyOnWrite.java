package hudson;

import java.lang.annotation.Documented;
import static java.lang.annotation.ElementType.FIELD;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.SOURCE;
import java.lang.annotation.Target;

/**
 * Represents fields that are protected for concurrency by the copy-on-write semantics.
 *
 * <p>
 * Fields marked by this annotation always holds on to an immutable collection.
 * A change to the collection must be done by first creating a new collection
 * object, making changes, then replacing the reference atomically.
 *
 * <p>
 * This allows code to access the field without synchronization, and
 * greatly reduces the risk of dead-lock bugs.
 *
 * <p>
 * The field marked with this annotation usually needs to be marked as
 * <tt>volatile</tt>.
 *
 * @author Kohsuke Kawaguchi
 */
@Retention(SOURCE)
@Documented
@Target(FIELD)
public @interface CopyOnWrite {
}
