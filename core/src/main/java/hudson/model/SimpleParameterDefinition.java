package hudson.model;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.cli.CLICommand;
import java.io.IOException;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest2;

/**
 * Convenient base class for {@link ParameterDefinition} whose value can be represented in a context-independent single string token.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class SimpleParameterDefinition extends ParameterDefinition {
    protected SimpleParameterDefinition(@NonNull String name) {
        super(name);
    }

    /**
     * @deprecated Prefer {@link #SimpleParameterDefinition(String)} with a {@link DataBoundConstructor} and allow {@link #setDescription} to be used as needed
     */
    @Deprecated
    protected SimpleParameterDefinition(@NonNull String name, @CheckForNull String description) {
        super(name, description);
    }

    /**
     * Creates a {@link ParameterValue} from the string representation.
     */
    public abstract ParameterValue createValue(String value);

    @Override
    public final ParameterValue createValue(StaplerRequest2 req) {
        String[] value = req.getParameterValues(getName());
        if (value == null) {
            return getDefaultParameterValue();
        } else if (value.length != 1) {
            throw new IllegalArgumentException("Illegal number of parameter values for " + getName() + ": " + value.length);
        } else {
            return createValue(value[0]);
        }
    }

    @Override
    public final ParameterValue createValue(CLICommand command, String value) throws IOException, InterruptedException {
        return createValue(value);
    }
}
