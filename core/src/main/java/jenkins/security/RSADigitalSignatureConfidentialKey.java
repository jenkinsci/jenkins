package jenkins.security;

import hudson.remoting.Base64;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;

/**
 * RSA digital signature as {@link ConfidentialKey} to prevent accidental leak of private key.
 *
 * @author Kohsuke Kawaguchi
 */
public class RSADigitalSignatureConfidentialKey extends RSAConfidentialKey {
    public RSADigitalSignatureConfidentialKey(String id) {
        super(id);
    }

    public RSADigitalSignatureConfidentialKey(Class owner, String shortName) {
        super(owner, shortName);
    }

    /**
     * Sign a message and base64 encode the signature.
     */
    public String sign(String msg) throws GeneralSecurityException, IOException {
        RSAPrivateKey key = getPrivateKey();
        Signature sig = Signature.getInstance(SIGNING_ALGORITHM + "with" + key.getAlgorithm());
        sig.initSign(key);
        sig.update(msg.getBytes("UTF8"));
        return Base64.encode(sig.sign());
    }

    static final String SIGNING_ALGORITHM = "SHA256";
}
