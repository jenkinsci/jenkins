package jenkins.data;

import hudson.model.Describable;
import hudson.model.ParametersDefinitionProperty;
import jenkins.data.tree.Mapping;
import jenkins.data.tree.TreeNode;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.ClassDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.CheckForNull;
import java.beans.Introspector;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link ReflectiveDataModel} dedicated to Describable components.
 * Parameters are indentified based on {@link DataBoundConstructor} and
 * {@link DataBoundSetter} annotated fields|setters to mimic
 * {@link org.kohsuke.stapler.RequestImpl#bindJSON(Class, JSONObject)}
 *
 */
public class DataBoundDataModel<D extends Describable<D>> extends ReflectiveDataModel<D> {

    /**
     * Data-bound constructor.
     */
    private final Constructor<D> constructor;

    private List<ReflectiveDataModelParameter> constructorParameters;
    
    
    public DataBoundDataModel(Class<D> clazz) {
        super(clazz);

        String[] constructorParamNames = new ClassDescriptor(clazz).loadConstructorParamNames();

        constructor = findConstructor(constructorParamNames.length);

        constructorParameters = new ArrayList<>(constructorParamNames.length);
        Type[] types = constructor.getGenericParameterTypes();
        for (int i = 0; i < constructorParamNames.length; i++) {
            final String name = constructorParamNames[i];
            constructorParameters.add(new ReflectiveDataModelParameter(this, types[i], name, null));
        }

        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.isAnnotationPresent(DataBoundSetter.class)) {
                    parameters.add(new ReflectiveDataModelParameter(this, f.getGenericType(), f.getName(), Setter.create(f)));
                }
            }
            for (Method m : c.getMethods()) {
                if (m.isAnnotationPresent(DataBoundSetter.class)) {
                    Type[] parameterTypes = m.getGenericParameterTypes();
                    if (!m.getName().startsWith("set") || parameterTypes.length != 1) {
                        throw new IllegalStateException(m + " cannot be a @DataBoundSetter");
                    }
                    parameters.add(new ReflectiveDataModelParameter(this, m.getGenericParameterTypes()[0],
                            Introspector.decapitalize(m.getName().substring(3)), Setter.create(m)));
                }
            }
        }
    }

    // adapted from RequestImpl
    @SuppressWarnings("unchecked")
    private Constructor<D> findConstructor(int length) {
        Constructor<D>[] ctrs = (Constructor<D>[]) type.getConstructors();
        for (Constructor<D> c : ctrs) {
            if (c.getAnnotation(DataBoundConstructor.class) != null) {
                if (c.getParameterTypes().length != length) {
                    throw new IllegalArgumentException(c + " has @DataBoundConstructor but it doesn't match with your .stapler file. Try clean rebuild");
                }
                return c;
            }
        }
        for (Constructor<D> c : ctrs) {
            if (c.getParameterTypes().length == length) {
                return c;
            }
        }
        throw new IllegalArgumentException(type + " does not have a constructor with " + length + " arguments");
    }


    @Override
    protected D getInstance(Mapping mapping, DataContext context)  {
        final TreeNode anonymous = mapping.get(ANONYMOUS_KEY);
        if (anonymous  != null) {
            if (mapping.size()!=1)
                throw new IllegalArgumentException("All arguments have to be named but it has "+ANONYMOUS_KEY);

            DataModelParameter rp = getSoleRequiredParameter();
            if (rp==null)
                throw new IllegalArgumentException("Arguments to "+type+" have to be explicitly named");
            mapping.clear();
            mapping.put(rp.getName(), anonymous);
        }

        try {
            Object[] args = constructorParameters.stream()
                    .map(p -> p.getType().from(mapping.getValue(p.getName()), context))
                    .toArray();

            return constructor.newInstance(args);
        } catch (Exception x) {
            throw new IllegalArgumentException("Could not instantiate " + mapping + " for " + this, x);
        }
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
        return parameters.stream()
                .filter(ReflectiveDataModelParameter::isRequired)
                .reduce((some, other) -> null) // if there's more than one, return null;
                .orElse(null);
    }

    /**
     * If this model has any required parameter, return the first one.
     * Otherwise null.
     */
    public @CheckForNull DataModelParameter getFirstRequiredParameter() {
        return parameters.stream()
                .filter(ReflectiveDataModelParameter::isRequired)
                .findAny()
                .orElse(null);
    }


    /**
     * As a short-hand, if a {@link DataModel} has only one required parameter,
     * {@link DataModel#read(TreeNode, DataContext)} accepts a single-item map whose key is this magic token.
     *
     * <p>
     * To avoid clients from needing to special-case this key, {@link #write(Object, DataContext)} does not
     * produce a tree that contains this magic token. Clients who want
     * to take advantages of this should look at {@link #hasSingleRequiredParameter()}
     */
    // TODO: which layer does this belong?
    public static final String ANONYMOUS_KEY = "<anonymous>";
}
