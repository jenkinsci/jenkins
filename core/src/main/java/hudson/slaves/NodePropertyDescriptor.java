package hudson.slaves;

import hudson.model.Descriptor;
import hudson.model.Node;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import org.jvnet.tiger_types.Types;

public abstract class NodePropertyDescriptor extends Descriptor<NodeProperty<?>> {

	protected NodePropertyDescriptor() {}
	
    /**
     * Returns true if this {@link NodeProperty} type is applicable to the
     * given job type.
     * 
     * <p>
     * The default implementation of this method checks if the given node type is assignable to 'N' of
     * {@link NodeProperty}<tt>&lt;N></tt>, but subtypes can extend this to change this behavior.
     *
     * @return
     *      true to indicate applicable, in which case the property will be
     *      displayed in the configuration screen of this node.
     */
    public boolean isApplicable(Class<? extends Node> nodeType) {
        Type parameterization = Types.getBaseClass(clazz, NodeProperty.class);
        if (parameterization instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) parameterization;
            Class<?> applicable = Types.erasure(Types.getTypeArgument(pt, 0));
            return applicable.isAssignableFrom(nodeType);
        } else {
            throw new AssertionError(clazz+" doesn't properly parameterize NodeProperty. The isApplicable() method must be overriden.");
        }
    }

}
