package jenkins.security;

import hudson.Util;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * {@link ConfidentialKey} that's used for creating a token by hashing some information with secret
 * (such as {@code hash(msg|secret)}).
 *
 * <p>
 * This provides more secure version of it by using HMAC.
 * See <a href="https://rdist.root.org/2009/10/29/stop-using-unsafe-keyed-hashes-use-hmac/">this blog post</a> for background.
 * This implementation also never leaks the secret value to outside, so it makes it impossible
 * for the careless caller to misuse the key (thus protecting ourselves from our own stupidity!)
 *
 * @author Kohsuke Kawaguchi
 * @since 1.498
 */
public class HMACConfidentialKey extends ConfidentialKey {

    private ConfidentialStore lastCS;
    private SecretKey key;
    private Mac mac;
    private final int length;

    /**
     * @param length
     *      Byte length of the HMAC code.
     *      By default we use HMAC-SHA256, which produces 256bit (=32bytes) HMAC,
     *      but if different use cases requires a shorter HMAC, specify the desired length here.
     *      Note that when using {@link #mac(String)}, string encoding causes the length to double.
     *      So if you want to get 16-letter HMAC, you specify 8 here.
     */
    public HMACConfidentialKey(String id, int length) {
        super(id);
        this.length = length;
    }

    /**
     * Calls into {@link #HMACConfidentialKey(String, int)} with the longest possible HMAC length.
     */
    public HMACConfidentialKey(String id) {
        this(id, Integer.MAX_VALUE);
    }

    /**
     * Calls into {@link #HMACConfidentialKey(String, int)} by combining the class name and the shortName
     * as the ID.
     */
    public HMACConfidentialKey(Class owner, String shortName, int length) {
        this(owner.getName() + '.' + shortName, length);
    }

    public HMACConfidentialKey(Class owner, String shortName) {
        this(owner, shortName, Integer.MAX_VALUE);
    }

    /**
     * Computes the message authentication code for the specified byte sequence.
     */
    public synchronized byte[] mac(byte[] message) {
        ConfidentialStore cs = ConfidentialStore.get();
        if (mac == null || cs != lastCS) {
            lastCS = cs;
            mac = createMac();
        }
        return chop(mac.doFinal(message));
    }

    /**
     * Convenience method for verifying the MAC code.
     */
    public boolean checkMac(byte[] message, byte[] mac) {
        return MessageDigest.isEqual(mac(message), mac);
    }

    /**
     * Computes the message authentication code and return it as a string.
     * While redundant, often convenient.
     */
    public String mac(String message) {
        return Util.toHexString(mac(message.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Verifies MAC constructed from {@link #mac(String)}
     */
    public boolean checkMac(String message, String mac) {
        return MessageDigest.isEqual(mac(message).getBytes(StandardCharsets.UTF_8), mac.getBytes(StandardCharsets.UTF_8));
    }

    private byte[] chop(byte[] mac) {
        //don't shorten the mac code on FIPS mode
        //if length supplied is less than original mac code length on FIPS, throw exception
        if (FIPS140.useCompliantAlgorithms() && length < mac.length) {
            throw new IllegalArgumentException("Supplied length can't be less than " + mac.length + " on FIPS mode");
        }
        if (mac.length <= length)  return mac; // already too short

        byte[] b = new byte[length];
        System.arraycopy(mac, 0, b, 0, b.length);
        return b;
    }

    /**
     * Creates a new {@link Mac} object.
     */
    public Mac createMac() {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(getKey());
            return mac;
        } catch (GeneralSecurityException e) {
            // Javadoc says HmacSHA256 must be supported by every Java implementation.
            throw new Error(ALGORITHM + " not supported?", e);
        }
    }

    private synchronized SecretKey getKey() {
        ConfidentialStore cs = ConfidentialStore.get();
        if (key == null || cs != lastCS) {
            lastCS = cs;
            try {
                byte[] encoded = load();
                if (encoded == null) {
                    KeyGenerator kg = KeyGenerator.getInstance(ALGORITHM);
                    SecretKey key = kg.generateKey();
                    store(encoded = key.getEncoded());
                }
                key = new SecretKeySpec(encoded, ALGORITHM);
            } catch (IOException | NoSuchAlgorithmException e) {
                throw new Error("Failed to load the key: " + getId(), e);
            }
        }
        return key;
    }

    private static final String ALGORITHM = "HmacSHA256";
}
