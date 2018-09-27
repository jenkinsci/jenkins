package jenkins.data;

import java.lang.reflect.Type;

/**
 * Convenient partial implementation
 *
 * @author Kohsuke Kawaguchi
 */
/*package*/ abstract class AbstractDataModelParameter implements DataModelParameter {
    private ParameterType type;
    protected final String name;

    /**
     * Type of the value that {@link DataModel} expects in its setter/field.
     */
    protected final Type rawType;

    public AbstractDataModelParameter(String name, Type rawType) {
        this.name = name;
        this.rawType = rawType;
    }

    /**
     * Classification of the type of this parameter.
     * <p>
     * Originates from the pipeline plugin and I'm not sure the logic behind this.
     */
    public final ParameterType getType() {
        if (type==null)
            type = ParameterType.of(rawType);
        return type;
    }

    @Override
    public final Type getRawType() {
        return rawType;
    }

    @Override
    public final String getName() {
        return name;
    }

}
