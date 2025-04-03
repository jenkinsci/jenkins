/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi
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

package hudson.util;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Util;
import java.io.IOException;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Base64;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.crypto.Cipher;
import jenkins.security.CryptoConfidentialKey;
import jenkins.util.SystemProperties;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.Stapler;

/**
 * Glorified {@link String} that uses encryption in the persisted form, to avoid accidental exposure of a secret.
 *
 * <p>
 * This is not meant as a protection against code running in the same VM, nor against an attacker
 * who has local file system access on Jenkins master.
 *
 * <p>
 * {@link Secret}s can correctly read-in plain text password, so this allows the existing
 * String field to be updated to {@link Secret}.
 *
 * @author Kohsuke Kawaguchi
 */
public final class Secret implements Serializable {
    private static final Logger LOGGER = Logger.getLogger(Secret.class.getName());

    private static final byte PAYLOAD_V1 = 1;
    /**
     * Unencrypted secret text.
     */
    @NonNull
    private final String value;
    private byte[] iv;

    /*package*/ Secret(String value) {
        this.value = value;
    }

    /*package*/ Secret(String value, byte[] iv) {
        this.value = value;
        this.iv = iv;
    }

    /**
     * Obtains the secret in a plain text.
     *
     * @see #getEncryptedValue()
     * @deprecated as of 1.356
     *      Use {@link #toString(Secret)} to avoid NPE in case Secret is null.
     *      Or if you really know what you are doing, use the {@link #getPlainText()} method.
     */
    @Override
    @Deprecated
    public String toString() {
        final String from = new Throwable().getStackTrace()[1].toString();
        LOGGER.warning("Use of toString() on hudson.util.Secret from " + from + ". Prefer getPlainText() or getEncryptedValue() depending your needs. see https://www.jenkins.io/redirect/hudson.util.Secret/");
        return value;
    }

    /**
     * Obtains the plain text password.
     * Before using this method, ask yourself if you'd be better off using {@link Secret#toString(Secret)}
     * to avoid NPE.
     */
    @NonNull
    public String getPlainText() {
        return value;
    }

    @Override
    public boolean equals(Object that) {
        return that instanceof Secret && value.equals(((Secret) that).value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    /**
     * Encrypts {@link #value} and returns it in an encoded printable form.
     *
     * @see #toString()
     */
    public String getEncryptedValue() {
        try {
            synchronized (this) {
                if (iv == null) { //if we were created from plain text or other reason without iv
                    iv = KEY.newIv();
                }
            }
            Cipher cipher = KEY.encrypt(iv);
            byte[] encrypted = cipher.doFinal(this.value.getBytes(UTF_8));
            byte[] payload = new byte[1 + 8 + iv.length + encrypted.length];
            int pos = 0;
            // For PAYLOAD_V1 we use this byte shifting model, V2 probably will need DataOutput
            payload[pos++] = PAYLOAD_V1;
            payload[pos++] = (byte) (iv.length >> 24);
            payload[pos++] = (byte) (iv.length >> 16);
            payload[pos++] = (byte) (iv.length >> 8);
            payload[pos++] = (byte) iv.length;
            payload[pos++] = (byte) (encrypted.length >> 24);
            payload[pos++] = (byte) (encrypted.length >> 16);
            payload[pos++] = (byte) (encrypted.length >> 8);
            payload[pos++] = (byte) encrypted.length;
            System.arraycopy(iv, 0, payload, pos, iv.length);
            pos += iv.length;
            System.arraycopy(encrypted, 0, payload, pos, encrypted.length);
            return "{" + Base64.getEncoder().encodeToString(payload) + "}";
        } catch (GeneralSecurityException e) {
            throw new Error(e); // impossible
        }
    }

    /**
     * Pattern matching a possible output of {@link #getEncryptedValue}
     * Basically, any Base64-encoded value optionally wrapped by {@code {}}.
     * You must then call {@link #decrypt(String)} to eliminate false positives.
     * @see #ENCRYPTED_VALUE_PATTERN
     */
    @Restricted(NoExternalUse.class)
    public static final Pattern ENCRYPTED_VALUE_PATTERN = Pattern.compile("\\{?[A-Za-z0-9+/]+={0,2}}?");

    /**
     * Reverse operation of {@link #getEncryptedValue()}. Returns null
     * if the given cipher text was invalid.
     */
    @CheckForNull
    public static Secret decrypt(@CheckForNull String data) {
        if (!isValidData(data))      return null;

        if (data.startsWith("{") && data.endsWith("}")) { //likely CBC encrypted/containing metadata but could be plain text
            byte[] payload;
            try {
                payload = Base64.getDecoder().decode(data.substring(1, data.length() - 1));
            } catch (IllegalArgumentException e) {
                return null;
            }
            switch (payload[0]) {
                case PAYLOAD_V1:
                    // For PAYLOAD_V1 we use this byte shifting model, V2 probably will need DataOutput
                    int ivLength = ((payload[1] & 0xff) << 24)
                            | ((payload[2] & 0xff) << 16)
                            | ((payload[3] & 0xff) << 8)
                            | (payload[4] & 0xff);
                    int dataLength = ((payload[5] & 0xff) << 24)
                            | ((payload[6] & 0xff) << 16)
                            | ((payload[7] & 0xff) << 8)
                            | (payload[8] & 0xff);
                    if (payload.length != 1 + 8 + ivLength + dataLength) {
                        // not valid v1
                        return null;
                    }
                    byte[] iv = Arrays.copyOfRange(payload, 9, 9 + ivLength);
                    byte[] code = Arrays.copyOfRange(payload, 9 + ivLength, payload.length);
                    String text;
                    try {
                        text = new String(KEY.decrypt(iv).doFinal(code), UTF_8);
                    } catch (GeneralSecurityException e) {
                        // it's v1 which cannot be historical, but not decrypting
                        return null;
                    }
                    return new Secret(text, iv);
                default:
                    return null;
            }
        } else {
            try {
                return HistoricalSecrets.decrypt(data, KEY);
            } catch (UnsupportedEncodingException e) {
                throw new Error(e); // impossible
            } catch (GeneralSecurityException | IOException e) {
                return null;
            }
        }
    }

    private static boolean isValidData(String data) {
        if (data == null || "{}".equals(data) || "".equals(data.trim())) return false;

        if (data.startsWith("{") && data.endsWith("}")) {
            return !"".equals(data.substring(1, data.length() - 1).trim());
        }

        return true;
    }

    /**
     * Workaround for <a href="https://issues.jenkins.io/browse/JENKINS-6459">JENKINS-6459</a> / <a href="https://web.archive.org/web/20110107095054/http://java.net/jira/browse/GLASSFISH-11862">GLASSFISH-11862</a>
     * This method uses specific provider selected via hudson.util.Secret.provider system property
     * to provide a workaround for the above bug where default provide gives an unusable instance.
     * (Glassfish Enterprise users should set value of this property to "SunJCE")
     */
    public static Cipher getCipher(String algorithm) throws GeneralSecurityException {
        return PROVIDER != null ? Cipher.getInstance(algorithm, PROVIDER)
                                : Cipher.getInstance(algorithm);
    }

    /**
     * Attempts to treat the given string first as a cipher text, and if it doesn't work,
     * treat the given string as the unencrypted secret value.
     *
     * <p>
     * Useful for recovering a value from a form field.
     */
    @NonNull
    public static Secret fromString(@CheckForNull String data) {
        data = Util.fixNull(data);
        Secret s = decrypt(data);
        if (s == null) s = new Secret(data);
        return s;
    }

    /**
     * Works just like {@link Secret#toString()} but avoids NPE when the secret is null.
     * To be consistent with {@link #fromString(String)}, this method doesn't distinguish
     * empty password and null password.
     */
    @NonNull
    public static String toString(@CheckForNull Secret s) {
        return s == null ? "" : s.value;
    }

    public static final class ConverterImpl implements Converter {
        public ConverterImpl() {
        }

        @Override
        public boolean canConvert(Class type) {
            return type == Secret.class;
        }

        @Override
        public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
            Secret src = (Secret) source;
            writer.setValue(src.getEncryptedValue());
        }

        @Override
        public Object unmarshal(HierarchicalStreamReader reader, final UnmarshallingContext context) {
            return fromString(reader.getValue());
        }

        private static final long serialVersionUID = 1L;
    }

    /**
     * Workaround for <a href="https://issues.jenkins.io/browse/JENKINS-6459">JENKINS-6459</a> / <a href="https://web.archive.org/web/20110107095054/http://java.net/jira/browse/GLASSFISH-11862">GLASSFISH-11862</a>
     * @see #getCipher(String)
     */
    private static final String PROVIDER = SystemProperties.getString(Secret.class.getName() + ".provider");

    /**
     * For testing only.
     * @deprecated Normally unnecessary.
     */
    @Deprecated
    /*package*/ static String SECRET = null;

    /**
     * The key that encrypts the data on disk.
     */
    private static final CryptoConfidentialKey KEY = new CryptoConfidentialKey(Secret.class.getName());

    private static final long serialVersionUID = 1L;

    @Restricted(NoExternalUse.class)
    public static final boolean AUTO_ENCRYPT_PASSWORD_CONTROL = SystemProperties.getBoolean(Secret.class.getName() + ".AUTO_ENCRYPT_PASSWORD_CONTROL", true);

    @Restricted(NoExternalUse.class)
    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "for script console")
    public static /* non-final */ boolean BLANK_NONSECRET_PASSWORD_FIELDS_WITHOUT_ITEM_CONFIGURE = SystemProperties.getBoolean(Secret.class.getName() + ".BLANK_NONSECRET_PASSWORD_FIELDS_WITHOUT_ITEM_CONFIGURE", true);

    static {
        Stapler.CONVERT_UTILS.register(new org.apache.commons.beanutils.Converter() {
            @Override
            public Secret convert(Class type, Object value) {
                if (value == null) {
                    return null;
                }
                if (value instanceof Secret) {
                    return (Secret) value;
                }
                return Secret.fromString(value.toString());
            }
        }, Secret.class);
        if (AUTO_ENCRYPT_PASSWORD_CONTROL) {
            Stapler.CONVERT_UTILS.register(new org.apache.commons.beanutils.Converter() {
                @Override
                public String convert(Class type, Object value) {
                    if (value == null) {
                        return null;
                    }
                    Secret decrypted = Secret.decrypt(value.toString());
                    if (decrypted == null) {
                        return value.toString();
                    } else {
                        return decrypted.getPlainText();
                    }
                }
            }, String.class);
        }
    }
}
