package hudson.model;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Parameter whose value is a string value.
 */
public class StringParameterDefinition extends ParameterDefinition {

    private boolean optional;
    private String defaultValue;

    @DataBoundConstructor
    public StringParameterDefinition(String name, String defaultValue,
                                     boolean optional) {
        super(name);
        this.defaultValue = defaultValue;
        this.optional = optional;
    }

    @Override
    public ParameterDescriptor getDescriptor() {
        return DESCRIPTOR;
    }

    public boolean isOptional() {
        return optional;
    }

    public void setOptional(boolean optional) {
        this.optional = optional;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public static final ParameterDescriptor DESCRIPTOR = new DescriptorImpl();

    public static class DescriptorImpl extends ParameterDescriptor {

        protected DescriptorImpl() {
            super(StringParameterDefinition.class);
        }

        @Override
        public String getDisplayName() {
            return "String Parameter";
        }

        @Override
        public ParameterDefinition newInstance(StaplerRequest req,
                                               JSONObject formData) throws FormException {
            return req.bindJSON(StringParameterDefinition.class, formData);
        }

    }

    @Override
    public ParameterValue createValue(StaplerRequest req, JSONObject jo) {
        return req.bindJSON(StringParameterValue.class, jo);
    }

}
