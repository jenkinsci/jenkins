package org.acegisecurity.util;

import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SuppressWarnings("deprecation")
public class FieldUtilsTest {

    @Issue("JENKINS-64390")
    @Test
    public void setProtectedFieldValue_Should_fail_silently_to_set_public_final_fields_in_InnerClass() throws Exception {
        InnerClassWithPublicFinalField sut = new InnerClassWithPublicFinalField();
        FieldUtils.setProtectedFieldValue("myField", sut, "test");
        assertEquals(sut.getMyField(), "original");
    }

    @Test
    @Issue("JENKINS-64390")
    public void setProtectedFieldValue_Should_fail_silently_to_set_public_final_fields_in_OuterClass() throws Exception {
        OuterClassWithPublicFinalField sut = new OuterClassWithPublicFinalField();
        FieldUtils.setProtectedFieldValue("myField", sut, "test");
        assertEquals(sut.getMyField(), "original");
    }

    @Test
    public void setProtectedFieldValue_Should_Succeed() throws Exception {
        InnerClassWithProtectedField sut = new InnerClassWithProtectedField();
        FieldUtils.setProtectedFieldValue("myProtectedField", sut, "test");
        assertEquals(sut.getMyNonFinalField(), "test");
    }

    @Test
    public void setNonExistingField_Should_Fail() throws Exception {
        InnerClassWithProtectedField sut = new InnerClassWithProtectedField();
        assertThrows(Exception.class, () -> FieldUtils.setProtectedFieldValue("bogus", sut, "whatever"));
    }

    class InnerClassWithPublicFinalField {

        public final String myField = "original";

        public String getMyField() {
            return myField;
        }
        
    }

    public class InnerClassWithProtectedField {

        protected String myProtectedField = "original";

        public String getMyNonFinalField() {
            return myProtectedField;
        }
    }

}
