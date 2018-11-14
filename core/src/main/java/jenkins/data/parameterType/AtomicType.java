package jenkins.data.parameterType;

import com.google.common.base.Defaults;
import com.google.common.base.Predicate;
import com.google.common.primitives.Primitives;
import hudson.util.Secret;
import jenkins.data.DataContext;
import jenkins.data.tree.Scalar;
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
public class AtomicType<T> extends ParameterType {

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
        return (Class<T>) getActualType();
    }


    @Override
    public Object from(Optional<TreeNode> treeNode, DataContext context) {
        return treeNode.map(t -> from(t, context))
                .orElse(Defaults.defaultValue(getType()));
    }

    @Override
    public Object from(TreeNode node, DataContext context) {
        return converter.convert(getType(), node.asScalar().getValue());
    }

    @Override
    public TreeNode export(Object instance, DataContext context) {
        if (instance == null) return null;
        if (instance instanceof Number) {
            return new Scalar((Number) instance);
        }
        if (instance instanceof Boolean) {
            return new Scalar((Boolean) instance);
        }
        if (instance instanceof Secret) {
            return new Scalar(((Secret) instance).getEncryptedValue());
        }
        if (getType().isEnum()) {
            return new Scalar((Enum) instance);
        }

        return new Scalar(String.valueOf(instance));
    }

    @Override
    public void toString(StringBuilder b, Stack<Class<?>> modelTypes) {
        b.append(Primitives.unwrap(getType()).getSimpleName());
    }
}
