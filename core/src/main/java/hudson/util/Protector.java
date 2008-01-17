package hudson.util;

import ch.ethz.ssh2.crypto.Base64;

import javax.crypto.SecretKey;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;

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

    public static String protect(String secret) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, DES_KEY);
            return new String(Base64.encode(cipher.doFinal(secret.getBytes("UTF-8"))));
        } catch (GeneralSecurityException e) {
            throw new Error(e); // impossible
        } catch (UnsupportedEncodingException e) {
            throw new Error(e); // impossible
        }
    }

    public static String unprotect(String data) throws IOException {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, DES_KEY);
            return new String(cipher.doFinal(Base64.decode(data.toCharArray())), "UTF-8");
        } catch (GeneralSecurityException e) {
            throw new Error(e); // impossible
        } catch (UnsupportedEncodingException e) {
            throw new Error(e); // impossible
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
