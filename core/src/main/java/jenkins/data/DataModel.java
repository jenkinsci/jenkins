package jenkins.data;

import hudson.model.Describable;
import hudson.model.Descriptor;
import jenkins.data.model.CNode;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.CheckForNull;
import java.io.IOException;
import java.util.Collection;
import java.util.Stack;
import java.util.function.Function;

/**
 * Immutable object that represents the databinding and introspection logic of an object.
 */
public abstract class DataModel<T> {
    public abstract CNode write(T object, DataContext context);
    public abstract T read(CNode input, DataContext context) throws IOException;

    /**
     * A concrete class, usually {@link Describable}.
     */
    public abstract Class<T> getType();

    /**
     * A map from parameter names to types.
     * A parameter name is either the name of an argument to a {@link DataBoundConstructor},
     * or the JavaBeans property name corresponding to a {@link DataBoundSetter}.
     *
     * <p>
     * Sorted by the mandatory parameters first (in the order they are specified in the code),
     * followed by optional arguments.
     */
    public abstract Collection<? extends DataModelParameter> getParameters();

    public DataModelParameter getParameter(String name) {
        for (DataModelParameter p : getParameters()) {
            if (p.getName().equals(name))
                return p;
        }
        return null;
    }

    /**
     * Corresponds to {@link Descriptor#getDisplayName} where available.
     */
    public String getDisplayName() {
        return getType().getSimpleName();
    }

    /**
     * True if this model's type is deprecated.
     *
     * A model is deprecated if it's {@link #getType() type} is marked as {@link Deprecated}.
     */
    public boolean isDeprecated() {
        return getType().getAnnotation(Deprecated.class) != null;
    }

    /**
     * Loads help defined for this object as a whole
     *
     * @return some HTML (in English locale), if available, else null
     * @see Descriptor#doHelp
     */
    @CheckForNull
    public abstract String getHelp() throws IOException;


    void toString(StringBuilder b, Stack<Class<?>> modelTypes) {
        b.append(getType().getSimpleName());
        if (modelTypes.contains(getType())) {
            b.append('…');
        } else {
            modelTypes.push(getType());
            try {
                b.append('(');
                boolean first = true;
                for (DataModelParameter dp : getParameters()) {
                    if (first) {
                        first = false;
                    } else {
                        b.append(", ");
                    }
                    dp.toString(b, modelTypes);
                }
                b.append(')');
            } finally {
                modelTypes.pop();
            }
        }
    }

    @Override public String toString() {
        StringBuilder b = new StringBuilder();
        toString(b, new Stack<Class<?>>());
        return b.toString();
    }

    
    

    public static <X,Y> DataModel<Y> byTranslation(Class<X> dto, Function<X, Y> reader, Function<Y, X> writer) {
        throw new UnsupportedOperationException(); // TODO
    }

    public static <T> DataModel<T> byReflection(Class<T> type) {
        throw new UnsupportedOperationException(); // TODO
    }

}
