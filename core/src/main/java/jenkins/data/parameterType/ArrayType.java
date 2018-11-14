package jenkins.data.parameterType;

import jenkins.data.DataContext;
import jenkins.data.tree.Sequence;
import jenkins.data.tree.TreeNode;
import org.apache.commons.collections.iterators.ArrayIterator;

import java.lang.reflect.Type;
import java.util.Iterator;
import java.util.Stack;
import java.util.stream.Collectors;

/**
 * @author Jesse Glick
 * @author Anderw Bayer
 */
public final class ArrayType extends ParameterType {
    private final ParameterType elementType;

    ArrayType(Type actualClass, ParameterType elementType) {
        super(actualClass);
        this.elementType = elementType;
    }

    /**
     * The element type of the array or list.
     */
    public ParameterType getElementType() {
        return elementType;
    }


    @Override
    public Object from(TreeNode node, DataContext context) {
        return node.asSequence().stream()
                .map(n -> elementType.from(n, context))
                .collect(Collectors.toList()); // TODO handle arrays, set, etc
    }

    @Override
    public TreeNode export(Object instance, DataContext context) {
        // Java Arrays aren't Tterables :'(
        final Sequence sequence = new Sequence();
        Iterator it = values(instance);
        while (it.hasNext()) {
            sequence.add(elementType.export(it.next(), context));
        }
        return sequence;
    }

    private Iterator values(Object instance) {
        if (instance instanceof Iterable) return ((Iterable) instance).iterator();
        return new ArrayIterator(instance);
    }

    @Override
    public void toString(StringBuilder b, Stack<Class<?>> modelTypes) {
        elementType.toString(b, modelTypes);
        b.append("[]");
    }
}
