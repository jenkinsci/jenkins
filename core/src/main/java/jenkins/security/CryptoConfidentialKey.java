package jenkins.security;

import hudson.util.Secret;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * {@link ConfidentialKey} that stores a {@link SecretKey} for shared-secret cryptography (AES).
 *
 * @author Kohsuke Kawaguchi
 * @since 1.498
 */
public class CryptoConfidentialKey extends ConfidentialKey {
    private volatile SecretKey secret;
    public CryptoConfidentialKey(String id) {
        super(id);
    }

    public CryptoConfidentialKey(Class owner, String shortName) {
        this(owner.getName()+'.'+shortName);
    }

    private SecretKey getKey() {
        try {
            if (secret==null) {
                synchronized (this) {
                    if (secret==null) {
                        byte[] payload = load();
                        if (payload==null) {
                            payload = ConfidentialStore.get().randomBytes(256);
                            store(payload);
                        }
                        // Due to the stupid US export restriction JDK only ships 128bit version.
                        secret = new SecretKeySpec(payload,0,128/8, ALGORITHM);
                    }
                }
            }
            return secret;
        } catch (IOException e) {
            throw new Error("Failed to load the key: "+getId(),e);
        }
    }

    /**
     * Returns a {@link Cipher} object for encrypting with this key.
     */
    public Cipher encrypt() {
        try {
            Cipher cipher = Secret.getCipher(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, getKey());
            return cipher;
        } catch (GeneralSecurityException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * Returns a {@link Cipher} object for decrypting with this key.
     */
    public Cipher decrypt() {
        try {
            Cipher cipher = Secret.getCipher(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, getKey());
            return cipher;
        } catch (GeneralSecurityException e) {
            throw new AssertionError(e);
        }
    }


    private static final String ALGORITHM = "AES";
}
