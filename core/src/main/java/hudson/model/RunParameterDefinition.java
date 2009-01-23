package hudson.model;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

public class RunParameterDefinition extends ParameterDefinition {

    @DataBoundConstructor
    public RunParameterDefinition(String name) {
        super(name);
    }

    @Override
    public ParameterDescriptor getDescriptor() {
        return DESCRIPTOR;
    }

    public static final ParameterDescriptor DESCRIPTOR = new DescriptorImpl();

    public static class DescriptorImpl extends ParameterDescriptor {
        @Override
        public String getDisplayName() {
            return "Run Parameter";
        }

        @Override
        public ParameterDefinition newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return req.bindJSON(RunParameterDefinition.class, formData);
        }
    }

    @Override
    public ParameterValue createValue(StaplerRequest req, JSONObject jo) {
        return req.bindJSON(RunParameterValue.class, jo);
    }

	@Override
	public ParameterValue createValue(StaplerRequest req) {
		throw new UnsupportedOperationException();
	}

}
