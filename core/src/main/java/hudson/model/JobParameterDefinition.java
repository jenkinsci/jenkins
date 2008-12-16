package hudson.model;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

public class JobParameterDefinition extends ParameterDefinition {

    @DataBoundConstructor
    public JobParameterDefinition(String name) {
        super(name);
    }

    @Override
    public ParameterDescriptor getDescriptor() {
        return DESCRIPTOR;
    }

    public static final ParameterDescriptor DESCRIPTOR = new DescriptorImpl();

    public static class DescriptorImpl extends ParameterDescriptor {

        protected DescriptorImpl() {
            super(JobParameterDefinition.class);
        }

        @Override
        public String getDisplayName() {
            return "Project Parameter";
        }

        @Override
        public ParameterDefinition newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return req.bindJSON(JobParameterDefinition.class, formData);
        }

    }

    @Override
    public ParameterValue createValue(StaplerRequest req, JSONObject jo) {
        return req.bindJSON(JobParameterValue.class, jo);
    }

}
