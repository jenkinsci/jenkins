/*
 * The MIT License
 *
 * Copyright 2026 CloudBees, Inc.
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

package jenkins.agents;

import com.google.common.annotations.VisibleForTesting;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.remoting.Callable;
import hudson.remoting.ClassFilter;
import hudson.remoting.ObjectInputStreamEx;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.concurrent.atomic.AtomicLong;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import jenkins.model.Jenkins;
import jenkins.security.Roles;
import jenkins.util.JenkinsJVM;
import org.jenkinsci.remoting.RoleChecker;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;

/**
 * Command sent from an agent to be run on the controller.
 * Use extreme caution in implementing these (and avoid doing so at all whenever possible).
 * Implement on a {@link Record}.
 * It is recommended that all fields and the return value be of immutable monomorphic types,
 * including {@link Record} or {@link TrustedObject} or {@link EncryptedObject}.
 * @since TODO
 */
@SuppressFBWarnings(value = "OBJECT_DESERIALIZATION", justification = "verified input")
public interface AgentToControllerCallable<V, T extends Throwable> extends Callable<V, T> {

    @Override
    default void checkRoles(RoleChecker checker) throws SecurityException {
        checker.check(this, Roles.MASTER);
    }

    @VisibleForTesting
    static void validateType(Type t) {
        if (t instanceof Class<?> c) {
            if (c.isArray()) {
                validateType(c.componentType());
            } else if (c.isPrimitive() || c == String.class || c.isEnum()) {
                // OK
            } else if (!Serializable.class.isAssignableFrom(c)) {
                throw new IllegalArgumentException(c + " is not serializable");
            } else if (c.isRecord()) {
                for (var rc : c.getRecordComponents()) {
                    validateType(rc.getGenericType());
                }
            } else {
                throw new IllegalArgumentException(c + " is not a supported class type");
            }
        } else if (t instanceof ParameterizedType pt && (pt.getRawType() == TrustedObject.class || pt.getRawType() == EncryptedObject.class)) {
            validateType(pt.getActualTypeArguments()[0]);
        } else {
            throw new IllegalArgumentException(t + " is not a known immutable monomorphic type");
        }
    }

    @VisibleForTesting
    static byte[] serialize(Object o) throws IOException {
        try (var baos = new ByteArrayOutputStream(); var oos = new ObjectOutputStream(baos)) {
            oos.writeObject(o);
            oos.flush();
            return baos.toByteArray();
        }
    }

    private static Object deserialize(byte[] ser, Class<?> type) throws IOException, ClassNotFoundException {
        ClassLoader loader;
        if (JenkinsJVM.isJenkinsJVM()) {
            // All struct types are expected to be defined in plugins.
            loader = Jenkins.get().getPluginManager().uberClassLoader;
        } else {
            // From the agent side, just trust the controller’s class loader mirroring.
            loader = type.getClassLoader();
        }
        try (var bais = new ByteArrayInputStream(ser); var ois = new ObjectInputStreamEx(bais, loader, ClassFilter.STANDARD)) {
            return ois.readObject();
        }
    }

    /**
     * A signed object which may be passed to an agent and back and then used safely from the controller.
     * Any attempt by code running in the agent to construct malicious data will be rejected;
     * the data must have been constructed originally in the controller in the same session.
     * Suitable for use as a field in an {@link AgentToControllerCallable}.
     * @param <T> {@link String}, an (unboxed) primitive type, an {@link Enum}, or a {@link Serializable} {@link Record} or array or {@link TrustedObject} or {@link EncryptedObject} of a supported type
     */
    final class TrustedObject<T> implements Serializable {

        private static final String ALGORITHM = "HmacSHA256";

        private static final Mac MAC;

        static {
            if (JenkinsJVM.isJenkinsJVM()) {
                try {
                    MAC = Mac.getInstance(ALGORITHM);
                    MAC.init(KeyGenerator.getInstance(ALGORITHM).generateKey());
                } catch (GeneralSecurityException x) {
                    throw new AssertionError(x);
                }
            } else {
                MAC = null;
            }
        }

        private static final long serialVersionUID = 1;

        private final Class<?> type;

        private transient T o;

        private final byte[] ser;

        @VisibleForTesting
        final byte[] mac;

        /**
         * Create a trusted object wrapper inside the controller.
         */
        public TrustedObject(T o) {
            JenkinsJVM.checkJenkinsJVM();
            type = o.getClass();
            validateType(type);
            this.o = o;
            try {
                ser = serialize(o);
            } catch (IOException x) {
                throw new RuntimeException(x);
            }
            mac = hash(ser);
        }

        @VisibleForTesting
        TrustedObject(T o, byte[] ser, byte[] mac) {
            this.o = o;
            type = o.getClass();
            this.ser = ser;
            this.mac = mac;
        }

        /**
         * Allows this utility to be used from unit tests as a convenience.
         * Does not check that the code is running inside a Jenkins JVM.
         * The result cannot be serialized, only used locally.
         */
        @Restricted(DoNotUse.class)
        public static <T> TrustedObject<T> forUnitTests(T o) {
            return new TrustedObject<>(o, null, null);
        }

        /**
         * The wrapped object.
         */
        public T o() {
            return o;
        }

        /**
         * Validates that the wrapper was in fact signed by the controller.
         */
        @SuppressWarnings("unchecked")
        private Object readResolve() throws InvalidObjectException {
            if (JenkinsJVM.isJenkinsJVM() && !MessageDigest.isEqual(mac, hash(ser))) {
                throw new SecurityException("Incorrect HMAC");
            }
            try {
                o = (T) deserialize(ser, type);
            } catch (IOException | ClassNotFoundException x) {
                throw new InvalidObjectException(x.toString(), x);
            }
            return this;
        }

        private static synchronized byte[] hash(byte[] ser) {
            return MAC.doFinal(ser);
        }
    }

    /**
     * A encrypted object which may be passed to an agent and back and then used safely from the controller.
     * Any attempt by code running in the agent to construct malicious data will be rejected;
     * the data must have been constructed originally in the controller in the same session.
     * Unlike {@link TrustedObject}, the agent cannot inspect the contents
     * (beyond what it could guess based on serialized size).
     * Suitable for use as a field in an {@link AgentToControllerCallable}.
     * @param <T> {@link String}, an (unboxed) primitive type, an {@link Enum}, or a {@link Serializable} {@link Record} or array or {@link TrustedObject} or {@link EncryptedObject} of a supported type
     */
    @SuppressFBWarnings(value = "DMI_RANDOM_USED_ONLY_ONCE", justification = "used once per JVM, fine")
    final class EncryptedObject<T> implements Serializable {

        private static final String KEY_ALGORITHM = "AES";

        private static final String ALGORITHM = "AES/GCM/NoPadding";

        private static final int KEY_SIZE_BITS = 256;

        private static final int GCM_TAG_BITS = 128;

        private static final SecretKey KEY;

        private static final byte[] IV_PREFIX;

        private static final AtomicLong IV_COUNTER = new AtomicLong();

        static {
            if (JenkinsJVM.isJenkinsJVM()) {
                try {
                    var rng = new SecureRandom();
                    var kg = KeyGenerator.getInstance(KEY_ALGORITHM);
                    kg.init(KEY_SIZE_BITS, rng);
                    KEY = kg.generateKey();
                    IV_PREFIX = new byte[4];
                    rng.nextBytes(IV_PREFIX);
                } catch (GeneralSecurityException x) {
                    throw new AssertionError(x);
                }
            } else {
                KEY = null;
                IV_PREFIX = null;
            }
        }

        private static final long serialVersionUID = 1;

        private final Class<?> type;

        private transient T o;

        private final byte[] data, iv;

        /**
         * Create an encrypted object wrapper inside the controller.
         */
        public EncryptedObject(T o) {
            JenkinsJVM.checkJenkinsJVM();
            type = o.getClass();
            validateType(type);
            this.o = o;
            byte[] ser;
            try {
                ser = serialize(o);
            } catch (IOException x) {
                throw new IllegalArgumentException(x);
            }
            try {
                iv = nextIV();
                var cipher = Cipher.getInstance(ALGORITHM);
                cipher.init(Cipher.ENCRYPT_MODE, KEY, new GCMParameterSpec(GCM_TAG_BITS, iv));
                data = cipher.doFinal(ser);
            } catch (GeneralSecurityException x) {
                throw new AssertionError(x);
            }
        }

        /**
         * The wrapped object.
         * May only be called from inside the controller.
         */
        public T o() {
            JenkinsJVM.checkJenkinsJVM();
            return o;
        }

        /**
         * Validates that the wrapper was in fact signed by the controller.
         */
        @SuppressWarnings("unchecked")
        private Object readResolve() throws ObjectStreamException {
            if (JenkinsJVM.isJenkinsJVM()) {
                try {
                    var cipher = Cipher.getInstance(ALGORITHM);
                    cipher.init(Cipher.DECRYPT_MODE, KEY, new GCMParameterSpec(GCM_TAG_BITS, iv));
                    var ser = cipher.doFinal(data);
                    o = (T) deserialize(ser, type);
                } catch (GeneralSecurityException | IOException | ClassNotFoundException x) {
                    throw new InvalidObjectException(x.toString(), x);
                }
            }
            return this;
        }

        private static byte[] nextIV() {
            long count = IV_COUNTER.getAndIncrement();
            if (count < 0) {
                throw new IllegalStateException("IV counter overflow; the controller must be restarted");
            }
            var iv = new byte[12];
            System.arraycopy(IV_PREFIX, 0, iv, 0, 4);
            ByteBuffer.wrap(iv, 4, 8).putLong(count);
            return iv;
        }
    }

}
