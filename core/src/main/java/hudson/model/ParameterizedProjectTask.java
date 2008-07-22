package hudson.model;

import hudson.model.Queue.Executable;
import hudson.util.QueueTaskFilter;

import java.io.IOException;
import java.util.List;

/**
 * A task representing a project that should be built with a certain set of
 * parameter values
 */
public class ParameterizedProjectTask extends QueueTaskFilter {

    private final AbstractProject<?, ?> project;
    private final List<ParameterValue> parameters;

    public ParameterizedProjectTask(AbstractProject<?, ?> project,
                                    List<ParameterValue> parameters) {
        super(project);
        this.project = project;
        this.parameters = parameters;
    }

    @Override
    public Executable createExecutable() throws IOException {
        AbstractBuild<?, ?> build = project.createExecutable();
        build.addAction(new ParametersAction(parameters, build));

        return build;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result
                + ((parameters == null) ? 0 : parameters.hashCode());
        result = prime * result + ((project == null) ? 0 : project.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ParameterizedProjectTask other = (ParameterizedProjectTask) obj;
        if (parameters == null) {
            if (other.parameters != null)
                return false;
        } else if (!parameters.equals(other.parameters)) {
            return false;
        }
        if (project != other.project) {
            return false;
        }
        return true;
    }
}
