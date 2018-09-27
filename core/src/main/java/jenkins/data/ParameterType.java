package jenkins.data;

import com.google.common.primitives.Primitives;
import hudson.model.Result;
import org.jvnet.tiger_types.Types;

import javax.annotation.Nonnull;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.TreeMap;
import java.util.logging.Logger;

import static java.util.logging.Level.*;

/**
 * A type of a parameter to a class.
 *
 * @author Jesse Glick
 * @author Anderw Bayer
 */
public abstract class ParameterType {
    @Nonnull
    private final Type actualType;

    public Type getActualType() {
        return actualType;
    }

    ParameterType(Type actualType) {
        this.actualType = actualType;
    }

    static ParameterType of(Type type, DataModelRegistry registry) {
        try {
            if (type instanceof Class) {
                Class<?> c = (Class<?>) type;
                if (c == String.class || Primitives.unwrap(c).isPrimitive()) {
                    return new AtomicType(c);
                }
                if (Enum.class.isAssignableFrom(c)) {
                    List<String> constants = new ArrayList<>();
                    for (Enum<?> value : c.asSubclass(Enum.class).getEnumConstants()) {
                        constants.add(value.name());
                    }
                    return new EnumType(c, constants.toArray(new String[constants.size()]));
                }
                if (c == URL.class) {
                    return new AtomicType(String.class);
                }
                if (c == Result.class) {
                    return new AtomicType(String.class);
                }
                if (c.isArray()) {
                    return new ArrayType(c,of(c.getComponentType(),registry));
                }
                // Assume it is a nested object of some sort.
                Set<Class<?>> subtypes = DescribableModel.findSubtypes(c);
                if ((subtypes.isEmpty() && !Modifier.isAbstract(c.getModifiers())) || subtypes.equals(Collections.singleton(c))) {
                    // Probably homogeneous. (Might be concrete but subclassable.)
                    return new HomogeneousObjectType(registry.lookupOrFail(c));
                } else {
                    // Definitely heterogeneous.
                    Map<String,List<Class<?>>> subtypesBySimpleName = new HashMap<>();
                    for (Class<?> subtype : subtypes) {
                        String simpleName = subtype.getSimpleName();
                        List<Class<?>> bySimpleName = subtypesBySimpleName.get(simpleName);
                        if (bySimpleName == null) {
                            subtypesBySimpleName.put(simpleName, bySimpleName = new ArrayList<>());
                        }
                        bySimpleName.add(subtype);
                    }
                    Map<String,DataModel<?>> types = new TreeMap<>();
                    for (Map.Entry<String,List<Class<?>>> entry : subtypesBySimpleName.entrySet()) {
                        if (entry.getValue().size() == 1) { // normal case: unambiguous via simple name
                            try {
                                types.put(entry.getKey(), registry.lookupOrFail(entry.getValue().get(0)));
                            } catch (Exception x) {
                                LOGGER.log(FINE, "skipping subtype", x);
                            }
                        } else { // have to diambiguate via FQN
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
            }
            if (Types.isSubClassOf(type, Collection.class)) {
                return new ArrayType(type, of(Types.getTypeArgument(Types.getBaseClass(type,Collection.class), 0, Object.class),registry));
            }
            throw new UnsupportedOperationException("do not know how to categorize attributes of type " + type);
        } catch (Exception x) {
            return new ErrorType(x, type);
        }
    }

    abstract void toString(StringBuilder b, Stack<Class<?>> modelTypes);

    @Override
    public final String toString() {
        StringBuilder b = new StringBuilder();
        toString(b, new Stack<>());
        return b.toString();
    }

    private static final Logger LOGGER = Logger.getLogger(ParameterType.class.getName());
}
