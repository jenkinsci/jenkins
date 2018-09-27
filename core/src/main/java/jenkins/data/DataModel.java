package jenkins.data;

import hudson.model.Describable;
import hudson.model.Descriptor;
import jenkins.data.model.CNode;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.CheckForNull;
import java.io.IOException;
import java.util.Collection;
import java.util.function.Function;

/**
 * Immutable object that represents the databinding and introspection logic of an object.
 */
public interface DataModel<T> {
    CNode write(T object, WriteDataContext context);
    T read(CNode input, ReadDataContext context) throws IOException;

    /**
     * A concrete class, usually {@link Describable}.
     */
    Class<T> getType();

    /**
     * A map from parameter names to types.
     * A parameter name is either the name of an argument to a {@link DataBoundConstructor},
     * or the JavaBeans property name corresponding to a {@link DataBoundSetter}.
     *
     * <p>
     * Sorted by the mandatory parameters first (in the order they are specified in the code),
     * followed by optional arguments.
     */
    Collection<? extends DataModelParameter> getParameters();

    default DataModelParameter getParameter(String name) {
        for (DataModelParameter p : getParameters()) {
            if (p.getName().equals(name))
                return p;
        }
        return null;
    }

    /**
     * Corresponds to {@link Descriptor#getDisplayName} where available.
     */
    default String getDisplayName() {
        return getType().getSimpleName();
    }

    /**
     * True if this model's type is deprecated.
     *
     * A model is deprecated if it's {@link #getType() type} is marked as {@link Deprecated}.
     */
    default boolean isDeprecated() {
        return getType().getAnnotation(Deprecated.class) != null;
    }

    /**
     * Loads help defined for this object as a whole
     *
     * @return some HTML (in English locale), if available, else null
     * @see Descriptor#doHelp
     */
    @CheckForNull
    String getHelp() throws IOException;

    
    

    static <X,Y> DataModel<Y> byTranslation(Class<X> dto, Function<X,Y> reader, Function<Y,X> writer) {
        throw new UnsupportedOperationException(); // TODO
    }

    static <T> DataModel<T> byReflection(Class<T> type) {
        throw new UnsupportedOperationException(); // TODO
    }

}
