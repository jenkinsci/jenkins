package jenkins.data.parameterType;

import jenkins.data.DataContext;
import jenkins.data.DataModel;
import jenkins.data.tree.TreeNode;

import java.lang.reflect.Type;
import java.util.Stack;

/**
 * {@link ParameterType} for data that are themselves composed of sub-elements, by opposition to simpler
 * {@link AtomicType}s.
 *
 * @author Jesse Glick
 * @author Anderw Bayer
 */
public final class ComposedObjectType<T> extends ParameterType<Class<T>> {
    private final DataModel<T> model;

    ComposedObjectType(DataModel<T> model) {
        super(model.getType());
        this.model = model;
    }

    public Class<T> getType() {
        return getActualType();
    }

    @Override
    public Object from(TreeNode node, DataContext context) {
        return model.read(node, context);
    }

    /**
     * The actual class underlying the type.
     */
    @Override
    public void toString(StringBuilder b, Stack<Class<?>> modelTypes) {
        model.toString(b, modelTypes);
    }
}
