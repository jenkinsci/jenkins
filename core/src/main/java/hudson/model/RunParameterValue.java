package hudson.model;

import org.kohsuke.stapler.DataBoundConstructor;

import java.util.Map;

public class RunParameterValue extends ParameterValue {

    public final Run run;

    @DataBoundConstructor
    public RunParameterValue(String name, Run run) {
        super(name);
        this.run = run;
    }

    /**
     * Exposes the name/value as an environment variable.
     */
    @Override
    public void buildEnvVars(AbstractBuild<?,?> build, Map<String,String> env) {
        // TODO: check with Tom if this is really what he had in mind
        env.put(name.toUpperCase(),run.toString());
    }
}
