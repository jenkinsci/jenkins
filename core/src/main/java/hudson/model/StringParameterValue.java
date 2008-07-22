package hudson.model;

import org.kohsuke.stapler.DataBoundConstructor;

import java.util.Map;

/**
 * {@link ParameterValue} created from {@link StringParameterDefinition}.
 */
public class StringParameterValue extends ParameterValue {
    public final String value;

    @DataBoundConstructor
    public StringParameterValue(String name, String value) {
        super(name);
        this.value = value;
    }

    /**
     * Exposes the name/value as an environment variable.
     */
    @Override
    public void buildEnvVars(AbstractBuild<?,?> build, Map<String,String> env) {
        env.put(name.toUpperCase(),value);
    }
}
