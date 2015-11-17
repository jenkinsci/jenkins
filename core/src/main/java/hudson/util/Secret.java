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

import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.trilead.ssh2.crypto.Base64;
import hudson.SystemProperties;
import jenkins.model.Jenkins;
import hudson.Util;
import jenkins.security.CryptoConfidentialKey;
import org.kohsuke.stapler.Stapler;

import javax.crypto.SecretKey;
import javax.crypto.Cipher;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * Glorified {@link String} that uses encryption in the persisted form, to avoid accidental exposure of a secret.
 *
 * <p>
 * This is not meant as a protection against code running in the same VM, nor against an attacker
 * who has local file system access on Jenkins master.
 *
 * <p>
 * {@link Secret}s can correctly read-in plain text password, so this allows the existing
 * String field to be updated to {@link Secret}.
 *
 * @author Kohsuke Kawaguchi
 */
public final class Secret implements Serializable {
    /**
     * Unencrypted secret text.
     */
    private final String value;

    private Secret(String value) {
        this.value = value;
    }

    /**
     * Obtains the secret in a plain text.
     *
     * @see #getEncryptedValue()
     * @deprecated as of 1.356
     *      Use {@link #toString(Secret)} to avoid NPE in case Secret is null.
     *      Or if you really know what you are doing, use the {@link #getPlainText()} method.
     */
    @Override
    @Deprecated
    public String toString() {
        return value;
    }

    /**
     * Obtains the plain text password.
     * Before using this method, ask yourself if you'd be better off using {@link Secret#toString(Secret)}
     * to avoid NPE.
     */
    public String getPlainText() {
        return value;
    }

    @Override
    public boolean equals(Object that) {
        return that instanceof Secret && value.equals(((Secret)that).value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    /**
     * Turns {@link Jenkins#getSecretKey()} into an AES key.
     *
     * @deprecated
     * This is no longer the key we use to encrypt new information, but we still need this
     * to be able to decrypt what's already persisted.
     */
    @Deprecated
    /*package*/ static SecretKey getLegacyKey() throws GeneralSecurityException {
        String secret = SECRET;
        if(secret==null)    return Jenkins.getInstance().getSecretKeyAsAES128();
        return Util.toAes128Key(secret);
    }

    /**
     * Encrypts {@link #value} and returns it in an encoded printable form.
     *
     * @see #toString() 
     */
    public String getEncryptedValue() {
        try {
            Cipher cipher = KEY.encrypt();
            // add the magic suffix which works like a check sum.
            return new String(Base64.encode(cipher.doFinal((value+MAGIC).getBytes("UTF-8"))));
        } catch (GeneralSecurityException e) {
            throw new Error(e); // impossible
        } catch (UnsupportedEncodingException e) {
            throw new Error(e); // impossible
        }
    }

    /**
     * Reverse operation of {@link #getEncryptedValue()}. Returns null
     * if the given cipher text was invalid.
     */
    public static Secret decrypt(String data) {
        if(data==null)      return null;
        try {
            byte[] in = Base64.decode(data.toCharArray());
            Secret s = tryDecrypt(KEY.decrypt(), in);
            if (s!=null)    return s;

            // try our historical key for backward compatibility
            Cipher cipher = getCipher("AES");
            cipher.init(Cipher.DECRYPT_MODE, getLegacyKey());
            return tryDecrypt(cipher, in);
        } catch (GeneralSecurityException e) {
            return null;
        } catch (UnsupportedEncodingException e) {
            throw new Error(e); // impossible
        } catch (IOException e) {
            return null;
        }
    }

    /*package*/ static Secret tryDecrypt(Cipher cipher, byte[] in) throws UnsupportedEncodingException {
        try {
            String plainText = new String(cipher.doFinal(in), "UTF-8");
            if(plainText.endsWith(MAGIC))
                return new Secret(plainText.substring(0,plainText.length()-MAGIC.length()));
            return null;
        } catch (GeneralSecurityException e) {
            return null; // if the key doesn't match with the bytes, it can result in BadPaddingException
        }
    }

    /**
     * Workaround for JENKINS-6459 / http://java.net/jira/browse/GLASSFISH-11862
     * This method uses specific provider selected via hudson.util.Secret.provider system property
     * to provide a workaround for the above bug where default provide gives an unusable instance.
     * (Glassfish Enterprise users should set value of this property to "SunJCE")
     */
    public static Cipher getCipher(String algorithm) throws GeneralSecurityException {
        return PROVIDER != null ? Cipher.getInstance(algorithm, PROVIDER)
                                : Cipher.getInstance(algorithm);
    }

    /**
     * Attempts to treat the given string first as a cipher text, and if it doesn't work,
     * treat the given string as the unencrypted secret value.
     *
     * <p>
     * Useful for recovering a value from a form field.
     *
     * @return never null
     */
    public static Secret fromString(String data) {
        data = Util.fixNull(data);
        Secret s = decrypt(data);
        if(s==null) s=new Secret(data);
        return s;
    }

    /**
     * Works just like {@link Secret#toString()} but avoids NPE when the secret is null.
     * To be consistent with {@link #fromString(String)}, this method doesn't distinguish
     * empty password and null password.
     */
    public static String toString(Secret s) {
        return s==null ? "" : s.value;
    }

    public static final class ConverterImpl implements Converter {
        public ConverterImpl() {
        }

        public boolean canConvert(Class type) {
            return type==Secret.class;
        }

        public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
            Secret src = (Secret) source;
            writer.setValue(src.getEncryptedValue());
        }

        public Object unmarshal(HierarchicalStreamReader reader, final UnmarshallingContext context) {
            return fromString(reader.getValue());
        }
    }

    private static final String MAGIC = "::::MAGIC::::";

    /**
     * Workaround for JENKINS-6459 / http://java.net/jira/browse/GLASSFISH-11862
     * @see #getCipher(String)
     */
    private static final String PROVIDER = SystemProperties.getProperty(Secret.class.getName()+".provider");

    /**
     * For testing only. Override the secret key so that we can test this class without {@link Jenkins}.
     */
    /*package*/ static String SECRET = null;

    /**
     * The key that encrypts the data on disk.
     */
    private static final CryptoConfidentialKey KEY = new CryptoConfidentialKey(Secret.class.getName());

    private static final long serialVersionUID = 1L;

    static {
        Stapler.CONVERT_UTILS.register(new org.apache.commons.beanutils.Converter() {
            public Secret convert(Class type, Object value) {
                return Secret.fromString(value.toString());
            }
        }, Secret.class);
    }
}
