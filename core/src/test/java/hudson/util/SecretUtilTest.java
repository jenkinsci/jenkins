package hudson.util;

import org.junit.Assert;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;

public class SecretUtilTest {

    @Issue("JENKINS-47500")
    @Test
    public void decrypt() throws Exception {
        String data = "{}";

        extractedMethod68395(data); // CAP AL

    }


    @Issue("JENKINS-47500")
    @Test
    public void decryptJustSpace() throws Exception {
        String data = " ";

        extractedMethod68395(data); // CAP AL

    }

    @Issue("JENKINS-47500")
    @Test
    public void decryptWithSpace() throws Exception {
        String data = "{ }";

        extractedMethod68395(data); // CAP AL

    }

    @Issue("JENKINS-47500")
    @Test
    public void decryptWithSpaces() throws Exception {
        String data = "{     }";

        extractedMethod68395(data); // CAP AL

    }
 // CAP AL
    private void extractedMethod68395(final String data) { // CAP AL
        try { // CAP AL
            Secret secret = Secret.decrypt(data); // CAP AL
            Assert.assertNull(secret); // CAP AL
        } catch (ArrayIndexOutOfBoundsException e) { // CAP AL
            Assert.fail(data + " shouldn't throw an ArrayIndexOutOfBoundsException but returning null"); // CAP AL
        } // CAP AL
    } // CAP AL
}
