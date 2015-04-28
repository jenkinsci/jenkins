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

import java.io.UnsupportedEncodingException;
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
    public String sign(String msg) {
        try {
            RSAPrivateKey key = getPrivateKey();
            Signature sig = Signature.getInstance(SIGNING_ALGORITHM + "with" + key.getAlgorithm());
            sig.initSign(key);
            sig.update(msg.getBytes("UTF-8"));
            return hudson.remoting.Base64.encode(sig.sign());
        } catch (GeneralSecurityException e) {
            throw new SecurityException(e);
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError(e);    // UTF-8 is mandatory
        }
    }

    static final String SIGNING_ALGORITHM = "SHA256";
}
