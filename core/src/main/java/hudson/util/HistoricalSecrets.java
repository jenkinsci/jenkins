/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi
 * Copyright (c) 2016, CloudBees Inc.
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

import hudson.Util;
import jenkins.model.Jenkins;
import jenkins.security.CryptoConfidentialKey;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Historical algorithms for decrypting {@link Secret}s.
 */
@Restricted(NoExternalUse.class)
public class HistoricalSecrets {

    /*package*/ static Secret decrypt(String data, CryptoConfidentialKey key) throws IOException, GeneralSecurityException {
        byte[] in;
        try {
            in = Base64.getDecoder().decode(data.getBytes(StandardCharsets.UTF_8));
        } catch (IllegalArgumentException ex) {
            throw new IOException("Could not decode secret", ex);
        }
        Secret s = tryDecrypt(key.decrypt(), in);
        if (s!=null)    return s;

        // try our historical key for backward compatibility
        Cipher cipher = Secret.getCipher("AES");
        cipher.init(Cipher.DECRYPT_MODE, getLegacyKey());
        return tryDecrypt(cipher, in);
    }

    /*package*/ static Secret tryDecrypt(Cipher cipher, byte[] in) {
        try {
            String plainText = new String(cipher.doFinal(in), UTF_8);
            if(plainText.endsWith(MAGIC))
                return new Secret(plainText.substring(0,plainText.length()-MAGIC.length()));
            return null;
        } catch (GeneralSecurityException e) {
            return null; // if the key doesn't match with the bytes, it can result in BadPaddingException
        }
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
        if (Secret.SECRET != null) {
            return Util.toAes128Key(Secret.SECRET);
        }
        Jenkins j = Jenkins.getInstanceOrNull();
        if (j != null) {
            return j.getSecretKeyAsAES128();
        } else {
            return Util.toAes128Key("mock");
        }
    }

    static final String MAGIC = "::::MAGIC::::";
}
