package hudson.util;

import org.junit.Assert;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;

public class SecretUtilTest {

    @Issue("JENKINS-47500")
    @Test
    public void decrypt() {
        String data = "{}";

        try {
            Secret secret = Secret.decrypt(data);
            Assert.assertNull(secret);
        } catch (ArrayIndexOutOfBoundsException e) {
            Assert.fail(data + " shouldn't throw an ArrayIndexOutOfBoundsException but returning null");
        }

    }


    @Issue("JENKINS-47500")
    @Test
    public void decryptJustSpace() {
        String data = " ";

        try {
            Secret secret = Secret.decrypt(data);
            Assert.assertNull(secret);
        } catch (ArrayIndexOutOfBoundsException e) {
            Assert.fail(data + " shouldn't throw an ArrayIndexOutOfBoundsException but returning null");
        }

    }

    @Issue("JENKINS-47500")
    @Test
    public void decryptWithSpace() {
        String data = "{ }";

        try {
            Secret secret = Secret.decrypt(data);
            Assert.assertNull(secret);
        } catch (ArrayIndexOutOfBoundsException e) {
            Assert.fail(data + " shouldn't throw an ArrayIndexOutOfBoundsException but returning null");
        }

    }

    @Issue("JENKINS-47500")
    @Test
    public void decryptWithSpaces() {
        String data = "{     }";

        try {
            Secret secret = Secret.decrypt(data);
            Assert.assertNull(secret);
        } catch (ArrayIndexOutOfBoundsException e) {
            Assert.fail(data + " shouldn't throw an ArrayIndexOutOfBoundsException but returning null");
        }

    }
}
