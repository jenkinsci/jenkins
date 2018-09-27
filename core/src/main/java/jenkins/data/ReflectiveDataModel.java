package jenkins.data;

import com.google.common.primitives.Primitives;
import groovy.lang.GString;
import hudson.ExtensionList;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Result;
import jenkins.data.model.CNode;
import jenkins.model.Jenkins;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.ClassUtils;
import org.codehaus.groovy.reflection.ReflectionCache;
import org.jenkinsci.Symbol;
import org.jvnet.tiger_types.Types;
import org.kohsuke.stapler.ClassDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.lang.Klass;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.beans.Introspector;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * {@link DataModel} implementation for models that defines itself via Stapler form binding
 * like {@link DataBoundSetter} and {@link DataBoundConstructor}.
 */
class ReflectiveDataModel<T> extends DataModel<T> implements Serializable {

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

    /** Binds type parameter, preferred means of obtaining a DataModel. */
    public static <T> DataModel<T> of(Class<T> clazz) {
        ReflectiveDataModel mod = modelCache.get(clazz.getName());
        if (mod != null && mod.type == clazz) {
            return mod;
        }
        mod = new ReflectiveDataModel<>(clazz);
        modelCache.put(clazz.getName(), mod);
        return mod;
    }

    /** Map class name to cached model. */
    static ConcurrentHashMap<String, ReflectiveDataModel> modelCache = new ConcurrentHashMap<>();

    /**
     * Loads a definition of the structure of a class: what kind of data
     * you might get back from {@link #uninstantiate} on an instance,
     * or might want to pass to {@link #instantiate(Map)}.
     *
     * Use {@link #of(Class)} instead -- that will returned cached instances.
     */
    public ReflectiveDataModel(Class<T> clazz) {
        this.type = clazz;

        ReflectiveDataModel mod = modelCache.get(clazz.getName());
        if (mod != null && mod.type == clazz) {
            constructor = mod.constructor;
            parameters = mod.parameters;
            constructorParamNames = mod.constructorParamNames;
            parametersView = mod.parametersView;
            return;
        }

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
        modelCache.putIfAbsent(clazz.getName(), this);
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
        DataModelParameter rp = null;
        for (DataModelParameter p : getParameters()) {
            if (p.isRequired()) {
                if (rp!=null)   return null;
                rp = p;
            }
        }
        return rp;
    }

    /**
     * If this model has any required parameter, return the first one.
     * Otherwise null.
     */
    public @CheckForNull DataModelParameter getFirstRequiredParameter() {
        for (DataModelParameter p : getParameters()) {
            if (p.isRequired()) {
                return p;
            }
        }
        return null;
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
     * {@link Character}s, {@link Enum}s, and {@link URL}s may be represented by {@link String}s.
     * Other object types may be passed in “raw” as well, but JSON-like structures are encouraged instead.
     * Specifically a {@link List} may be used to represent any list- or array-valued argument.
     * A {@link Map} with {@link String} keys may be used to represent any class which is itself data-bound.
     * In that case the special key {@link #CLAZZ} is used to specify the {@link Class#getName};
     * or it may be omitted if the argument is declared to take a concrete type;
     * or {@link Class#getSimpleName} may be used in case the argument type is {@link Describable}
     * and only one subtype is registered (as a {@link Descriptor}) with that simple name.
     */
    public T instantiate(Map<String,?> arguments, ReadDataContext context) throws Exception {
        if (arguments.containsKey(ANONYMOUS_KEY)) {
            if (arguments.size()!=1)
                throw new IllegalArgumentException("All arguments have to be named but it has "+ANONYMOUS_KEY);

            DataModelParameter rp = getSoleRequiredParameter();
            if (rp==null)
                throw new IllegalArgumentException("Arguments to "+type+" have to be explicitly named");
            arguments = Collections.singletonMap(rp.getName(),arguments.get(ANONYMOUS_KEY));
        }

        try {
            Object[] args = buildArguments(arguments, constructor.getGenericParameterTypes(), constructorParamNames, true, context);
            T o = constructor.newInstance(args);
            injectSetters(o, arguments, context);
            return o;
        } catch (Exception x) {
            throw new IllegalArgumentException("Could not instantiate " + arguments + " for " + this + ": " + x, x);
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
     * Give a method/constructor, take values specified in the bag and build up the arguments to invoke it with.
     *
     * @param types
     *      Types of the parameters
     * @param names
     *      Names of the parameters
     * @param callEvenIfNoArgs
     *      true for constructor, false for a method call
     * @return
     *      null if the method shouldn't be invoked at all. IOW, there's nothing in the bag.
     */
    private Object[] buildArguments(Map<String,?> bag, Type[] types, String[] names, boolean callEvenIfNoArgs, ReadDataContext context) throws Exception {
        assert names.length==types.length;

        Object[] args = new Object[names.length];
        boolean hasArg = callEvenIfNoArgs;
        for (int i = 0; i < args.length; i++) {
            String name = names[i];
            hasArg |= bag.containsKey(name);
            Object a = bag.get(name);
            Type type = types[i];
            if (a != null) {
                args[i] = coerce(this.type.getName() + "." + name, type, a, context);
            } else if (type instanceof Class && ((Class) type).isPrimitive()) {
                args[i] = getVmDefaultValueForPrimitiveType((Class)type);
                if (args[i]==null && callEvenIfNoArgs)
                    throw new UnsupportedOperationException("not yet handling @DataBoundConstructor default value of " + type + "; pass an explicit value for " + name);
            } else {
                // TODO this might be fine (ExecutorStep.label), or not (GenericSCMStep.scm); should inspect parameter annotations for @Nonnull and throw an UOE if found
            }
        }
        return hasArg ? args : null;
    }

    /**
     * Injects via {@link DataBoundSetter}
     */
    private void injectSetters(Object o, Map<String,?> arguments, ReadDataContext context) throws Exception {
        for (ReflectiveDataModelParameter p : parameters.values()) {
            if (p.setter!=null) {
                if (arguments.containsKey(p.getName())) {
                    Object v = arguments.get(p.getName());
                    p.setter.set(o, coerce(p.setter.getDisplayName(), p.getRawType(), v, context));
                }
            }
        }
    }

    /**
     * Take an object of random type and tries to convert it into another type
     *
     * @param location
     *      Human readable location of coercion when reporting a problem.
     * @param type
     *      The type to convert the object to.
     * @param o
     *      Source object to be converted.
     */
    @SuppressWarnings("unchecked")
    private Object coerce(String location, Type type, Object o, ReadDataContext context) throws Exception {
        Class erased = Types.erasure(type);

        if (type instanceof Class) {
            o = ReflectionCache.getCachedClass(erased).coerceArgument(o);
        }
        if (o instanceof GString) {
            o = o.toString();
        }
        if (o instanceof List && Collection.class.isAssignableFrom(erased)) {
            return coerceList(location,
                    Types.getTypeArgument(Types.getBaseClass(type, Collection.class), 0, Object.class), (List) o, context);
        } else if (Primitives.wrap(erased).isInstance(o)) {
            return o;
        } else if (o==null) {
            return null;
        } else if (o instanceof CNode) {
            return context.lookup(erased).read((CNode)o, context);
        } else if (o instanceof Map) {
            Map<String,Object> m = new HashMap<>();
            for (Map.Entry<?,?> entry : ((Map<?,?>) o).entrySet()) {
                m.put((String) entry.getKey(), entry.getValue());
            }

            Class<?> clazz = resolveClass(erased, (String) m.remove(CLAZZ), null);
            return context.lookup(clazz).instantiate(m, context);
        } else if (o instanceof String && erased.isEnum()) {
            return Enum.valueOf(erased.asSubclass(Enum.class), (String) o);
        } else if (o instanceof String && erased == URL.class) {
            return new URL((String) o);
        } else if (o instanceof String && erased == Result.class) {
            return Result.fromString((String)o);
        } else if (o instanceof String && (erased == char.class || erased == Character.class) && ((String) o).length() == 1) {
            return ((String) o).charAt(0);
        } else if (o instanceof String && ClassUtils.isAssignable(ClassUtils.primitiveToWrapper(erased), Number.class)) {
            return coerceStringToNumber(location, ClassUtils.primitiveToWrapper(erased), (String)o);
        } else if (o instanceof String && (erased == boolean.class || erased == Boolean.class)) {
            return Boolean.valueOf((String)o);
        } else if (o instanceof List && erased.isArray()) {
            Class<?> componentType = erased.getComponentType();
            List<Object> list = coerceList(location, componentType, (List) o, context);
            return list.toArray((Object[]) Array.newInstance(componentType, list.size()));
        } else {
            throw new ClassCastException(location + " expects " + type + " but received " + o.getClass());
        }
    }

    private Object coerceStringToNumber(@Nonnull String context, @Nonnull Class numberClass, @Nonnull String o)
            throws ClassCastException {
        try {
            if (numberClass.equals(Integer.class)) {
                return Integer.valueOf(o);
            } else if (numberClass.equals(Float.class)) {
                return Float.valueOf(o);
            } else if (numberClass.equals(Double.class)) {
                return Double.valueOf(o);
            } else if (numberClass.equals(Long.class)) {
                return Long.valueOf(o);
            } else if (numberClass.equals(Byte.class)) {
                return Byte.valueOf(o);
            } else if (numberClass.equals(Short.class)) {
                return Short.valueOf(o);
            } else {
                // Fallback for any other Number - just return the original string.
                return o;
            }
        } catch (NumberFormatException nfe) {
            throw new ClassCastException(context + " expects " + numberClass + " but was unable to coerce the received value \"" + o + "\" to that type");
        }
    }

    /**
     * Resolves a class name to an actual {@link Class} object.
     *
     * @param symbol
     *      {@linkplain Symbol symbol name} of the class to resolve.
     * @param name
     *      Either a simple name or a fully qualified class name.
     * @param base
     *      Signature of the type that the resolved class should be assignable to.
     */
    /*package*/ static Class<?> resolveClass(Class<?> base, @Nullable String name, @Nullable String symbol) throws ClassNotFoundException {
        // TODO: if both name & symbol are present, should we verify its consistency?

        if (name != null) {
            if (name.contains(".")) {// a fully qualified name
                Jenkins j = Jenkins.getInstanceOrNull();
                ClassLoader loader = j != null ? j.getPluginManager().uberClassLoader : Thread.currentThread().getContextClassLoader();
                return Class.forName(name, true, loader);
            } else {
                Class<?> clazz = null;
                for (Class<?> c : findSubtypes(base)) {
                    if (c.getSimpleName().equals(name)) {
                        if (clazz != null) {
                            throw new UnsupportedOperationException(name + " as a " + base + " could mean either " + clazz.getName() + " or " + c.getName());
                        }
                        clazz = c;
                    }
                }
                if (clazz == null) {
                    throw new UnsupportedOperationException("no known implementation of " + base + " is named " + name);
                }
                return clazz;
            }
        }

        if (symbol != null) {
            // The normal case: the Descriptor is marked, but the name applies to its Describable.
            Descriptor d = SymbolLookup.get().findDescriptor(base, symbol);
            if (d != null) {
                return d.clazz;
            }
            if (base == ParameterValue.class) { // TODO JENKINS-26093 workaround
                d = SymbolLookup.get().findDescriptor(ParameterDefinition.class, symbol);
                if (d != null) {
                    Class<?> c = parameterValueClass(d.clazz);
                    if (c != null) {
                        return c;
                    }
                }
            }
            throw new UnsupportedOperationException("no known implementation of " + base + " is using symbol ‘" + symbol + "’");
        }

        if (Modifier.isAbstract(base.getModifiers())) {
            throw new UnsupportedOperationException("must specify " + CLAZZ + " with an implementation of " + base);
        }
        return base;
    }

    /**
     * Apply {@link #coerce(String, Type, Object, ReadDataContext)} method to a collection item.
     */
    private List<Object> coerceList(String location, Type type, List<?> list, ReadDataContext context) throws Exception {
        List<Object> r = new ArrayList<>();
        for (Object elt : list) {
            r.add(coerce(location, type, elt, context));
        }
        return r;
    }

    /** Tries to find the {@link ParameterValue} type corresponding to a {@link ParameterDefinition} by assuming conventional naming. */
    private static @CheckForNull Class<?> parameterValueClass(@Nonnull Class<?> parameterDefinitionClass) { // TODO JENKINS-26093
        String name = parameterDefinitionClass.getName();
        if (name.endsWith("Definition")) {
            try {
                Class<?> parameterValueClass = parameterDefinitionClass.getClassLoader().loadClass(name.replaceFirst("Definition$", "Value"));
                if (ParameterValue.class.isAssignableFrom(parameterValueClass)) {
                    return parameterValueClass;
                }
            } catch (ClassNotFoundException x) {
                // ignore
            }
        }
        return null;
    }

    static Set<Class<?>> findSubtypes(Class<?> supertype) {
        Set<Class<?>> clazzes = new HashSet<>();
        // Jenkins.getDescriptorList does not work well since it is limited to descriptors declaring one supertype, and does not work at all for SimpleBuildStep.
        for (Descriptor<?> d : ExtensionList.lookup(Descriptor.class)) {
            if (supertype.isAssignableFrom(d.clazz)) {
                clazzes.add(d.clazz);
            }
        }
        if (supertype == ParameterValue.class) { // TODO JENKINS-26093 hack, pending core change
            for (Class<?> d : findSubtypes(ParameterDefinition.class)) {
                Class<?> c = parameterValueClass(d);
                if (c != null) {
                    clazzes.add(c);
                }
            }
        }
        return clazzes;
    }

    /**
     * Finds a symbol for an instance if there's one, or return null.
     */
    /*package*/ static String symbolOf(Object o) {
        Set<String> symbols = SymbolLookup.getSymbolValue(o);
        return symbols.isEmpty() ? null : symbols.iterator().next();
    }

    /**
     * True if this model's type is deprecated.
     *
     * A model is deprecated if it's {@link #getType() type} is marked as {@link Deprecated}.
     */
    public boolean isDeprecated() {
        return type.getAnnotation(Deprecated.class) != null;
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

    void toString(StringBuilder b, Stack<Class<?>> modelTypes) {
        b.append(type.getSimpleName());
        if (modelTypes.contains(type)) {
            b.append('…');
        } else {
            modelTypes.push(type);
            try {
                b.append('(');
                boolean first = true;
                for (DataModelParameter dp : getParameters()) {
                    if (first) {
                        first = false;
                    } else {
                        b.append(", ");
                    }
                    dp.toString(b, modelTypes);
                }
                b.append(')');
            } finally {
                modelTypes.pop();
            }
        }
    }

    @Override public String toString() {
        StringBuilder b = new StringBuilder();
        toString(b, new Stack<>());
        return b.toString();
    }

    private Object writeReplace() {
        return new SerializedForm(type);
    }

    /**
     * Serialized form of {@link ReflectiveDataModel}, which is just its class as everything else
     * can be computed.
     */
    private static class SerializedForm implements Serializable {
        private final Class type;

        public SerializedForm(Class type) {
            this.type = type;
        }

        private Object readResolve() {
            return ReflectiveDataModel.of(type);
        }

        private static final long serialVersionUID = 1L;
    }

    public static final String CLAZZ = "$class";

    private static final Logger LOGGER = Logger.getLogger(ReflectiveDataModel.class.getName());

    private static final long serialVersionUID = 1L;

    // TODO: switch to use {@link hudson.util.ReflectionUtils}
    /**
     * Given the primitive type, returns the VM default value for that type in a boxed form.
     */
    private static Object getVmDefaultValueForPrimitiveType(Class<?> type) {
        return defaultPrimitiveValue.get(type);
    }

    private static final Map<Class,Object> defaultPrimitiveValue = new HashMap<>();
    static {
        defaultPrimitiveValue.put(boolean.class, false);
        defaultPrimitiveValue.put(byte.class, (byte) 0);
        defaultPrimitiveValue.put(short.class, (short) 0);
        defaultPrimitiveValue.put(int.class, 0);
        defaultPrimitiveValue.put(long.class, 0L);
        defaultPrimitiveValue.put(float.class, 0.0f);
        defaultPrimitiveValue.put(double.class, 0.0d);
    }

    /**
     * As a short-hand, if a {@link DataModel} has only one required parameter,
     * {@link #instantiate(Class)} accepts a single-item map whose key is this magic token.
     *
     * <p>
     * To avoid clients from needing to special-case this key, {@link #from(Object)} does not
     * produce {@link #arguments} that contains this magic token. Clients who want
     * to take advantages of this should look at {@link DataModel#hasSingleRequiredParameter()}
     */
    public static final String ANONYMOUS_KEY = "<anonymous>";
}
