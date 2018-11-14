package jenkins.data.parameterType;

import hudson.ExtensionPoint;
import hudson.model.Descriptor;
import jenkins.data.DataContext;
import jenkins.data.DataModel;
import jenkins.data.SymbolLookup;
import jenkins.data.tree.Mapping;
import jenkins.data.tree.TreeNode;

import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.stream.Collectors;

/**
 * A parameter (or array element) which could take any of the indicated concrete object types.
 *
 * @author Jesse Glick
 * @author Anderw Bayer
 */
public final class HeterogeneousObjectType<T extends ExtensionPoint> extends ParameterType<Class<T>> {
    private final Set<Class<T>> types;

    HeterogeneousObjectType(Class<T> supertype, Set<Class<T>> types) {
        super(supertype);
        this.types = types;
    }

    public Class<T> getType() {
        return getActualType();
    }

    @Override
    public Object from(TreeNode node, DataContext context) {
        final Mapping mapping = node.asMapping();

        // Pipeline support $class - legacy ? Shall we drop this ?
        final String clazz = mapping.getScalarValue("$class");
        if (clazz != null) {
            return types.stream()
                .filter(c -> c.getSimpleName().equals(clazz))
                .findAny()
                .map(c -> context.lookupOrFail(c).read(node, context))
                .orElseThrow(() -> new IllegalArgumentException("no implementation for type " + getType() + " with class name "+ clazz));
        }

        // This is how Pipeline handle symbols
        String symbol = mapping.getScalarValue("type"); // FIXME risk of collision with a legitimate parameter.

        // This is how CasC handle symbols
        if (symbol == null && mapping.size() == 1) {
            symbol = mapping.keySet().iterator().next();
        }

        if (symbol != null) {
            final Descriptor<?> descriptor = SymbolLookup.get().findDescriptor(getActualType(), symbol);
            if (descriptor == null) throw new IllegalArgumentException("no implementation for type " + getType() + " with symbol "+ symbol);
            return context.lookupOrFail(descriptor.getKlass().toJavaClass()).read(node, context);
        }


        throw new IllegalArgumentException("need to specify symbol of a concrete "+getType()+" implementation");
    }


    @Override
    public void toString(StringBuilder b, Stack<Class<?>> modelTypes) {
        Class<?> type = getType();
        b.append(type.getSimpleName());
        if (modelTypes.contains(type)) {
            b.append('…');
        } else {
            modelTypes.push(type);
            try {
                b.append('{');
                // TODO
                b.append('}');
            } finally {
                modelTypes.pop();
            }
        }
    }
}
