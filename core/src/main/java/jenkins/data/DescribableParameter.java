package jenkins.data;

import java.lang.reflect.Type;

/**
 * @author Kohsuke Kawaguchi
 */
public final class DescribableParameter {
    private ModelBinder<?> parent;
    private ParameterType type;
    private String name;

    /**
     * Type of the value that {@link Describable} expects in its setter/field.
     */
    private Type rawType;

    /**
     * If this property is optional, the {@link Setter} that abstracts away how to set
     * the value to this property. Otherwise this parameter must be injected via the constructor.
     */
    /*package*/ Setter setter;


}