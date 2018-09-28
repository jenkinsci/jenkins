package jenkins.data;

import hudson.model.Descriptor;
import hudson.model.Result;
import jenkins.data.tree.TreeNode;
import jenkins.data.tree.Scalar;
import jenkins.data.tree.Sequence;
import org.jvnet.tiger_types.Types;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.NoStaplerConstructorException;

import javax.annotation.CheckForNull;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Stack;
import java.util.logging.Level;
import java.util.logging.Logger;

import static jenkins.data.ReflectiveDataModel.*;

/**
 * {@link DataModelParameter} implementation for models that defines itself via Stapler form binding
 * like {@link DataBoundSetter} and {@link DataBoundConstructor}.
 *
 * @author Kohsuke Kawaguchi
 */
class ReflectiveDataModelParameter extends AbstractDataModelParameter {
    private final ReflectiveDataModel<?> parent;

    /**
     * If this property is optional, the {@link Setter} that abstracts away how to set
     * the value to this property. Otherwise this parameter must be injected via the constructor.
     */
    /*package*/ final Setter setter;

    /*package*/ ReflectiveDataModelParameter(ReflectiveDataModel<?> parent, Type type, String name, Setter setter) {
        super(name,type);
        this.parent = parent;
        this.setter = setter;
    }

    /**
     * True if this parameter is required.
     *
     * <p>
     * A parameter set via {@link DataBoundSetter} is considered optional.
     * Right now, all the parameters set via {@link DataBoundConstructor} is
     * considered mandatory, but this might change in the future.
     */
    public boolean isRequired() {
        return setter==null;
    }

    /**
     * True if this parameter is deprecated.
     *
     * <p>
     * A parameter is deprecated if the corresponding {@link DataBoundSetter} marked as {@link Deprecated}.
     */
    public boolean isDeprecated() {
        return setter != null && setter.isDeprecated();
    }

    /**
     * Loads help defined for this parameter.
     *
     * @return some HTML (in English locale), if available, else null
     * @see Descriptor#doHelp
     */
    public @CheckForNull
    String getHelp() throws IOException {
        return parent.getHelp("help-" + name + ".html");
    }

    void toString(StringBuilder b, Stack<Class<?>> modelTypes) {
        b.append(name);
        if (!isRequired()) {
            b.append('?');
        }
        if (isDeprecated()) {
            b.append("(deprecated)");
        }
        b.append(": ");
        getType().toString(b, modelTypes);
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();
        toString(b, new Stack<>());
        return b.toString();
    }

    /**
     * Given an configured instance, try to infer the current value of the property.
     */
    /*package*/ TreeNode inspect(Object o, DataContext context) {
        return uncoerce(getValue(o), rawType, context);
    }

    private Object getValue(Object o) {
        Class<?> ownerClass = parent.getType();
        try {
            try {
                return ownerClass.getField(name).get(o);
            } catch (NoSuchFieldException x) {
                // OK, check for getter instead
            }
            try {
                return ownerClass.getMethod("get" + getCapitalizedName()).invoke(o);
            } catch (NoSuchMethodException x) {
                // one more check
            }
            try {
                return ownerClass.getMethod("is" + getCapitalizedName()).invoke(o);
            } catch (NoSuchMethodException x) {
                throw new UnsupportedOperationException("no public field ‘" + name + "’ (or getter method) found in " + ownerClass);
            }
        } catch (UnsupportedOperationException x) {
            throw x;
        } catch (Exception x) {
            throw new UnsupportedOperationException(x);
        }
    }


    /**
     * @param type
     *      Expected type statically inferred from signature. Where heterogenous typing is possible,
     *      the returned tree representation must include type annotation.
     */
    private TreeNode uncoerce(Object o, Type type, DataContext context) {
        if (o==null)
            return null;
        if (o instanceof Enum) {
            return new Scalar((Enum) o);
        } else if (o instanceof Result) {
            return new Scalar(o.toString());
        } else if (o instanceof Object[]) {
            Object[] array = (Object[]) o;
            Sequence list = new Sequence(array.length);
            Class<?> ct = array.getClass().getComponentType();
            for (Object elt : array) {
                list.add(uncoerce(elt, ct, context));
            }
            return list;
        } else if (o instanceof Collection) {
            Type ct = Types.getTypeArgument(Types.getBaseClass(type, Collection.class), 0, Object.class);
            Sequence list = new Sequence(((Collection) o).size());
            for (Object elt : (Collection<?>) o) {
                list.add(uncoerce(elt, ct, context));
            }
            return list;
        } else if (!o.getClass().getName().startsWith("java.")) {
            try {
                // Check to see if this can be treated as a data-bound struct.
                DataModel<Object> model = DataModelRegistry.get().lookup(o.getClass());
                if (model!=null) {
                    TreeNode nested = model.write(o, context);
                    if (nested.getType()!= TreeNode.Type.MAPPING) {
                        // can't add the type name. fall through
                    } else {
                        if (type != o.getClass()) {
                            int simpleNameCount = 0;
                            for (Class<?> c : context.findSubtypes(Types.erasure(type))) {
                                if (c.getSimpleName().equals(o.getClass().getSimpleName())) {
                                    simpleNameCount++;
                                }
                            }
// TODO: klass - do we continue to need this?
//                            if (simpleNameCount > 1) {
//                                nested.setKlass(o.getClass().getName());
//                            } else {
//                                nested.setKlass(o.getClass().getSimpleName());
//                            }
                        }
                        // TODO: how do we insert the symbol?
                        // nested.setSymbol(symbolOf(o));
                    }
                    return nested;
                }
            } catch (UnsupportedOperationException x) {
                // then leave it raw
                if (!(x.getCause() instanceof NoStaplerConstructorException)) {
                    LOGGER.log(Level.WARNING, "failed to uncoerce " + o, x);
                }
            } catch (NoStaplerConstructorException x) {
                // leave it raw
            }
        }
        return new Scalar(o.toString());
    }

    private static final Logger LOGGER = Logger.getLogger(ReflectiveDataModelParameter.class.getName());
}
