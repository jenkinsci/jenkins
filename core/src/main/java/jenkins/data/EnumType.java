package jenkins.data;

import java.util.Arrays;
import java.util.Stack;

/**
 * @author Jesse Glick
 * @author Anderw Bayer
 */
public final class EnumType extends ParameterType {
    private final String[] values;

    EnumType(Class<?> clazz, String[] values) {
        super(clazz);
        this.values = values;
    }

    public Class<?> getType() {
        return (Class) getActualType();
    }

    /**
     * A list of enumeration values.
     */
    public String[] getValues() {
        return values.clone();
    }

    @Override
    void toString(StringBuilder b, Stack<Class<?>> modelTypes) {
        b.append(((Class) getActualType()).getSimpleName()).append(Arrays.toString(values));
    }
}
