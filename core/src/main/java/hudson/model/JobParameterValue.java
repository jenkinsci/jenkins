package hudson.model;

import org.kohsuke.stapler.DataBoundConstructor;

import java.util.Map;

public class JobParameterValue extends ParameterValue {
    public final Job job;

    @DataBoundConstructor
    public JobParameterValue(String name, Job job) {
        super(name);
        this.job = job;
    }

    /**
     * Exposes the name/value as an environment variable.
     */
    @Override
    public void buildEnvVars(AbstractBuild<?,?> build, Map<String,String> env) {
        // TODO: check with Tom if this is really what he had in mind
        env.put(name.toUpperCase(),job.toString());
    }
}
