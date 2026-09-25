package jenkins.security;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.util.Secret;
import java.io.IOException;
import java.security.GeneralSecurityException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * {@link ConfidentialKey} that stores a {@link SecretKey} for shared-secret cryptography (AES).
 *
 * @author Kohsuke Kawaguchi
 * @since 1.498
 */
public class CryptoConfidentialKey extends ConfidentialKey {
    @Restricted(NoExternalUse.class) // TODO pending API
    public static final int DEFAULT_IV_LENGTH = 16;

    private ConfidentialStore lastCS;
    private SecretKey secret;

    public CryptoConfidentialKey(String id) {
        super(id);
    }

    public CryptoConfidentialKey(Class owner, String shortName) {
        this(owner.getName() + '.' + shortName);
    }

    private synchronized SecretKey getKey() {
        ConfidentialStore cs = ConfidentialStore.get();
        if (secret == null || cs != lastCS) {
            lastCS = cs;
            try {
                byte[] payload = load();
                if (payload == null) {
                    payload = cs.randomBytes(256);
                    store(payload);
                }
                // Due to the stupid US export restriction JDK only ships 128bit version.
                secret = new SecretKeySpec(payload, 0, 128 / 8, KEY_ALGORITHM);
            } catch (IOException e) {
                throw new Error("Failed to load the key: " + getId(), e);
            }
        }
        return secret;
    }

    /**
     * Returns a {@link Cipher} object for encrypting with this key.
     * @deprecated use {@link #encrypt(byte[])}
     */
    @Deprecated
    public Cipher encrypt() {
        try {
            Cipher cipher = Secret.getCipher(KEY_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, getKey());
            return cipher;
        } catch (GeneralSecurityException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * Returns a {@link Cipher} object for encrypting with this key using the provided initialization vector.
     * @param iv the initialization vector
     * @return the cipher
     */
    @Restricted(NoExternalUse.class) // TODO pending API
    @SuppressFBWarnings(value = "STATIC_IV", justification = "TODO needs triage")
    public Cipher encrypt(byte[] iv) {
        try {
            Cipher cipher = Secret.getCipher(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, getKey(), new IvParameterSpec(iv));
            return cipher;
        } catch (GeneralSecurityException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * Returns a {@link Cipher} object for decrypting with this key using the provided initialization vector.
     * @param iv the initialization vector
     * @return the cipher
     */
    @Restricted(NoExternalUse.class) // TODO pending ApI
    public Cipher decrypt(byte[] iv) {
        try {
            Cipher cipher = Secret.getCipher(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, getKey(), new IvParameterSpec(iv));
            return cipher;
        } catch (GeneralSecurityException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * Generates a new Initialization Vector.
     * @param length the length of the salt
     * @return some random bytes
     * @see #encrypt(byte[])
     */
    @Restricted(NoExternalUse.class) // TODO pending API
    public byte[] newIv(int length) {
        return ConfidentialStore.get().randomBytes(length);
    }

    /**
     * Generates a new Initialization Vector of default length.
     * @return some random bytes
     * @see #newIv(int)
     * @see #encrypt(byte[])
     */
    @Restricted(NoExternalUse.class) // TODO pending API
    public byte[] newIv() {
        return newIv(DEFAULT_IV_LENGTH);
    }

    /**
     * Returns a {@link Cipher} object for decrypting with this key.
     * @deprecated use {@link #decrypt(byte[])}
     */
    @Deprecated
    public Cipher decrypt() {
        try {
            Cipher cipher = Secret.getCipher(KEY_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, getKey());
            return cipher;
        } catch (GeneralSecurityException e) {
            throw new AssertionError(e);
        }
    }


    private static final String KEY_ALGORITHM = "AES";
    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";

}
