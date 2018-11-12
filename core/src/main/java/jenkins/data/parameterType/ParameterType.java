package jenkins.data.parameterType;

import com.google.common.primitives.Primitives;
import jenkins.data.DataModel;
import jenkins.data.DataModelRegistry;
import org.apache.commons.beanutils.Converter;
import org.jvnet.tiger_types.Types;
import org.kohsuke.stapler.Stapler;

import javax.annotation.Nonnull;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.TreeMap;
import java.util.logging.Logger;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import static java.util.logging.Level.*;

/**
 * A type of a parameter to a class.
 *
 * @author Jesse Glick
 * @author Anderw Bayer
 */
public abstract class ParameterType<T extends Type> {
    @Nonnull
    private final T actualType;

    public T getActualType() {
        return actualType;
    }

    ParameterType(T actualType) {
        this.actualType = actualType;
    }

    /**
     * Creates the right {@link ParameterType} tree, given a Java type.
     */
    public static ParameterType of(Type type) {
        final Class c = Types.erasure(type);
        try {
            if (c == String.class || Primitives.unwrap(c).isPrimitive()) {
                return new AtomicType(c);
            }
            if (Enum.class.isAssignableFrom(c)) {
                return new EnumType(c.asSubclass(Enum.class));
            }
            final Converter converter = Stapler.lookupConverter(c);
            if (converter != null) {
                return new AtomicType(String.class, converter);
            }
            if (c.isArray()) {
                return new ArrayType(c,of(c.getComponentType()));
            }
            if (c.isAssignableFrom(Collections.class)) {
                return new ArrayType(type, of(Types.getTypeArgument(Types.getBaseClass(type,Collection.class), 0, Object.class)));
            }

            // Assume it is a nested object of some sort.
            DataModelRegistry registry = DataModelRegistry.get();
            Set<Class<?>> subtypes = registry.findSubtypes(c);
            if ((subtypes.isEmpty() && !Modifier.isAbstract(c.getModifiers())) || subtypes.equals(Collections.singleton(c))) {
                // Probably homogeneous. (Might be concrete but subclassable.)
                return new ComposedObjectType(registry.lookupOrFail(c));
            } else {
                // Definitely heterogeneous.
                final Map<String, List<Class<?>>> subtypesBySimpleName = subtypes.stream()
                        .collect(Collectors.groupingBy(Class::getSimpleName));

                Map<String,DataModel<?>> types = new TreeMap<>();
                for (Map.Entry<String,List<Class<?>>> entry : subtypesBySimpleName.entrySet()) {
                    if (entry.getValue().size() == 1) { // normal case: unambiguous via simple name
                        try {
                            types.put(entry.getKey(), registry.lookupOrFail(entry.getValue().get(0)));
                        } catch (Exception x) {
                            LOGGER.log(FINE, "skipping subtype", x);
                        }
                    } else { // have to disambiguate via FQN
                        for (Class<?> subtype : entry.getValue()) {
                            try {
                                types.put(subtype.getName(), registry.lookupOrFail(subtype));
                            } catch (Exception x) {
                                LOGGER.log(FINE, "skipping subtype", x);
                            }
                        }
                    }
                }
                return new HeterogeneousObjectType(c, types);
            }
        } catch (Exception x) {
            // return new ErrorType(x, type) // I don't get what this is for;
        }
        throw new UnsupportedOperationException("do not know how to categorize attributes of type " + type);
    }

    /**
     * Flavor of toString that avoids infinite recursion.
     */
    public abstract void toString(StringBuilder b, Stack<Class<?>> modelTypes);

    @Override
    public final String toString() {
        StringBuilder b = new StringBuilder();
        toString(b, new Stack<>());
        return b.toString();
    }

    private static final Logger LOGGER = Logger.getLogger(ParameterType.class.getName());
}
