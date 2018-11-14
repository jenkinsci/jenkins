package jenkins.data.parameterType;

import org.apache.commons.lang.StringUtils;

import java.util.Arrays;
import java.util.Set;
import java.util.Stack;
import java.util.stream.Collectors;

/**
 * @author Jesse Glick
 * @author Anderw Bayer
 */
public final class EnumType<E extends Enum<E>> extends AtomicType<E> {

    EnumType(Class<E> clazz) {
        super(clazz);
    }

    /**
     * A list of enumeration values.
     */
    public Set<String> getValues() {
        return Arrays.stream(getType().getEnumConstants())
                .map(Enum::name)
                .collect(Collectors.toSet());
    }

    @Override
    public void toString(StringBuilder b, Stack<Class<?>> modelTypes) {
        b.append((getActualType()).getSimpleName())
         .append(StringUtils.join(getValues(), ","));
    }
}
