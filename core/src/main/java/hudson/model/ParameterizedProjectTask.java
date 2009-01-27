package hudson.model;

import hudson.model.Queue.Executable;
import hudson.util.QueueTaskFilter;

import java.io.IOException;
import java.util.List;

import javax.servlet.ServletException;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 * A task representing a project that should be built with a certain set of
 * parameter values
 */
public class ParameterizedProjectTask extends QueueTaskFilter {

    private final AbstractProject<?,?> project;
    private final List<ParameterValue> parameters;
    private final String triggeredBy;

    private static long COUNTER = System.currentTimeMillis(); 

    /**
     * Used for identifying the task in the queue
     */
    private final String key = Long.toString(COUNTER++);

    public ParameterizedProjectTask(AbstractProject<?,?> project, List<ParameterValue> parameters, String triggeredBy) {
        super(project);
        this.project = project;
        this.parameters = parameters;
        this.triggeredBy = triggeredBy;
    }

    /** @deprecated since 1.279 */
    @Deprecated
    public ParameterizedProjectTask(AbstractProject<?,?> project, List<ParameterValue> parameters) {
        this(project, parameters, null);
    }

    public AbstractProject<?, ?> getProject() {
        return project;
    }

    public List<ParameterValue> getParameters() {
        return parameters;
    }

    public String getTriggeredBy() {
        return triggeredBy;
    }

    @Override
    public Executable createExecutable() throws IOException {
        AbstractBuild<?, ?> build = project.createExecutable();
        if (project.isParameterized())
            build.addAction(new ParametersAction(parameters, build));

        return build;
    }

    /**
     * Cancels a scheduled build.
     */
    public void doCancelQueue( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        project.checkPermission(AbstractProject.BUILD);

        Hudson.getInstance().getQueue().cancel(this);
        rsp.forwardToPreviousPage(req);
    }
	

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        // triggeredBy is NOT included so different triggers won't schedule duplicate builds
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
        // triggeredBy is NOT checked so different triggers won't schedule duplicate builds
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
    
    public String getUrl() {
    	return getProject().getUrl() + "/parameters/queued/" + key + "/";
    }

	public String getQueueKey() {
		return key;
	}
}
