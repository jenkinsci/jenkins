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

import javax.crypto.SecretKey;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;

import com.trilead.ssh2.crypto.Base64;

/**
 * Encrypt/decrypt data by using a "session" key that only lasts for
 * the duration of the server instance.
 *
 * @author Kohsuke Kawaguchi
 * @see Scrambler
 * @since 1.162
 */
public class Protector {
    private static final String ALGORITHM = "DES";
    private static final String MAGIC = ":::";

    public static String protect(String secret) {
        try {
            Cipher cipher = Secret.getCipher(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, DES_KEY);
            return new String(Base64.encode(cipher.doFinal((secret+ MAGIC).getBytes("UTF-8"))));
        } catch (GeneralSecurityException e) {
            throw new Error(e); // impossible
        } catch (UnsupportedEncodingException e) {
            throw new Error(e); // impossible
        }
    }

    /**
     * Returns null if fails to decrypt properly.
     */
    public static String unprotect(String data) {
        if(data==null)      return null;
        try {
            Cipher cipher = Secret.getCipher(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, DES_KEY);
            String plainText = new String(cipher.doFinal(Base64.decode(data.toCharArray())), "UTF-8");
            if(plainText.endsWith(MAGIC))
                return plainText.substring(0,plainText.length()-3);
            return null;
        } catch (GeneralSecurityException e) {
            return null;
        } catch (UnsupportedEncodingException e) {
            throw new Error(e); // impossible
        } catch (IOException e) {
            return null;
        }
    }

    private static final SecretKey DES_KEY;

    static {
        try {
            DES_KEY = KeyGenerator.getInstance(ALGORITHM).generateKey();
        } catch (NoSuchAlgorithmException e) {
            throw new Error(e);
        }
    }
}
