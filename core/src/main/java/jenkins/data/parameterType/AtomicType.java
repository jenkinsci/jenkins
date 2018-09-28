package jenkins.data.parameterType;

import com.google.common.primitives.Primitives;

import java.util.Stack;

/**
 * @author Jesse Glick
 * @author Anderw Bayer
 */
public final class AtomicType extends ParameterType {
    AtomicType(Class<?> clazz) {
        super(clazz);
    }

    public Class<?> getType() {
        return (Class) getActualType();
    }

    @Override
    public void toString(StringBuilder b, Stack<Class<?>> modelTypes) {
        b.append(Primitives.unwrap((Class<?>) getActualType()).getSimpleName());
    }
}
