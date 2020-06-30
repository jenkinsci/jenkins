package hudson.model;

import hudson.util.FormValidation;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ChoiceParameterDefinitionTest {
    @Test
    public void shouldValidateChoices(){
        assertFalse(ChoiceParameterDefinition.areValidChoices(""));
        assertFalse(ChoiceParameterDefinition.areValidChoices("        "));
        assertTrue(ChoiceParameterDefinition.areValidChoices("abc"));
        assertTrue(ChoiceParameterDefinition.areValidChoices("abc\ndef"));
        assertTrue(ChoiceParameterDefinition.areValidChoices("abc\r\ndef"));
    }

    @Test
    public void testCheckChoices() {
        ChoiceParameterDefinition.DescriptorImpl descriptorImpl = new ChoiceParameterDefinition.DescriptorImpl();

        assertEquals(FormValidation.Kind.OK, descriptorImpl.doCheckChoices("abc\ndef").kind);
        assertEquals(FormValidation.Kind.ERROR, descriptorImpl.doCheckChoices("").kind);
    }

    @Test
    @Issue("JENKINS-60721")
    public void testNullDefaultParameter() {
        ChoiceParameterDefinition param = new ChoiceParameterDefinition("name", new String[0], null);
        assertNull(param.getDefaultParameterValue());
    }

    @Test
    public void createNullChoice() {
        String stringValue = null;
        String[] choices = new String[]{stringValue};
        assertCreation(stringValue, choices, true);
    }

    @Test
    @Issue("JENKINS-62889")
    public void checkValue_Null() {
        String stringValue = null;
        String[] choices = new String[]{stringValue};
        assertCheckValue(stringValue, choices, true);
    }

    @Test
    public void createBlankChoice() {
        String stringValue = "";
        String[] choices = new String[]{stringValue};
        assertCreation(stringValue, choices, true);
    }

    @Test
    @Issue("JENKINS-62889")
    public void checkValue_Blank() {
        String stringValue = "";
        String[] choices = new String[]{stringValue};
        assertCheckValue(stringValue, choices, true);
    }

    @Test
    public void createEmptyChoice() {
        String stringValue = "   ";
        String[] choices = new String[]{stringValue};
        assertCreation(stringValue, choices, true);
    }

    @Test
    @Issue("JENKINS-62889")
    public void checkValue_Empty() {
        String stringValue = "";
        String[] choices = new String[]{stringValue};
        assertCheckValue(stringValue, choices, true);
    }

    @Test
    @Issue("JENKINS-62889")
    public void checkValue_Single() {
        String stringValue = "single";
        String[] choices = new String[]{stringValue};
        assertCheckValue(stringValue, choices, true);
    }

    @Test
    @Issue("JENKINS-62889")
    public void checkValue_Multiple() {
        String[] choices = new String[]{"one", "two", "three"};
        ChoiceParameterDefinition parameterDefinition = new ChoiceParameterDefinition("name", choices, "description");
        for (String choiceValue : choices) {
            StringParameterValue parameterValue = parameterDefinition.createValue(choiceValue);
            assertTrue(parameterDefinition.isValid(parameterValue));
        }
    }

    @Test
    @Issue("JENKINS-62889")
    public void checkValue_Invalid() {
        String stringValue = "single";
        String[] choices = new String[]{stringValue};
        ChoiceParameterDefinition parameterDefinition = new ChoiceParameterDefinition("name", choices, "description");
        StringParameterValue parameterValue = new StringParameterValue("choice", "invalid");
        assertFalse(parameterDefinition.isValid(parameterValue));
    }

    @Test(expected = ClassCastException.class)
    @Issue("JENKINS-62889")
    public void checkValue_WrongValueType() {
        String stringValue = "single";
        String[] choices = new String[]{stringValue};
        ChoiceParameterDefinition parameterDefinition = new ChoiceParameterDefinition("name", choices, "description");
        BooleanParameterValue parameterValue = new BooleanParameterValue("choice", false);
        parameterDefinition.isValid(parameterValue);
    }

    @Test(expected = IllegalArgumentException.class)
    @Issue("JENKINS-62889")
    public void createValue_Invalid() {
        String stringValue = "single";
        String[] choices = new String[]{stringValue};
        ChoiceParameterDefinition parameterDefinition = new ChoiceParameterDefinition("name", choices, "description");
        parameterDefinition.createValue("invalid");
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
