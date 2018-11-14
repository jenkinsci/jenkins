package jenkins.data.parameterType;

import jenkins.data.DataContext;
import jenkins.data.tree.TreeNode;

import java.lang.reflect.Type;
import java.util.Optional;
import java.util.Stack;
import java.util.stream.Collectors;

/**
 * @author Jesse Glick
 * @author Anderw Bayer
 */
public final class ArrayType<T extends Type> extends ParameterType<T> {
    private final ParameterType elementType;

    ArrayType(T actualClass, ParameterType elementType) {
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
    public void toString(StringBuilder b, Stack<Class<?>> modelTypes) {
        elementType.toString(b, modelTypes);
        b.append("[]");
    }
}
