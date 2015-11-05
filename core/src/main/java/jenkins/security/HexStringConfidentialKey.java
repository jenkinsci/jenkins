package jenkins.security;

import hudson.Util;

import java.io.IOException;

/**
 * {@link ConfidentialKey} that is the random hexadecimal string of length N.
 *
 * <p>
 * This is typically used as a unique ID, as a hex dump is suitable for printing, copy-pasting,
 * as well as use as an identifier.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.498
 */
public class HexStringConfidentialKey extends ConfidentialKey {
    private final int length;

    private volatile String secret;

    /**
     * @param length
     *      Length of the hexadecimal string.
     */
    public HexStringConfidentialKey(String id, int length) {
        super(id);
        if (length%2!=0)
            throw new IllegalArgumentException("length must be even: "+length);
        this.length = length;
    }

    public HexStringConfidentialKey(Class owner, String shortName, int length) {
        this(owner.getName()+'.'+shortName,length);
    }

    /**
     * Returns the persisted hex string value.
     *
     * If the value isn't persisted, a new random value is created.
     *
     * @throws Error
     *      If the secret fails to load. Not throwing a checked exception is for the convenience
     *      of the caller.
     */
    public String get() {
        try {
            if (secret==null) {
                synchronized (this) {
                    if (secret==null) {
                        byte[] payload = load();
                        if (payload==null) {
                            payload = ConfidentialStore.get().randomBytes(length/2);
                            store(payload);
                        }
                        secret = Util.toHexString(payload).substring(0,length);
                    }
                }
            }
            return secret;
        } catch (IOException e) {
            throw new Error("Failed to load the key: "+getId(),e);
        }
    }
}
