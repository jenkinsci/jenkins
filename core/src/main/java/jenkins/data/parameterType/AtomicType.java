package jenkins.data.parameterType;

import com.google.common.base.Defaults;
import com.google.common.primitives.Primitives;
import jenkins.data.DataContext;
import jenkins.data.tree.TreeNode;
import org.apache.commons.beanutils.Converter;
import org.kohsuke.stapler.Stapler;

import java.util.Optional;
import java.util.Stack;

/**
 * {@link ParameterType} for data that can be represented by a single value.

 * @author Jesse Glick
 * @author Anderw Bayer
 */
public class AtomicType<T> extends ParameterType<Class<T>> {

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
    public Object from(Optional<TreeNode> treeNode, DataContext context) {
        return treeNode.map(t -> from(t, context))
                .orElse(Defaults.defaultValue(getActualType()));
    }

    @Override
    public Object from(TreeNode node, DataContext context) {
        return converter.convert(getActualType(), node.asScalar().getValue());
    }

    @Override
    public void toString(StringBuilder b, Stack<Class<?>> modelTypes) {
        b.append(Primitives.unwrap((Class<?>) getActualType()).getSimpleName());
    }
}
