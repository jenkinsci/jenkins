package hudson.util;

import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;

class SecretUtilTest {

    @Issue("JENKINS-47500")
    @Test
    void decrypt() {
        String data = "{}";
        Secret secret = Secret.decrypt(data);
        assertNull(secret); // expected to not throw ArrayIndexOutOfBoundsException
    }


    @Issue("JENKINS-47500")
    @Test
    void decryptJustSpace() {
        String data = " ";
        Secret secret = Secret.decrypt(data);
        assertNull(secret); // expected to not throw ArrayIndexOutOfBoundsException
    }

    @Issue("JENKINS-47500")
    @Test
    void decryptWithSpace() {
        String data = "{ }";
        Secret secret = Secret.decrypt(data);
        assertNull(secret); // expected to not throw ArrayIndexOutOfBoundsException
    }

    @Issue("JENKINS-47500")
    @Test
    void decryptWithSpaces() {
        String data = "{     }";
        Secret secret = Secret.decrypt(data);
        assertNull(secret); // expected to not throw ArrayIndexOutOfBoundsException
    }
}
