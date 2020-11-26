package hudson.model;

import hudson.util.FormValidation;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.export.Exported;
import org.apache.commons.lang.StringUtils;
import net.sf.json.JSONObject;
import hudson.Extension;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.util.Objects;

/**
 * @author huybrechts
 */
public class ChoiceParameterDefinition extends SimpleParameterDefinition {
    public static final String CHOICES_DELIMITER = "\\r?\\n";

    @Deprecated
    public static final String CHOICES_DELIMETER = CHOICES_DELIMITER;


    private /* quasi-final */ List<String> choices;
    private final String defaultValue;

    public static boolean areValidChoices(String choices) {
        String strippedChoices = choices.trim();
        return !StringUtils.isEmpty(strippedChoices) && strippedChoices.split(CHOICES_DELIMITER).length > 0;
    }

    public ChoiceParameterDefinition(@NonNull String name, @NonNull String choices, String description) {
        super(name, description);
        setChoicesText(choices);
        defaultValue = null;
    }

    public ChoiceParameterDefinition(@NonNull String name, @NonNull String[] choices, String description) {
        super(name, description);
        this.choices = new ArrayList<>(Arrays.asList(choices));
        defaultValue = null;
    }

    private ChoiceParameterDefinition(@NonNull String name, @NonNull List<String> choices, String defaultValue, String description) {
        super(name, description);
        this.choices = choices;
        this.defaultValue = defaultValue;
    }

    /**
     * Databound constructor for reflective instantiation.
     *
     * @param name parameter name
     * @param description parameter description
     *
     * @since 2.112
     */
    @DataBoundConstructor
    @Restricted(NoExternalUse.class) // there are specific constructors with String and List arguments for 'choices'
    public ChoiceParameterDefinition(String name, String description) {
        super(name, description);
        this.choices = new ArrayList<>();
        this.defaultValue = null;
    }

    /**
     * Set the list of choices. Legal arguments are String (in which case the arguments gets split into lines) and Collection
     * which sets the list of legal parameters to the String representations of the argument's non-null entries.
     *
     * See JENKINS-26143 for background.
     *
     * This retains the compatibility with the legacy String 'choices' parameter, while supporting the list type as generated
     * by the snippet generator.
     *
     * @param choices String or Collection representing this parameter definition's possible values.
     *
     * @since 2.112
     *
     */
    @DataBoundSetter
    @Restricted(NoExternalUse.class) // this is terrible enough without being used anywhere
    public void setChoices(Object choices) {
        if (choices instanceof String) {
            setChoicesText((String) choices);
            return;
        }
        if (choices instanceof List) {
            ArrayList<String> newChoices = new ArrayList<>();
            for (Object o : (List) choices) {
                if (o != null) {
                    newChoices.add(o.toString());
                }
            }
            this.choices = newChoices;
            return;
        }
        throw new IllegalArgumentException("expected String or List, but got " + choices.getClass().getName());
    }

    private void setChoicesText(String choices) {
        this.choices = Arrays.asList(choices.split(CHOICES_DELIMITER));
    }

    @Override
    public ParameterDefinition copyWithDefaultValue(ParameterValue defaultValue) {
        if (defaultValue instanceof StringParameterValue) {
            StringParameterValue value = (StringParameterValue) defaultValue;
            return new ChoiceParameterDefinition(getName(), choices, value.value, getDescription());
        } else {
            return this;
        }
    }

    @Exported
    public List<String> getChoices() {
        return choices;
    }

    public String getChoicesText() {
        return StringUtils.join(choices, "\n");
    }

    @Override
    @CheckForNull
    public StringParameterValue getDefaultParameterValue() {
        if (defaultValue == null) {
            if (choices.isEmpty()) {
                return null;
            }
            return new StringParameterValue(getName(), choices.get(0), getDescription());
        }
        return new StringParameterValue(getName(), defaultValue, getDescription());
    }

    @Override
    public boolean isValid(ParameterValue value) {
        return choices.contains(((StringParameterValue) value).getValue());
    }

    @Override
    public ParameterValue createValue(StaplerRequest req, JSONObject jo) {
        StringParameterValue value = req.bindJSON(StringParameterValue.class, jo);
        value.setDescription(getDescription());
        checkValue(value, value.getValue());
        return value;
    }

    private void checkValue(StringParameterValue value, String value2) {
        if (!isValid(value)) {
            throw new IllegalArgumentException("Illegal choice for parameter " + getName() + ": " + value2);
        }
    }

    public StringParameterValue createValue(String value) {
        StringParameterValue parameterValue = new StringParameterValue(getName(), value, getDescription());
        checkValue(parameterValue, value);
        return parameterValue;
    }

    @Override
    public int hashCode() {
        if (ChoiceParameterDefinition.class != getClass()) {
            return super.hashCode();
        }
        return Objects.hash(getName(), getDescription(), choices, defaultValue);
    }

    @Override
    public boolean equals(Object obj) {
        if (ChoiceParameterDefinition.class != getClass())
            return super.equals(obj);
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ChoiceParameterDefinition other = (ChoiceParameterDefinition) obj;
        if (!Objects.equals(getName(), other.getName()))
            return false;
        if (!Objects.equals(getDescription(), other.getDescription()))
            return false;
        if (!Objects.equals(choices, other.choices))
                return false;
        return Objects.equals(defaultValue, other.defaultValue);
    }

    @Extension @Symbol({"choice","choiceParam"})
    public static class DescriptorImpl extends ParameterDescriptor {
        @Override
        public String getDisplayName() {
            return Messages.ChoiceParameterDefinition_DisplayName();
        }

        @Override
        public String getHelpFile() {
            return "/help/parameter/choice.html";
        }

        @Override
        /*
         * We need this for JENKINS-26143 -- reflective creation cannot handle setChoices(Object). See that method for context.
         */
        public ParameterDefinition newInstance(@Nullable StaplerRequest req, @NonNull JSONObject formData) throws FormException {
            String name = formData.getString("name");
            String desc = formData.getString("description");
            String choiceText = formData.getString("choices");
            return new ChoiceParameterDefinition(name, choiceText, desc);
        }

        /**
         * Checks if parameterized build choices are valid.
         */
        public FormValidation doCheckChoices(@QueryParameter String value) {
            if (ChoiceParameterDefinition.areValidChoices(value)) {
                return FormValidation.ok();
            } else {
                return FormValidation.error(Messages.ChoiceParameterDefinition_MissingChoices());
            }
        }
    }

}
