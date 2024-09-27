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

import hudson.RestrictedSince;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Encrypt/decrypt data by using a "session" key that only lasts for
 * the duration of the server instance.
 *
 * @author Kohsuke Kawaguchi
 * @see Scrambler
 * @since 1.162 and restricted since 2.236
 */
@Restricted(NoExternalUse.class)
@RestrictedSince("2.236")
public class Protector {
    private static final String ALGORITHM_MODE = "AES/CBC/PKCS5Padding";
    private static final String ALGORITHM = "AES";
    private static final String MAGIC = ":::MAGIC";
    private static final int IV_BYTES = 16;

    public static String protect(String secret) {
        try {
            final byte[] iv = new byte[IV_BYTES];
            SR.nextBytes(iv);
            Cipher cipher = Secret.getCipher(ALGORITHM_MODE);
            cipher.init(Cipher.ENCRYPT_MODE, KEY, new IvParameterSpec(iv));
            final byte[] encrypted = cipher.doFinal((secret + MAGIC).getBytes(StandardCharsets.UTF_8));
            byte[] result = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(encrypted, 0, result, iv.length, encrypted.length);
            return new String(Base64.getEncoder().encode(result), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new Error(e); // impossible
        }
    }

    /**
     * Returns null if fails to decrypt properly.
     */
    public static String unprotect(String data) {
        if (data == null) {
            return null;
        }
        try {
            final byte[] value = Base64.getDecoder().decode(data.getBytes(StandardCharsets.UTF_8));
            final byte[] iv = Arrays.copyOfRange(value, 0, IV_BYTES);
            final byte[] encrypted = Arrays.copyOfRange(value, IV_BYTES, value.length);
            Cipher cipher = Secret.getCipher(ALGORITHM_MODE);
            cipher.init(Cipher.DECRYPT_MODE, KEY, new IvParameterSpec(iv));
            String plainText = new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
            if (plainText.endsWith(MAGIC)) {
                return plainText.substring(0, plainText.length() - MAGIC.length());
            }
            return null;
        } catch (GeneralSecurityException | RuntimeException e) {
            return null;
        }
    }

    private static final SecretKey KEY;

    private static final SecureRandom SR = new SecureRandom();

    static {
        try {
            final KeyGenerator instance = KeyGenerator.getInstance(ALGORITHM);
            KEY = instance.generateKey();
        } catch (NoSuchAlgorithmException e) {
            throw new Error(e);
        }
    }
}
