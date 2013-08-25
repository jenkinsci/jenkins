package hudson.cli.agent;

import com.jcraft.jsch.agentproxy.Buffer;
import com.trilead.ssh2.auth.AgentIdentity;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.DSAPublicKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPublicKeySpec;

/**
 * @author reynald
 */
public class AgentIdentityUtils {

    /**
     * Retrieve the public key from the specified agent identity.
     * <p/>
     * Copied from http://svn.apache.org/repos/asf/mina/sshd/tags/sshd-0.8.0/sshd-core/src/main/java/org/apache/sshd/common/util/Buffer.java#getRawPublicKey()
     *
     * @param identity Agent identity
     * @return Public Key
     * @throws NoSuchAlgorithmException
     * @throws InvalidKeySpecException
     */
    public static PublicKey getPublicKey(final AgentIdentity identity)
            throws NoSuchAlgorithmException, InvalidKeySpecException {

        final Buffer buffer = new Buffer(identity.getPublicKeyBlob());

        final String algorithm = new String(buffer.getString());

        if (algorithm.equals("ssh-rsa")) {
            final BigInteger e = new BigInteger(buffer.getMPInt());
            final BigInteger n = new BigInteger(buffer.getMPInt());
            return KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(n, e));

        } else if (algorithm.equals("ssh-dsa")) {
            final BigInteger p = new BigInteger(buffer.getMPInt());
            final BigInteger q = new BigInteger(buffer.getMPInt());
            final BigInteger g = new BigInteger(buffer.getMPInt());
            final BigInteger y = new BigInteger(buffer.getMPInt());
            return KeyFactory.getInstance("DSA").generatePublic(new DSAPublicKeySpec(y, p, q, g));
        }

        throw new IllegalArgumentException("Unsupported agent algorithm " + algorithm);
    }
}
