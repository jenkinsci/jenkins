package org.acegisecurity.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;

@SuppressWarnings("deprecation")
class FieldUtilsTest {

    @Issue("JENKINS-64390")
    @Test
    void setProtectedFieldValue_Should_fail_silently_to_set_public_final_fields_in_InnerClass() {
        InnerClassWithPublicFinalField sut = new InnerClassWithPublicFinalField();
        FieldUtils.setProtectedFieldValue("myField", sut, "test");
        assertEquals("original", sut.getMyField());
    }

    @Test
    @Issue("JENKINS-64390")
    void setProtectedFieldValue_Should_fail_silently_to_set_public_final_fields_in_OuterClass() {
        OuterClassWithPublicFinalField sut = new OuterClassWithPublicFinalField();
        FieldUtils.setProtectedFieldValue("myField", sut, "test");
        assertEquals("original", sut.getMyField());
    }

    @Test
    void setProtectedFieldValue_Should_Succeed() {
        InnerClassWithProtectedField sut = new InnerClassWithProtectedField();
        FieldUtils.setProtectedFieldValue("myProtectedField", sut, "test");
        assertEquals("test", sut.getMyNonFinalField());
    }

    @Test
    void setNonExistingField_Should_Fail() {
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
