package jenkins.data;

import hudson.model.Descriptor;
import jenkins.data.tree.Mapping;
import jenkins.data.tree.TreeNode;

import javax.annotation.CheckForNull;
import javax.xml.ws.Holder;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Stack;
import java.util.function.Function;

/**
 * Immutable object that represents the databinding and introspection logic of an object.
 *
 * There's one {@link DataModel} per every data-bindable {@link Class}, not {@link Type}.
 * This might feel subtle, but what this means is that if a class is parameterized, the model
 * and the serialized form doesn't consider the particular parameterization at a site of use.
 * {@link Holder} might be a good example to see this difference.
 */
public abstract class DataModel<T> {
    /**
     * Type that this model represents.
     */
    public abstract Class<T> getType();

    /**
     * Serializes an object into a tree structure and return it.
     *
     * @param context
     *      Provides access to a bigger context in which this object is being serialized.
     */
    public abstract TreeNode write(T object, DataContext context);

    /**
     * Instantiates an object from a tree structure and return the newly constructed instance.
     *
     * @param context
     *      Provides access to a bigger context in which this object is being serialized.
     */
    public abstract T read(TreeNode input, DataContext context);

    /**
     * "Schema" of this data model.
     *
     * <p>
     * If you think of a {@link DataModel} as a class, then {@link DataModelParameter}s are properties.
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


    public void toString(StringBuilder b, Stack<Class<?>> modelTypes) {
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
        toString(b, new Stack<>());
        return b.toString();
    }

    
    

    public static <X,Y> DataModel<Y> byTranslation(Class<X> dto, Function<X, Y> reader, Function<Y, X> writer) {
        throw new UnsupportedOperationException(); // TODO
    }

    public static <T> DataModel<T> byReflection(Class<T> type) {
        return ReflectiveDataModel.of(type);
    }
}
