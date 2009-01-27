package hudson.model;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 * Keeps a list of the parameters defined for a project.
 *
 * <p>
 * This class also implements {@link Action} so that <tt>index.jelly</tt> provides
 * a form to enter build parameters. 
 */
public class ParametersDefinitionProperty extends JobProperty<AbstractProject<?, ?>>
        implements Action {

    private final List<ParameterDefinition> parameterDefinitions;

    public ParametersDefinitionProperty(List<ParameterDefinition> parameterDefinitions) {
        this.parameterDefinitions = parameterDefinitions;
    }

    public AbstractProject<?,?> getOwner() {
        return owner;
    }

    public List<ParameterDefinition> getParameterDefinitions() {
        return parameterDefinitions;
    }

    @Override
    public Action getJobAction(AbstractProject<?, ?> job) {
        return this;
    }

    public AbstractProject<?, ?> getProject() {
        return (AbstractProject<?, ?>) owner;
    }

    /**
     * Interprets the form submission and schedules a build for a parameterized job.
     *
     * <p>
     * This method is supposed to be invoked from {@link AbstractProject#doBuild(StaplerRequest, StaplerResponse)}.
     */
    public void _doBuild(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        if(!req.getMethod().equals("POST")) {
            // show the parameter entry form.
            req.getView(this,"index.jelly").forward(req,rsp);
            return;
        }

        List<ParameterValue> values = new ArrayList<ParameterValue>();
        
        JSONObject formData = req.getSubmittedForm();
        JSONArray a = JSONArray.fromObject(formData.get("parameter"));

        for (Object o : a) {
            JSONObject jo = (JSONObject) o;
            String name = jo.getString("name");

            ParameterDefinition d = getParameterDefinition(name);
            if(d==null)
                throw new IllegalArgumentException("No such parameter definition: " + name);
            values.add(d.createValue(req, jo));
        }

        scheduleBuild(values);

        // send the user back to the job top page.
        rsp.sendRedirect(".");
    }
    
    public void buildWithParameters(StaplerRequest req, StaplerResponse rsp) throws IOException {
        List<ParameterValue> values = new ArrayList<ParameterValue>();
        for (ParameterDefinition d: parameterDefinitions) {
        	ParameterValue value = d.createValue(req);
        	if (value != null) {
        		values.add(value);
        	} else {
        		throw new IllegalArgumentException("Parameter " + d.getName() + " was missing.");
        	}
        }

        scheduleBuild(values);

        // send the user back to the job top page.
        rsp.sendRedirect(".");
    }

    private boolean scheduleBuild(List<ParameterValue> parameters) {
        User user = User.current();
        return Hudson.getInstance().getQueue().add(
                new ParameterizedProjectTask(owner, parameters,
                    "User " + (user != null ? user.getId() : "anonymous")),
                0);
    }

    /**
     * Gets the {@link ParameterDefinition} of the given name, if any.
     */
    public ParameterDefinition getParameterDefinition(String name) {
        for (ParameterDefinition pd : parameterDefinitions)
            if (pd.getName().equals(name))
                return pd;
        return null;
    }

    @Override
    public JobPropertyDescriptor getDescriptor() {
        return DESCRIPTOR;
    }

    public static final JobPropertyDescriptor DESCRIPTOR = new DescriptorImpl();

    public static class DescriptorImpl extends JobPropertyDescriptor {
        @Override
        public boolean isApplicable(Class<? extends Job> jobType) {
            return AbstractProject.class.isAssignableFrom(jobType);
        }

        @Override
        public JobProperty<?> newInstance(StaplerRequest req,
                                          JSONObject formData) throws FormException {
            if (formData.isNullObject()) {
                return null;
            }

            if (req.getParameter("parameterized") == null) {
            	return null;
            }
            
            List<ParameterDefinition> parameterDefinitions = Descriptor.newInstancesFromHeteroList(
                    req, formData, "parameter", ParameterDefinition.LIST);
            if(parameterDefinitions.isEmpty())
                return null;

            return new ParametersDefinitionProperty(parameterDefinitions);
        }

        @Override
        public String getDisplayName() {
            return Messages.ParametersDefinitionProperty_DisplayName();
        }
    }

    public String getDisplayName() {
        return null;
    }

    public String getIconFileName() {
        return null;
    }

    public String getUrlName() {
        return "parameters";
    }
    
    public ParameterizedProjectTask getQueued(String key) {
    	Queue.Item[] items = Hudson.getInstance().getQueue().getItems();
    	for (Queue.Item item: items) {
    		if (item.task instanceof ParameterizedProjectTask) {
    			ParameterizedProjectTask ppt = (ParameterizedProjectTask) item.task;
				if (ppt.getProject() == owner && ppt.getQueueKey().equals(key)) {
    				return ppt;
    			}
    		}
    	}
    	return null;
    }

    static {
        ParameterDefinition.LIST.add(StringParameterDefinition.DESCRIPTOR);
        ParameterDefinition.LIST.add(FileParameterDefinition.DESCRIPTOR);
    }
}
