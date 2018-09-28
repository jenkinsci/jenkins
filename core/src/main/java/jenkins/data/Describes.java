package jenkins.data;

import hudson.Extension;
import hudson.model.Describable;
import org.jvnet.hudson.annotation_indexer.Indexed;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.reflect.Type;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.*;

/**
 * Put on {@link DataModel} subtype to indicate which {@link Describable} object the model is describing.
 *
 * @author Kohsuke Kawaguchi
 */
@Indexed
@Target({TYPE, FIELD, METHOD})
@Retention(RUNTIME)
@Documented
public @interface Describes {
    /**
     * What class this model describes?
     */
    Class value();

    @Extension
    public static final class DataModelFactoryImpl implements DataModelFactory {
        @Override
        public DataModel find(Type type) {
            // TODO: list up Describes and find those
            return null;
        }
    }
}
