package jenkins.data.parameterType;

import com.google.common.primitives.Primitives;
import org.apache.commons.beanutils.Converter;

import java.lang.reflect.Type;
import java.util.Stack;

/**
 * {@link ParameterType} for data that can be represented by a single value.

 * @author Jesse Glick
 * @author Anderw Bayer
 */
public final class AtomicType<T> extends ParameterType<Class<T>> {

    private final Converter converter;

    AtomicType(Class<T> clazz) {
        super(clazz);
        converter = (t,v) -> v;
    }

    public AtomicType(Class<T> clazz, Converter converter) {
        super(clazz);
        this.converter = converter;
    }

    public Class<T> getType() {
        return getActualType();
    }

    @Override
    public void toString(StringBuilder b, Stack<Class<?>> modelTypes) {
        b.append(Primitives.unwrap((Class<?>) getActualType()).getSimpleName());
    }
}
