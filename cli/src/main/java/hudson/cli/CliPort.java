package hudson.cli;

import org.apache.commons.codec.binary.Base64;

import java.net.InetSocketAddress;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;

/**
 * @author Kohsuke Kawaguchi
 */
public final class CliPort {
    /**
     * The TCP endpoint to talk to.
     */
    final InetSocketAddress endpoint;

    /**
     * CLI protocol version. 1 and 2 are currently defined.
     */
    final int version;

    /**
     * Server instance identity. Can be null.
     */
    final String identity;

    public CliPort(InetSocketAddress endpoint, String identity, int version) {
        this.endpoint = endpoint;
        this.identity = identity;
        this.version = version;
    }

    /**
     * Gets the public part of the RSA key that represents the server identity.
     */
    public PublicKey getIdentity() throws GeneralSecurityException {
        if (identity==null) return null;
        byte[] image = Base64.decodeBase64(identity);
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(image));
    }
}
