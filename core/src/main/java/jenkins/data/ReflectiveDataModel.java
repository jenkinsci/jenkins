package jenkins.data;

import com.google.common.base.Defaults;
import hudson.ExtensionList;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.ParametersDefinitionProperty;
import jenkins.data.tree.Mapping;
import jenkins.data.tree.Sequence;
import jenkins.data.tree.TreeNode;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.ObjectUtils;
import org.kohsuke.stapler.ClassDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.lang.Klass;

import javax.annotation.CheckForNull;
import java.beans.Introspector;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link DataModel} implementation for models that defines itself via Stapler form binding
 * like {@link DataBoundSetter} and {@link DataBoundConstructor}.
 */
class ReflectiveDataModel<T> extends DataModel<T> {

    /**
     * Type that this model represents.
     */
    private final Class<T> type;

    private Map<String,ReflectiveDataModelParameter> parameters = new LinkedHashMap<>(4);

    /**
     * Read only view to {@link #parameters}
     */
    private Map<String,ReflectiveDataModelParameter> parametersView;

    /**
     * Data-bound constructor.
     */
    private final Constructor<T> constructor;

    /**
     * Name of the parameters of the {@link #constructor}
     */
    private final String[] constructorParamNames;

    /**
     * Loads a definition of the structure of a class.
     */
    public ReflectiveDataModel(Class<T> clazz) {
        this.type = clazz;

        if (type == ParametersDefinitionProperty.class) { // TODO pending core fix
            constructorParamNames = new String[] {"parameterDefinitions"};
        } else {
            constructorParamNames = new ClassDescriptor(type).loadConstructorParamNames();
        }

        constructor = findConstructor(constructorParamNames.length);

        Type[] types = constructor.getGenericParameterTypes();
        for (int i = 0; i < constructorParamNames.length; i++) {
            addParameter(parameters, types[i], constructorParamNames[i], null);
        }

        // rest of the properties will be sorted alphabetically
        Map<String,ReflectiveDataModelParameter> rest = new TreeMap<>();

        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.isAnnotationPresent(DataBoundSetter.class)) {
                    addParameter(rest, f.getGenericType(), f.getName(), Setter.create(f));
                }
            }
            for (Method m : c.getDeclaredMethods()) {
                if (m.isAnnotationPresent(DataBoundSetter.class)) {
                    Type[] parameterTypes = m.getGenericParameterTypes();
                    if (!m.getName().startsWith("set") || parameterTypes.length != 1) {
                        throw new IllegalStateException(m + " cannot be a @DataBoundSetter");
                    }
                    addParameter(rest, m.getGenericParameterTypes()[0],
                            Introspector.decapitalize(m.getName().substring(3)), Setter.create(m));
                }
            }
        }
        parameters.putAll(rest);
        parametersView = Collections.unmodifiableMap(parameters);
    }

    private void addParameter(Map<String,ReflectiveDataModelParameter> props, Type type, String name, Setter setter) {
        props.put(name, new ReflectiveDataModelParameter(this, type, name, setter));
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
    public Collection<ReflectiveDataModelParameter> getParameters() {
        return parametersView.values();
    }

    public DataModelParameter getParameter(String name) {
        return parameters.get(name);
    }

    /**
     * Returns true if this model has one and only one required parameter.
     *
     * @see #ANONYMOUS_KEY
     */
    public boolean hasSingleRequiredParameter() {
        return getSoleRequiredParameter()!=null;
    }

    /**
     * If this model has one and only one required parameter, return it.
     * Otherwise null.
     *
     * @see #ANONYMOUS_KEY
     */
    public @CheckForNull
    DataModelParameter getSoleRequiredParameter() {
        return getParameters().stream()
            .filter(ReflectiveDataModelParameter::isRequired)
            .reduce((some, other) -> null) // if there's more than one, return null;
            .orElse(null);
    }

    /**
     * If this model has any required parameter, return the first one.
     * Otherwise null.
     */
    public @CheckForNull DataModelParameter getFirstRequiredParameter() {
        return getParameters().stream()
            .filter(ReflectiveDataModelParameter::isRequired)
            .findAny()
            .orElse(null);
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
        Mapping mapping = input.asMapping();
        if (mapping.containsKey(ANONYMOUS_KEY)) {
            if (mapping.size()!=1)
                throw new IllegalArgumentException("All arguments have to be named but it has "+ANONYMOUS_KEY);

            DataModelParameter rp = getSoleRequiredParameter();
            if (rp==null)
                throw new IllegalArgumentException("Arguments to "+type+" have to be explicitly named");
            mapping = new Mapping();
            mapping.put(rp.getName(),mapping.get(ANONYMOUS_KEY));
        }

        try {
            final int count = constructor.getParameterCount();
            Object[] args = new Object[count];
            final Type[] types = constructor.getGenericParameterTypes();

            for (int i = 0; i < count; i++) {
                final String name = constructorParamNames[i];
                args[i] = getParameter(name).getType().from(mapping.getValue(name), context);
            }
            T o = constructor.newInstance(args);

            // Object[] args = buildArguments(input, constructor.getGenericParameterTypes(), constructorParamNames, true, context);
            // T o = constructor.newInstance(args);
            injectSetters(o, mapping, context);
            return o;
        } catch (Exception x) {
            throw new IllegalArgumentException("Could not instantiate " + input + " for " + this + ": " + x, x);
        }
    }


        // adapted from RequestImpl
    @SuppressWarnings("unchecked")
    private Constructor<T> findConstructor(int length) {
        try { // may work without this, but only if the JVM happens to return the right overload first
            if (type == ParametersDefinitionProperty.class && length == 1) { // TODO pending core fix
                return (Constructor<T>) ParametersDefinitionProperty.class.getConstructor(List.class);
            }
        } catch (NoSuchMethodException x) {
            throw new AssertionError(x);
        }
        Constructor<T>[] ctrs = (Constructor<T>[]) type.getConstructors();
        for (Constructor<T> c : ctrs) {
            if (c.getAnnotation(DataBoundConstructor.class) != null) {
                if (c.getParameterTypes().length != length) {
                    throw new IllegalArgumentException(c + " has @DataBoundConstructor but it doesn't match with your .stapler file. Try clean rebuild");
                }
                return c;
            }
        }
        for (Constructor<T> c : ctrs) {
            if (c.getParameterTypes().length == length) {
                return c;
            }
        }
        throw new IllegalArgumentException(type + " does not have a constructor with " + length + " arguments");
    }

    /**
     * Injects via {@link DataBoundSetter}
     */
    private void injectSetters(Object o, Mapping arguments, DataContext context) throws Exception {

        for (ReflectiveDataModelParameter p : parameters.values()) {
            if (p.setter!=null) {
                final String name = p.getName();
                if (arguments.containsKey(name)) {
                    Object value = getParameter(name).getType().from(arguments.getValue(name), context);
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
        for (ReflectiveDataModelParameter p : parameters.values()) {
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
            for (ReflectiveDataModelParameter p : parameters.values()) {
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
                for (ReflectiveDataModelParameter p : parameters.values()) {
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



    /**
     * As a short-hand, if a {@link DataModel} has only one required parameter,
     * {@link DataModel#read(TreeNode, DataContext)} accepts a single-item map whose key is this magic token.
     *
     * <p>
     * To avoid clients from needing to special-case this key, {@link #write(Object, DataContext)} does not
     * produce a tree that contains this magic token. Clients who want
     * to take advantages of this should look at {@link ReflectiveDataModel#hasSingleRequiredParameter()}
     */
    // TODO: which layer does this belong?
    public static final String ANONYMOUS_KEY = "<anonymous>";
}
