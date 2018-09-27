package jenkins.data;

import java.util.Stack;

/**
 *
 * @author Jesse Glick
 * @author Anderw Bayer
 */
public final class HomogeneousObjectType extends ParameterType {
    private final DataModel<?> model;

    HomogeneousObjectType(DataModel<?> model) {
        super(model.getType());
        this.model = model;
    }

    public Class<?> getType() {
        return (Class) getActualType();
    }

    /**
     * The schema representing a type of nested object.
     */
    public DataModel<?> getSchemaType() {
        return model;
    }

    /**
     * The actual class underlying the type.
     */
    @Override
    void toString(StringBuilder b, Stack<Class<?>> modelTypes) {
        model.toString(b, modelTypes);
    }
}
