package hudson.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.util.FormValidation;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;

class ChoiceParameterDefinitionTest {

    @Test
    void shouldValidateChoices() {
        assertFalse(ChoiceParameterDefinition.areValidChoices(""));
        assertFalse(ChoiceParameterDefinition.areValidChoices("        "));
        assertTrue(ChoiceParameterDefinition.areValidChoices("abc"));
        assertTrue(ChoiceParameterDefinition.areValidChoices("abc\ndef"));
        assertTrue(ChoiceParameterDefinition.areValidChoices("abc\r\ndef"));
    }

    @Test
    void testCheckChoices() {
        ChoiceParameterDefinition.DescriptorImpl descriptorImpl = new ChoiceParameterDefinition.DescriptorImpl();

        assertEquals(FormValidation.Kind.OK, descriptorImpl.doCheckChoices("abc\ndef").kind);
        assertEquals(FormValidation.Kind.ERROR, descriptorImpl.doCheckChoices("").kind);
    }

    @Test
    @Issue("JENKINS-60721")
    void testNullDefaultParameter() {
        ChoiceParameterDefinition param = new ChoiceParameterDefinition("name", new String[0], null);
        assertNull(param.getDefaultParameterValue());
    }

    @Test
    void createNullChoice() {
        String stringValue = null;
        String[] choices = new String[]{stringValue};
        assertCreation(stringValue, choices, true);
    }

    @Test
    @Issue("JENKINS-62889")
    void checkValue_Null() {
        String stringValue = null;
        String[] choices = new String[]{stringValue};
        assertCheckValue(stringValue, choices, true);
    }

    @Test
    void createBlankChoice() {
        String stringValue = "";
        String[] choices = new String[]{stringValue};
        assertCreation(stringValue, choices, true);
    }

    @Test
    @Issue("JENKINS-62889")
    void checkValue_Blank() {
        String stringValue = "";
        String[] choices = new String[]{stringValue};
        assertCheckValue(stringValue, choices, true);
    }

    @Test
    void createEmptyChoice() {
        String stringValue = "   ";
        String[] choices = new String[]{stringValue};
        assertCreation(stringValue, choices, true);
    }

    @Test
    @Issue("JENKINS-62889")
    void checkValue_Empty() {
        String stringValue = "";
        String[] choices = new String[]{stringValue};
        assertCheckValue(stringValue, choices, true);
    }

    @Test
    @Issue("JENKINS-62889")
    void checkValue_Single() {
        String stringValue = "single";
        String[] choices = new String[]{stringValue};
        assertCheckValue(stringValue, choices, true);
    }

    @Test
    @Issue("JENKINS-62889")
    void checkValue_Multiple() {
        String[] choices = new String[]{"one", "two", "three"};
        ChoiceParameterDefinition parameterDefinition = new ChoiceParameterDefinition("name", choices, "description");
        for (String choiceValue : choices) {
            StringParameterValue parameterValue = parameterDefinition.createValue(choiceValue);
            assertTrue(parameterDefinition.isValid(parameterValue));
        }
    }

    @Test
    @Issue("JENKINS-62889")
    void checkValue_Invalid() {
        String stringValue = "single";
        String[] choices = new String[]{stringValue};
        ChoiceParameterDefinition parameterDefinition = new ChoiceParameterDefinition("name", choices, "description");
        StringParameterValue parameterValue = new StringParameterValue("choice", "invalid");
        assertFalse(parameterDefinition.isValid(parameterValue));
    }

    @Test
    @Issue("JENKINS-62889")
    void checkValue_WrongValueType() {
        String stringValue = "single";
        String[] choices = new String[]{stringValue};
        ChoiceParameterDefinition parameterDefinition = new ChoiceParameterDefinition("name", choices, "description");
        BooleanParameterValue parameterValue = new BooleanParameterValue("choice", false);
        assertThrows(ClassCastException.class, () -> parameterDefinition.isValid(parameterValue));
    }

    @Test
    @Issue("JENKINS-62889")
    void createValue_Invalid() {
        String stringValue = "single";
        String[] choices = new String[]{stringValue};
        ChoiceParameterDefinition parameterDefinition = new ChoiceParameterDefinition("name", choices, "description");
        assertThrows(IllegalArgumentException.class, () -> parameterDefinition.createValue("invalid"));
    }

    private void assertCreation(String stringValue, String[] choices, boolean valid) {
        ChoiceParameterDefinition parameterDefinition = new ChoiceParameterDefinition("name", choices, "description");
        StringParameterValue parameterValue = new StringParameterValue("choice", stringValue);
        assertThat(parameterDefinition.isValid(parameterValue), is(valid));
    }

    private void assertCheckValue(String stringValue, String[] choices, boolean valid) {
        ChoiceParameterDefinition parameterDefinition = new ChoiceParameterDefinition("name", choices, "description");
        StringParameterValue parameterValue = new StringParameterValue("choice", stringValue);
        assertThat(parameterDefinition.isValid(parameterValue), is(valid));
    }

}
