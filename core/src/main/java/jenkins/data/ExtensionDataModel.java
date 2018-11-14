package jenkins.data;

import hudson.model.Descriptor;
import jenkins.data.tree.Mapping;
import jenkins.model.Jenkins;

import java.beans.Introspector;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.List;

/**
 * {@link ReflectiveDataModel} to access an extension (typically, a {@link Descriptor}).
 * Parameters are indentified based on exposed javabean setters to mimic
 * {@link org.kohsuke.stapler.RequestImpl#fill(Object, String, Object)}
 *
 */
public class ExtensionDataModel<E> extends ReflectiveDataModel<E> {

    private List<ReflectiveDataModelParameter> constructorParameters;


    public ExtensionDataModel(Class<E> clazz) {
        super(clazz);

        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            for (Method m : c.getMethods()) {
                Type[] parameterTypes = m.getGenericParameterTypes();
                if (!m.getName().startsWith("set") || parameterTypes.length != 1) {
                    continue;
                }
                parameters.add(new ReflectiveDataModelParameter(this, m));
            }
        }
    }

    @Override
    protected E getInstance(Mapping input, DataContext context) {
        return Jenkins.get().getInjector().getInstance(getType());
    }
}
