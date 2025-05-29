/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package hudson.util;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Random;
import java.util.regex.Pattern;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import jenkins.model.Jenkins;
import jenkins.security.ConfidentialStoreRule;
import org.junit.Rule;
import org.junit.Test;

public class SecretTest {

    @Rule
    public ConfidentialStoreRule confidentialStore = new ConfidentialStoreRule();

    private static final Pattern ENCRYPTED_VALUE_PATTERN = Pattern.compile("\\{?[A-Za-z0-9+/]+={0,2}}?");

    @Test
    public void encrypt() {
        Secret secret = Secret.fromString("abc");
        assertEquals("abc", secret.getPlainText());

        // make sure we got some encryption going
        assertNotEquals("abc", secret.getEncryptedValue());

        // can we round trip?
        assertEquals(secret, Secret.fromString(secret.getEncryptedValue()));

        //Two consecutive encryption requests of the same object should result in the same encrypted value - SECURITY-304
        assertEquals(secret.getEncryptedValue(), secret.getEncryptedValue());
        //Two consecutive encryption requests of different objects with the same value should not result in the same encrypted value - SECURITY-304
        assertNotEquals(secret.getEncryptedValue(), Secret.fromString(secret.getPlainText()).getEncryptedValue());
    }

    @Test
    public void encryptedValuePattern() {
        final Random random = new Random();
        for (int i = 1; i < 100; i++) {
            String plaintext = random(i, random);
            String ciphertext = Secret.fromString(plaintext).getEncryptedValue();
            //println "${plaintext} → ${ciphertext}"
            assertTrue(ENCRYPTED_VALUE_PATTERN.matcher(ciphertext).matches());
        }
        //Not "plain" text
        assertFalse(ENCRYPTED_VALUE_PATTERN.matcher("hello world").matches());
        //Not "plain" text
        assertFalse(ENCRYPTED_VALUE_PATTERN.matcher("helloworld!").matches());
        //legacy key
        assertTrue(ENCRYPTED_VALUE_PATTERN.matcher("abcdefghijklmnopqr0123456789").matches());
        //legacy key
        assertTrue(ENCRYPTED_VALUE_PATTERN.matcher("abcdefghijklmnopqr012345678==").matches());
    }

    private static String random(int count, Random random) {
        String result = "";
        for (int i = 0; i < count; i++) {
            result += (char) random.nextInt(30000);
        }
        return result;
    }

    @Test
    public void decrypt() {
        assertEquals("abc", Secret.toString(Secret.fromString("abc")));
    }

    @Test
    public void serialization() {
        Secret s = Secret.fromString("Mr.Jenkins");
        String xml = Jenkins.XSTREAM.toXML(s);
        assertThat(xml, not(containsString(s.getPlainText())));
        // TODO MatchesPattern not available until Hamcrest 2.0
        assertTrue(xml, xml.matches("<hudson[.]util[.]Secret>[{][A-Za-z0-9+/]+={0,2}[}]</hudson[.]util[.]Secret>"));

        Object o = Jenkins.XSTREAM.fromXML(xml);
        assertEquals(xml, s, o);
    }

    public static class Foo {
        Secret password;
    }

    /**
     * Makes sure the serialization form is backward compatible with String.
     */
    @Test
    public void testCompatibilityFromString() {
        String tagName = Foo.class.getName().replace("$", "_-");
        String xml = "<" + tagName + "><password>secret</password></" + tagName + ">";
        Foo foo = new Foo();
        Jenkins.XSTREAM.fromXML(xml, foo);
        assertEquals("secret", Secret.toString(foo.password));
    }

    /**
     * Secret persisted with Jenkins.getSecretKey() should still decrypt OK.
     */
    @Test
    @SuppressWarnings("deprecation")
    public void migrationFromLegacyKeyToConfidentialStore() throws Exception {
        SecretKey legacy = HistoricalSecrets.getLegacyKey();
        for (String str : new String[] {"Hello world", "", "\u0000unprintable"}) {
            Cipher cipher = Secret.getCipher("AES");
            cipher.init(Cipher.ENCRYPT_MODE, legacy);
            String old = Base64.getEncoder().encodeToString(cipher.doFinal((str + HistoricalSecrets.MAGIC).getBytes(StandardCharsets.UTF_8)));
            Secret s = Secret.fromString(old);
            assertEquals("secret by the old key should decrypt", str, s.getPlainText());
            assertNotEquals("but when encrypting, ConfidentialKey should be in use", old, s.getEncryptedValue());
        }
    }

}
