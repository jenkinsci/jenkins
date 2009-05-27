/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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
import hudson.model.Hudson;
import hudson.Util;

import javax.crypto.SecretKey;
import javax.crypto.Cipher;
import java.io.UnsupportedEncodingException;
import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * Glorified {@link String} that uses encryption in the persisted form, to avoid accidental exposure of a secret.
 *
 * <p>
 * Note that since the cryptography relies on {@link Hudson#getSecretKey()}, this is not meant as a protection
 * against code running in the same VM, nor against an attacker who has local file system access. 
 *
 * @author Kohsuke Kawaguchi
 */
public final class Secret {
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
     */
    public String toString() {
        return value;
    }

    public boolean equals(Object that) {
        return that instanceof Secret && value.equals(((Secret)that).value);
    }

    public int hashCode() {
        return value.hashCode();
    }

    /**
     * Turns {@link Hudson#getSecretKey()} into an AES key. 
     */
    private static SecretKey getKey() throws UnsupportedEncodingException, GeneralSecurityException {
        String secret = SECRET;
        if(secret==null)    return Hudson.getInstance().getSecretKeyAsAES128();
        return Util.toAes128Key(secret);
    }

    /**
     * Encrypts {@link #value} and returns it in an encoded printable form.
     *
     * @see #toString() 
     */
    public String getEncryptedValue() {
        try {
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, getKey());
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
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, getKey());
            String plainText = new String(cipher.doFinal(Base64.decode(data.toCharArray())), "UTF-8");
            if(plainText.endsWith(MAGIC))
                return new Secret(plainText.substring(0,plainText.length()-MAGIC.length()));
            return null;
        } catch (GeneralSecurityException e) {
            return null;
        } catch (UnsupportedEncodingException e) {
            throw new Error(e); // impossible
        } catch (IOException e) {
            return null;
        }
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
        Secret s = decrypt(data);
        if(s==null) s=new Secret(data);
        return s;
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
            return Secret.decrypt(reader.getValue());
        }
    }

    private static final String MAGIC = "::::MAGIC::::";

    /**
     * For testing only. Override the secret key so that we can test this class without {@link Hudson}.
     */
    /*package*/ static String SECRET = null;
}
