package jenkins.data;

import java.util.Map;
import java.util.Stack;

/**
 * A parameter (or array element) which could take any of the indicated concrete object types.
 *
 * @author Jesse Glick
 * @author Anderw Bayer
 */
public final class HeterogeneousObjectType extends ParameterType {
    private final Map<String,DataModel<?>> types;
    HeterogeneousObjectType(Class<?> supertype, Map<String,DataModel<?>> types) {
        super(supertype);
        this.types = types;
    }

    public Class<?> getType() {
        return (Class) getActualType();
    }

    /**
     * A map from names which could be passed to {@link ReflectiveDataModel#CLAZZ} to types of allowable nested objects.
     */
    public Map<String,DataModel<?>> getTypes() {
        return types;
    }

    @Override
    void toString(StringBuilder b, Stack<Class<?>> modelTypes) {
        Class<?> type = getType();
        b.append(type.getSimpleName());
        if (modelTypes.contains(type)) {
            b.append('…');
        } else {
            modelTypes.push(type);
            try {
                b.append('{');
                boolean first = true;
                for (Map.Entry<String, DataModel<?>> entry : types.entrySet()) {
                    if (first) {
                        first = false;
                    } else {
                        b.append(" | ");
                    }
                    String key = entry.getKey();
                    DataModel<?> model = entry.getValue();
                    if (!key.equals(model.getType().getSimpleName())) {
                        b.append(key).append('~');
                    }
                    model.toString(b, modelTypes);
                }
                b.append('}');
            } finally {
                modelTypes.pop();
            }
        }
    }
}
