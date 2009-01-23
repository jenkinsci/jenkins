package hudson.model;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Parameter whose value is a string value.
 */
public class StringParameterDefinition extends ParameterDefinition {

    private String defaultValue;

    @DataBoundConstructor
    public StringParameterDefinition(String name, String defaultValue) {
        super(name);
        this.defaultValue = defaultValue;
    }

    @Override
    public ParameterDescriptor getDescriptor() {
        return DESCRIPTOR;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }
    
    public StringParameterValue getDefaultParameterValue() {
        return new StringParameterValue(getName(), defaultValue);
    }

    public static final ParameterDescriptor DESCRIPTOR = new DescriptorImpl();

    public static class DescriptorImpl extends ParameterDescriptor {
        @Override
        public String getDisplayName() {
            return Messages.StringParameterDefinition_DisplayName();
        }

        @Override
        public String getHelpFile() {
            return "/help/parameter/string.html";
        }
    }

    @Override
    public ParameterValue createValue(StaplerRequest req, JSONObject jo) {
        return req.bindJSON(StringParameterValue.class, jo);
    }

	@Override
	public ParameterValue createValue(StaplerRequest req) {
        String[] value = req.getParameterValues(getName());
        if (value == null) {
        	return getDefaultParameterValue();
        } else if (value.length != 1) {
        	throw new IllegalArgumentException("Illegal number of parameter values for " + getName() + ": " + value.length);
        } else 
        	return new StringParameterValue(getName(), value[0]);
	}

}
