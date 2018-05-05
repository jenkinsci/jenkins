/*
 * The MIT License
 *
 * Copyright (c) 2015, CloudBees, Inc.
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
package jenkins.security;

import org.apache.commons.codec.binary.Base64;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;

/**
 * RSA public/private key pair as {@link ConfidentialKey}.
 *
 * <p>
 * As per the design principle of {@link ConfidentialKey}, not exposing {@link PrivateKey} directly.
 * Define subtypes for different use cases.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class RSAConfidentialKey extends ConfidentialKey {
    private RSAPrivateKey priv;
    private RSAPublicKey pub;

    public RSAConfidentialKey(String id) {
        super(id);
    }

    public RSAConfidentialKey(Class owner, String shortName) {
        this(owner.getName() + '.' + shortName);
    }

    /**
     * Obtains the private key (lazily.)
     * <p>
     * This method is not publicly exposed as per the design principle of {@link ConfidentialKey}.
     * Instead of exposing private key, define methods that use them in specific way, such as
     * {@link RSADigitalSignatureConfidentialKey}.
     *
     * @throws Error
     *      If key cannot be loaded for some reasons, we fail.
     */
    protected synchronized RSAPrivateKey getPrivateKey() {
        try {
            if (priv == null) {
                byte[] payload = load();
                if (payload == null) {
                    KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
                    gen.initialize(2048, new SecureRandom()); // going beyond 2048 requires crypto extension
                    KeyPair keys = gen.generateKeyPair();
                    priv = (RSAPrivateKey) keys.getPrivate();
                    pub = (RSAPublicKey) keys.getPublic();
                    store(priv.getEncoded());
                } else {
                    KeyFactory keyFactory = KeyFactory.getInstance("RSA");
                    priv = (RSAPrivateKey) keyFactory.generatePrivate(new PKCS8EncodedKeySpec(payload));

                    RSAPrivateCrtKey pks = (RSAPrivateCrtKey) priv;
                    pub = (RSAPublicKey) keyFactory.generatePublic(
                            new RSAPublicKeySpec(pks.getModulus(), pks.getPublicExponent()));
                }
            }
            return priv;
        } catch (IOException e) {
            throw new Error("Failed to load the key: " + getId(), e);
        } catch (GeneralSecurityException e) {
            throw new Error("Failed to load the key: " + getId(), e);
        }
    }

    public RSAPublicKey getPublicKey() {
        getPrivateKey();
        return pub;
    }

    /**
     * Gets base64-encoded public key.
     */
    public String getEncodedPublicKey() {
        return new String(Base64.encodeBase64(getPublicKey().getEncoded()));
    }
}
