/*
 * The MIT License
 *
 * Copyright (c) 2016, CloudBees Inc.
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
package jenkins.model.identity;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.security.interfaces.DSAPrivateKey;
import java.security.interfaces.DSAPublicKey;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

/**
 * A source of instance identity.
 *
 * @param <PUB>  the type of public key.
 * @param <PRIV> the type of private key.
 * @since FIXME
 */
public abstract class InstanceIdentityProvider<PUB extends PublicKey, PRIV extends PrivateKey> implements
        ExtensionPoint {
    /**
     * RSA keys.
     */
    public static final KeyTypes<RSAPublicKey, RSAPrivateKey> RSA =
            new KeyTypes<>(RSAPublicKey.class, RSAPrivateKey.class);
    /**
     * DSA keys.
     */
    public static final KeyTypes<DSAPublicKey, DSAPrivateKey> DSA =
            new KeyTypes<>(DSAPublicKey.class, DSAPrivateKey.class);
    /**
     * EC keys
     */
    public static final KeyTypes<ECPublicKey, ECPrivateKey> EC =
            new KeyTypes<>(ECPublicKey.class, ECPrivateKey.class);

    /**
     * Gets the {@link KeyPair} that comprises the instance identity.
     *
     * @return the {@link KeyPair} that comprises the instance identity. {@code null} could technically be returned in
     * the event that a keypair could not be generated, for example if the specific key type of this provider
     * is not permitted at the required length by the JCA policy.
     */
    @CheckForNull
    public abstract KeyPair getKeyPair();

    /**
     * Shortcut to {@link KeyPair#getPublic()}.
     *
     * @return the public key. {@code null} if {@link #getKeyPair()} is {@code null}.
     */
    @CheckForNull
    public PUB getPublicKey() {
        KeyPair keyPair = getKeyPair();
        return keyPair == null ? null : (PUB) keyPair.getPublic();
    }

    /**
     * Shortcut to {@link KeyPair#getPrivate()}.
     *
     * @return the private key. {@code null} if {@link #getKeyPair()} is {@code null}.
     */
    @CheckForNull
    public PRIV getPrivateKey() {
        KeyPair keyPair = getKeyPair();
        return keyPair == null ? null : (PRIV) keyPair.getPrivate();
    }

    /**
     * Gets the self-signed {@link X509Certificate} that is associated with this identity. The certificate
     * will must be currently valid. Repeated calls to this method may result in new certificates being generated.
     *
     * @return the certificate. {@code null} if {@link #getKeyPair()} is {@code null}.
     */
    @CheckForNull
    public abstract X509Certificate getCertificate();

    /**
     * Gets the provider of the required identity type.
     *
     * @param type   the type of keys.
     * @param <PUB>  the type of public key.
     * @param <PRIV> the type of private key.
     * @return the provider or {@code null} if no provider of the specified type is available.
     */
    @CheckForNull
    @SuppressWarnings("unchecked")
    public static <PUB extends PublicKey, PRIV extends PrivateKey> InstanceIdentityProvider<PUB, PRIV> get(
            @Nonnull KeyTypes<PUB, PRIV> type) {
        for (InstanceIdentityProvider provider : ExtensionList.lookup(InstanceIdentityProvider.class)) {
            KeyPair keyPair = provider.getKeyPair();
            if (keyPair != null
                    && type.pubKeyType.isInstance(keyPair.getPublic())
                    && type.privKeyType.isInstance(keyPair.getPrivate())) {
                return (InstanceIdentityProvider<PUB, PRIV>) provider;
            }
        }
        return null;
    }

    /**
     * Holds information about the paired keytypes that can be used to form the various identity keys.
     *
     * @param <PUB>  the type of public key.
     * @param <PRIV> the type of private key.
     */
    public static final class KeyTypes<PUB extends PublicKey, PRIV extends PrivateKey> {
        /**
         * The interface for the public key.
         */
        private final Class<PUB> pubKeyType;
        /**
         * The interface for the private key.
         */
        private final Class<PRIV> privKeyType;

        /**
         * Constructor.
         *
         * @param pubKeyType  the interface for the public key.
         * @param privKeyType the interface for the private key.
         */
        private KeyTypes(Class<PUB> pubKeyType, Class<PRIV> privKeyType) {
            this.pubKeyType = pubKeyType;
            this.privKeyType = privKeyType;
        }

        /**
         * Gets the interface for the public key.
         *
         * @return the interface for the public key.
         */
        public Class<PUB> getPublicKeyClass() {
            return pubKeyType;
        }

        /**
         * Gets the interface for the private key.
         *
         * @return the interface for the private key.
         */
        public Class<PRIV> getPrivateKeyClass() {
            return privKeyType;
        }
    }

}
