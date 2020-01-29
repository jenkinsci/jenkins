package hudson.model;

import hudson.util.FormValidation;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;

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
    public void testCheckChoices() throws Exception {
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
}
