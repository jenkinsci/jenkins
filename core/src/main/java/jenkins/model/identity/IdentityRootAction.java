package jenkins.model.identity;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.UnprotectedRootAction;
import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import org.jenkinsci.remoting.util.KeyUtils;

/**
 * A simple root action that exposes the public key to users so that they do not need to search for the
 * {@code X-Instance-Identity} response header, also exposes the fingerprint of the public key so that people
 * can verify a fingerprint of a master before connecting to it.
 * <p>Do not use this class from plugins. Depend on {@code instance-identity} directly instead.
 * @since 2.16
 */
@Extension
public class IdentityRootAction implements UnprotectedRootAction {
    @Override
    public String getIconFileName() {
        return null;
    }

    @Override
    public String getDisplayName() {
        return null;
    }

    @Override
    public String getUrlName() {
        return InstanceIdentityProvider.RSA.getKeyPair() == null ? null : "instance-identity";
    }

    /**
     * Returns the PEM encoded public key.
     *
     * @return the PEM encoded public key.
     *         Null if the {@code instance-identity} plugin is not enabled.
     */
    @CheckForNull
    public String getPublicKey() {
        RSAPublicKey key = InstanceIdentityProvider.RSA.getPublicKey();
        if (key == null) {
            return null;
        }
        byte[] encoded = Base64.getEncoder().encode(key.getEncoded());
        int index = 0;
        StringBuilder buf = new StringBuilder(encoded.length + 20);
        while (index < encoded.length) {
            int len = Math.min(64, encoded.length - index);
            if (index > 0) {
                buf.append("\n");
            }
            buf.append(new String(encoded, index, len, StandardCharsets.UTF_8));
            index += len;
        }
        return String.format("-----BEGIN PUBLIC KEY-----%n%s%n-----END PUBLIC KEY-----%n", buf);
    }

    /**
     * Returns the fingerprint of the public key.
     *
     * @return the fingerprint of the public key.
     */
    @NonNull
    public String getFingerprint() {
        return KeyUtils.fingerprint(InstanceIdentityProvider.RSA.getPublicKey());
    }

}
