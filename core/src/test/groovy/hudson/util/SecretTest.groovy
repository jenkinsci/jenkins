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
package hudson.util

import com.trilead.ssh2.crypto.Base64;
import jenkins.model.Jenkins
import jenkins.security.ConfidentialStoreRule;
import org.apache.commons.lang.RandomStringUtils;
import org.junit.Rule
import org.junit.Test

import java.util.Random;
import javax.crypto.Cipher
import java.util.regex.Pattern;

/**
 * @author Kohsuke Kawaguchi
 */
public class SecretTest {
    @Rule
    public ConfidentialStoreRule confidentialStore = new ConfidentialStoreRule()

    @Rule
    public MockSecretRule mockSecretRule = new MockSecretRule()

    static final Pattern ENCRYPTED_VALUE_PATTERN = Pattern.compile("\\{?[A-Za-z0-9+/]+={0,2}}?");

    @Test
    void testEncrypt() {
        def secret = Secret.fromString("abc");
        assert "abc"==secret.plainText;

        // make sure we got some encryption going
        println secret.encryptedValue;
        assert !"abc".equals(secret.encryptedValue);

        // can we round trip?
        assert secret==Secret.fromString(secret.encryptedValue);

        //Two consecutive encryption requests of the same object should result in the same encrypted value - SECURITY-304
        assert secret.encryptedValue == secret.encryptedValue
        //Two consecutive encryption requests of different objects with the same value should not result in the same encrypted value - SECURITY-304
        assert secret.encryptedValue != Secret.fromString(secret.plainText).encryptedValue
    }

    @Test
    void testEncryptedValuePattern() {
        for (int i = 1; i < 100; i++) {
            String plaintext = RandomStringUtils.random(new Random().nextInt(i));
            String ciphertext = Secret.fromString(plaintext).getEncryptedValue();
            //println "${plaintext} → ${ciphertext}"
            assert ENCRYPTED_VALUE_PATTERN.matcher(ciphertext).matches();
        }
        //Not "plain" text
        assert !ENCRYPTED_VALUE_PATTERN.matcher("hello world").matches();
        //Not "plain" text
        assert !ENCRYPTED_VALUE_PATTERN.matcher("helloworld!").matches();
        //legacy key
        assert ENCRYPTED_VALUE_PATTERN.matcher("abcdefghijklmnopqr0123456789").matches();
        //legacy key
        assert ENCRYPTED_VALUE_PATTERN.matcher("abcdefghijklmnopqr012345678==").matches();
    }

    @Test
    void testDecrypt() {
        assert "abc"==Secret.toString(Secret.fromString("abc"))
    }

    @Test
    void testSerialization() {
        def s = Secret.fromString("Mr.Jenkins");
        def xml = Jenkins.XSTREAM.toXML(s);
        assert !xml.contains(s.plainText)
        assert xml ==~ /<hudson\.util\.Secret>\{[A-Za-z0-9+\/]+={0,2}}<\/hudson\.util\.Secret>/

        def o = Jenkins.XSTREAM.fromXML(xml);
        assert o==s : xml;
    }

    public static class Foo {
        Secret password;
    }

    /**
     * Makes sure the serialization form is backward compatible with String.
     */
    @Test
    void testCompatibilityFromString() {
        def tagName = Foo.class.name.replace("\$","_-");
        def xml = "<$tagName><password>secret</password></$tagName>";
        def foo = new Foo();
        Jenkins.XSTREAM.fromXML(xml, foo);
        assert "secret"==Secret.toString(foo.password)
    }

    /**
     * Secret persisted with Jenkins.getSecretKey() should still decrypt OK.
     */
    @Test
    void migrationFromLegacyKeyToConfidentialStore() {
        def legacy = HistoricalSecrets.legacyKey
        ["Hello world","","\u0000unprintable"].each { str ->
            def cipher = Secret.getCipher("AES");
            cipher.init(Cipher.ENCRYPT_MODE, legacy);
            def old = new String(Base64.encode(cipher.doFinal((str + HistoricalSecrets.MAGIC).getBytes("UTF-8"))))
            def s = Secret.fromString(old)
            assert s.plainText==str : "secret by the old key should decrypt"
            assert s.encryptedValue!=old : "but when encrypting, ConfidentialKey should be in use"
        }
    }
}
