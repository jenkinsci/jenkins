package hudson.util;

import junit.framework.TestCase;

import java.security.SecureRandom;

import hudson.Util;
import hudson.model.Hudson;

/**
 * @author Kohsuke Kawaguchi
 */
public class SecretTest extends TestCase {
    protected void setUp() throws Exception {
        SecureRandom sr = new SecureRandom();
        byte[] random = new byte[32];
        sr.nextBytes(random);
        Secret.SECRET = Util.toHexString(random);

    }

    protected void tearDown() throws Exception {
        Secret.SECRET = null;
    }

    public void testEncrypt() {
        Secret secret = Secret.fromString("abc");
        assertEquals("abc",secret.toString());

        // make sure we got some encryption going
        System.out.println(secret.getEncryptedValue());
        assertTrue(!"abc".equals(secret.getEncryptedValue()));

        // can we round trip?
        assertEquals(secret,Secret.fromString(secret.getEncryptedValue()));
    }

    public void testDecrypt() {
        assertEquals("abc",Secret.fromString("abc").toString());
    }

    public void testSerialization() {
        Secret s = Secret.fromString("Mr.Hudson");
        String xml = Hudson.XSTREAM.toXML(s);
        System.out.println(xml);
        assertTrue(!xml.contains(s.toString()));
        assertTrue(xml.contains(s.getEncryptedValue()));
        Object o = Hudson.XSTREAM.fromXML(xml);
        assertEquals(o,s);
    }
}
