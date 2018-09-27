package jenkins.data;

import hudson.model.Descriptor;
import org.jvnet.tiger_types.Types;

import javax.annotation.CheckForNull;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Stack;

/**
 * A property of {@link DataModel}
 *
 * @author Kohsuke Kawaguchi
 * @see DataModel#getParameter(String)
 */
public abstract class DataModelParameter {
    /**
     * Classification of the type of this parameter.
     * <p>
     * Originates from the pipeline plugin and I'm not sure the logic behind this.
     */
    public abstract ParameterType getType();

    /**
     * The type of this parameter, possibly with generics.
     */
    public abstract Type getRawType();

    /**
     * Gets the erasure of {@link #getRawType()}
     */
    public Class getErasedType() {
        return Types.erasure(getRawType());
    }

    public abstract String getName();


    /**
     * True if this parameter is required.
     */
    public abstract boolean isRequired();

    /**
     * True if this parameter is deprecated.
     */
    public abstract boolean isDeprecated();

    /**
     * Loads help defined for this parameter.
     *
     * @return some HTML (in English locale), if available, else null
     * @see Descriptor#doHelp
     */
    @CheckForNull
    public abstract String getHelp() throws IOException;

    public String getCapitalizedName() {
        return Character.toUpperCase(getName().charAt(0)) + getName().substring(1);
    }


    void toString(StringBuilder b, Stack<Class<?>> modelTypes) {
        b.append(getName());
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
}
