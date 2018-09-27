package jenkins.data;

import hudson.model.Descriptor;
import hudson.model.Result;
import org.jvnet.tiger_types.Types;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.NoStaplerConstructorException;

import javax.annotation.CheckForNull;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Stack;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A property of {@link ModelBinder}
 *
 * @author Kohsuke Kawaguchi
 * @see ModelBinder#getParameter(String)
 */
public final class DescribableParameter {
    private final ModelBinder<?> parent;
    private ParameterType type;
    private final String name;

    /**
     * Type of the value that {@link Describable} expects in its setter/field.
     */
    private final Type rawType;

    /**
     * If this property is optional, the {@link Setter} that abstracts away how to set
     * the value to this property. Otherwise this parameter must be injected via the constructor.
     */
    /*package*/ final Setter setter;

    /*package*/ DescribableParameter(ModelBinder<?> parent, Type type, String name, Setter setter) {
        this.parent = parent;
        this.rawType = type;
        this.name = name;
        this.setter = setter;
    }

    /**
     * Classification of the type of this parameter.
     * <p>
     * Originates from the pipeline plugin and I'm not sure the logic behind this.
     */
    public ParameterType getType() {
        if (type==null)
            type = ParameterType.of(rawType);
        return type;
    }

    /**
     * The type of this parameter, possibly with generics.
     */
    public Type getRawType() {
        return rawType;
    }

    /**
     * Gets the erasure of {@link #getRawType()}
     */
    public Class getErasedType() {
        return Types.erasure(rawType);
    }

    public String getName() {
        return name;
    }

    public String getCapitalizedName() {
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
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
        toString(b, new Stack<Class<?>>());
        return b.toString();
    }

    /**
     * Given an configured instance, try to infer the current value of the property.
     */
    /*package*/ Object inspect(Object o) {
        return uncoerce(getValue(o), rawType);
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


    private Object uncoerce(Object o, Type type) {
        if (type instanceof Class && ((Class) type).isEnum() && o instanceof Enum) {
            return ((Enum) o).name();
        } else if (type == URL.class && o instanceof URL) {
            return o.toString();
        } else if (type == Result.class && o instanceof Result) {
            return o.toString();
        } else if ((type == Character.class || type == char.class) && o instanceof Character) {
            return o.toString();
        } else if (o instanceof Object[]) {
            Object[] array = (Object[]) o;
            List<Object> list = new ArrayList<Object>(array.length);
            for (Object elt : array) {
                list.add(uncoerce(elt, array.getClass().getComponentType()));
            }
            return list;
        } else if (o instanceof Collection && Types.isSubClassOf(type, Collection.class)) {
            List<Object> list = new ArrayList<Object>(((Collection) o).size());
            for (Object elt : (Collection<?>) o) {
                list.add(uncoerce(elt, Types.getTypeArgument(Types.getBaseClass(type,Collection.class),0,Object.class)));
            }
            return list;
        } else if (o != null && !o.getClass().getName().startsWith("java.")) {
            try {
                // Check to see if this can be treated as a data-bound struct.
                UninstantiatedDescribable nested = ModelBinder.uninstantiate2_(o);
                if (type != o.getClass()) {
                    int simpleNameCount = 0;
                    for (Class<?> c : findSubtypes(Types.erasure(type))) {
                        if (c.getSimpleName().equals(o.getClass().getSimpleName())) {
                            simpleNameCount++;
                        }
                    }
                    if (simpleNameCount > 1) {
                        nested.setKlass(o.getClass().getName());
                    } else {
                        nested.setKlass(o.getClass().getSimpleName());
                    }
                }
                nested.setSymbol(symbolOf(o));
                return nested;
            } catch (UnsupportedOperationException x) {
                // then leave it raw
                if (!(x.getCause() instanceof NoStaplerConstructorException)) {
                    LOGGER.log(Level.WARNING, "failed to uncoerce " + o, x);
                }
            } catch (NoStaplerConstructorException x) {
                // leave it raw
            }
        }
        return o;
    }

    private static final Logger LOGGER = Logger.getLogger(DescribableParameter.class.getName());
}
