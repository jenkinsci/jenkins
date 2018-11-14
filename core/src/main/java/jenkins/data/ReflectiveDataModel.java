package jenkins.data;

import hudson.ExtensionList;
import hudson.model.Describable;
import hudson.model.Descriptor;
import jenkins.data.tree.Mapping;
import jenkins.data.tree.Sequence;
import jenkins.data.tree.TreeNode;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.ObjectUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.lang.Klass;

import javax.annotation.CheckForNull;
import java.beans.Introspector;
import java.io.IOException;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * {@link DataModel} implementation for models that defines itself via Stapler form binding
 * like {@link DataBoundSetter} and {@link DataBoundConstructor}.
 */
abstract class ReflectiveDataModel<T> extends DataModel<T> {

    /**
     * Type that this model represents.
     */
    protected final Class<T> type;

    protected List<ReflectiveDataModelParameter> parameters;

    /**
     * Loads a definition of the structure of a class.
     */
    public ReflectiveDataModel(Class<T> clazz) {
        this.type = clazz;
        this.parameters = new ArrayList<>();
    }

    /**
     * A concrete class, usually {@link Describable}.
     */
    public Class<T> getType() {
        return type;
    }

    /**
     * A map from parameter names to types.
     * A parameter name is either the name of an argument to a {@link DataBoundConstructor},
     * or the JavaBeans property name corresponding to a {@link DataBoundSetter}.
     *
     * <p>
     * Sorted by the mandatory parameters first (in the order they are specified in the code),
     * followed by optional arguments.
     */
    // FIXME where|why do we need this ?
    public Iterable<ReflectiveDataModelParameter> getParameters() {
        return Collections.unmodifiableCollection(parameters);
    }

    public DataModelParameter getParameter(String name) {
        return  parameters.stream()
                .filter(p -> name.equals(p.getName()))
                .findAny()
                .orElseThrow(() -> new IllegalArgumentException(type + " as no parameter named " + name));
    }

    /**
     * Corresponds to {@link Descriptor#getDisplayName} where available.
     */
    public String getDisplayName() {
        for (Descriptor<?> d : ExtensionList.lookup(Descriptor.class)) {
            if (d.clazz == type) {
                return d.getDisplayName();
            }
        }
        return type.getSimpleName();
    }

    /**
     * Creates an instance of a class via {@link DataBoundConstructor} and {@link DataBoundSetter}.
     * <p>The arguments may be primitives (as wrappers) or {@link String}s if that is their declared type.
     * Stapler-convertible types may be represented by {@link String}s as well.
     * Other object types may be passed using a nested JSON-like structure.
     * Specifically a {@link Sequence} may be used to represent any list- or array-valued argument.
     * A {@link Mapping} may be used to represent any class which is itself data-bound.
     * implementations.
     */
    @Override
    public T read(TreeNode input, DataContext context) {
        final Mapping mapping = input.asMapping();

        T o = getInstance(mapping, context);

        try {
            injectSetters(o, mapping, context);
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not configure " + input + " for " + this, e);
        }
        return o;
    }

    protected abstract T getInstance(Mapping input, DataContext context);

    /**
     * Injects via {@link DataBoundSetter}
     */
    private void injectSetters(Object o, Mapping arguments, DataContext context) throws Exception {

        for (ReflectiveDataModelParameter p : parameters) {
            if (p.setter!=null) {
                final String name = p.getName();
                if (arguments.containsKey(name)) {
                    Object value = p.getType().from(arguments.getValue(name), context);
                    p.setter.set(o, value);
                }
            }
        }
    }

    /**
    /**
     * True if this model's type is deprecated.
     *
     * A model is deprecated if it's {@link #getType() type} is marked as {@link Deprecated}.
     */
    public boolean isDeprecated() {
        return type.getAnnotation(Deprecated.class) != null;
    }


    @Override
    public Mapping write(T o, DataContext context) {
        if (o==null)
            throw new IllegalArgumentException("Expected "+type+" but got null");
        if (!type.isInstance(o))
            throw new IllegalArgumentException("Expected "+type+" but got an instance of "+o.getClass());

        Mapping r = new Mapping();
        Mapping constructorOnlyDataBoundProps = new Mapping();
        Mapping nonDeprecatedDataBoundProps = new Mapping();
        for (ReflectiveDataModelParameter p : parameters) {
            TreeNode v = p.inspect(o,context);
            if (p.isRequired() && v==null) {
                // instantiate() method treats missing properties as nulls, so we don't need to keep it
                // but if it's for the setter, explicit null invocation is needed, so we need to keep it
                continue;
            }
            r.put(p.getName(), v);
            if (p.isRequired()) {
                constructorOnlyDataBoundProps.put(p.getName(),v);
            }
            if (!p.isDeprecated()) {
                nonDeprecatedDataBoundProps.put(p.getName(),v);
            }
        }

        Object control = null;
        try {
            control = read(constructorOnlyDataBoundProps,context);
        } catch (Exception x) {
            LOGGER.log(Level.WARNING, "Cannot create control version of " + type + " using " + constructorOnlyDataBoundProps, x);
        }

        if (control!=null) {
            for (ReflectiveDataModelParameter p : parameters) {
                if (p.isRequired())
                    continue;

                TreeNode v = p.inspect(control,context);

                // if the control has the same value as our object, we won't need to keep it
                if (ObjectUtils.equals(v, r.get(p.getName()))) {
                    r.remove(p.getName());
                    nonDeprecatedDataBoundProps.remove(p.getName());
                }
            }
        }

        if (!nonDeprecatedDataBoundProps.keySet().equals(r.keySet())) {
            // we have some deprecated properties
            control = null;
            try {
                control = read(nonDeprecatedDataBoundProps,context);
            } catch (Exception x) {
                LOGGER.log(Level.WARNING,
                        "Cannot create control version of " + type + " using " + nonDeprecatedDataBoundProps, x);
            }

            if (control != null) {
                for (ReflectiveDataModelParameter p : parameters) {
                    if (!p.isDeprecated())
                        continue;

                    TreeNode v = p.inspect(control,context);

                    // if the control has the same value as our object, we won't need to keep it
                    if (ObjectUtils.equals(v, r.get(p.getName()))) {
                        r.remove(p.getName());
                    }
                }
            }
        }

        return r;
    }

    /**
     * Loads help defined for this object as a whole
     *
     * @return some HTML (in English locale), if available, else null
     * @see Descriptor#doHelp
     */
    public @CheckForNull String getHelp() throws IOException {
        return getHelp("help.html");
    }

    /*package*/ @CheckForNull
    String getHelp(String name) throws IOException {
        for (Klass<?> c = Klass.java(type); c != null; c = c.getSuperClass()) {
            URL u = c.getResource(name);
            if (u != null) {
                return IOUtils.toString(u, "UTF-8");
            }
        }
        return null;
    }


    private static final Logger LOGGER = Logger.getLogger(ReflectiveDataModel.class.getName());

}
