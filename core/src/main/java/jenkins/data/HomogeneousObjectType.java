package jenkins.data;

import java.util.Stack;

/**
 *
 * @author Jesse Glick
 * @author Anderw Bayer
 */
public final class HomogeneousObjectType extends ParameterType {
    private final DescribableModel<?> type;

    HomogeneousObjectType(Class<?> actualClass) {
        super(actualClass);
        this.type = DescribableModel.of(actualClass);
    }

    public Class<?> getType() {
        return (Class) getActualType();
    }

    /**
     * The schema representing a type of nested object.
     */
    public DescribableModel<?> getSchemaType() {
        return type;
    }

    /**
     * The actual class underlying the type.
     */
    @Override
    void toString(StringBuilder b, Stack<Class<?>> modelTypes) {
        type.toString(b, modelTypes);
    }
}
