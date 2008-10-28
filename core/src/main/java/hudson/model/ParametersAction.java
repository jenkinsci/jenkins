package hudson.model;

import hudson.Util;
import hudson.tasks.BuildWrapper;
import hudson.util.VariableResolver;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Records the parameter values used for a build.
 *
 * <P>
 * This object is associated with the build record so that we remember what parameters
 * were used for what build.
 */
public class ParametersAction implements Action, Iterable<ParameterValue> {

    private final List<ParameterValue> parameters;
    private final AbstractBuild<?, ?> build;

    public ParametersAction(List<ParameterValue> parameters, AbstractBuild<?, ?> build) {
        this.parameters = parameters;
        this.build = build;
    }

    public void createBuildWrappers(AbstractBuild<?,?> build, Collection<? super BuildWrapper> result) {
        for (ParameterValue p : parameters) {
            BuildWrapper w = p.createBuildWrapper(build);
            if(w!=null) result.add(w);
        }
    }

    public void buildEnvVars(AbstractBuild<?,?> build, Map<String,String> env) {
        for (ParameterValue p : parameters)
            p.buildEnvVars(build,env);
    }

    /**
     * Performs a variable subsitution to the given text and return it.
     */
    public String substitute(AbstractBuild<?,?> build, String text) {
        return Util.replaceMacro(text,createVariableResolver(build));
    }

    /**
     * Creates an {@link VariableResolver} that aggregates all the parameters.
     */
    public VariableResolver<String> createVariableResolver(AbstractBuild<?,?> build) {
        VariableResolver[] resolvers = new VariableResolver[parameters.size()+1];
        int i=0;
        for (ParameterValue p : parameters)
            resolvers[i++] = p.createVariableResolver(build);

        resolvers[i] = build.getBuildVariableResolver();

        return new VariableResolver.Union<String>(resolvers);
    }

    public AbstractBuild<?, ?> getBuild() {
        return build;
    }

    public Iterator<ParameterValue> iterator() {
        return parameters.iterator();
    }

    public List<ParameterValue> getParameters() {
        return parameters;
    }

    @Override
    public String getDisplayName() {
        return Messages.ParameterAction_DisplayName();
    }

    @Override
    public String getIconFileName() {
        return "document-properties.gif";
    }

    @Override
    public String getUrlName() {
        return "parameters";
    }
}
