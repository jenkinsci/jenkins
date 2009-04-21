package hudson.model;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.DataBoundConstructor;
import net.sf.json.JSONObject;
import hudson.Extension;

/**
 * @author huybrechts
 */
public class BooleanParameterDefinition extends ParameterDefinition {
    private final boolean defaultValue;

    @DataBoundConstructor
    public BooleanParameterDefinition(String name, boolean defaultValue, String description) {
        super(name, description);
        this.defaultValue = defaultValue;
    }

    public boolean isDefaultValue() {
        return defaultValue;
    }

    @Override
    public ParameterValue createValue(StaplerRequest req, JSONObject jo) {
        BooleanParameterValue value = req.bindJSON(BooleanParameterValue.class, jo);
        value.setDescription(getDescription());
        return value;
    }

    @Override
    public ParameterValue createValue(StaplerRequest req) {
        String[] value = req.getParameterValues(getName());
        if (value == null) {
            return getDefaultParameterValue();
        } else if (value.length != 1) {
            throw new IllegalArgumentException("Illegal number of parameter values for " + getName() + ": " + value.length);
        } else {
            boolean booleanValue = Boolean.parseBoolean(value[0]);
            return new BooleanParameterValue(getName(), booleanValue, getDescription());
        }
    }

    @Override
    public BooleanParameterValue getDefaultParameterValue() {
        return new BooleanParameterValue(getName(), defaultValue, getDescription());
    }

    @Extension
    public static class DescriptorImpl extends ParameterDescriptor {
        @Override
        public String getDisplayName() {
            return Messages.BooleanParameterDefinition_DisplayName();
        }

        @Override
        public String getHelpFile() {
            return "/help/parameter/boolean.html";
        }
    }

}
