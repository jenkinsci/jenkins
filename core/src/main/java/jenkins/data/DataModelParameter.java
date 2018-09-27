package jenkins.data;

import hudson.model.Descriptor;
import org.jvnet.tiger_types.Types;

import javax.annotation.CheckForNull;
import java.io.IOException;
import java.lang.reflect.Type;

/**
 * A property of {@link DataModel}
 *
 * @author Kohsuke Kawaguchi
 * @see DataModel#getParameter(String)
 */
public interface DataModelParameter {
    /**
     * Classification of the type of this parameter.
     * <p>
     * Originates from the pipeline plugin and I'm not sure the logic behind this.
     */
    ParameterType getType();

    /**
     * The type of this parameter, possibly with generics.
     */
    Type getRawType();

    /**
     * Gets the erasure of {@link #getRawType()}
     */
    default Class getErasedType() {
        return Types.erasure(getRawType());
    }

    String getName();


    /**
     * True if this parameter is required.
     */
    boolean isRequired();

    /**
     * True if this parameter is deprecated.
     */
    boolean isDeprecated();

    /**
     * Loads help defined for this parameter.
     *
     * @return some HTML (in English locale), if available, else null
     * @see Descriptor#doHelp
     */
    @CheckForNull
    String getHelp() throws IOException;

    default String getCapitalizedName() {
        return Character.toUpperCase(getName().charAt(0)) + getName().substring(1);
    }
}
