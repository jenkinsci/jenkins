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
import hudson.remoting.ClassFilter;
import hudson.remoting.ObjectInputStreamEx;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Set;
import jenkins.model.Jenkins;
import jenkins.util.JenkinsJVM;

@VisibleForTesting
class AgentToControllerCallableUtils {

    static void validateType(Type t) {
        validateType(t, new HashSet<>());
    }

    private static void validateType(Type t, Set<Type> validated) {
        if (!validated.add(t)) {
            return; // already checked
        }
        if (t instanceof Class<?> c) {
            if (c.isArray()) {
                validateType(c.componentType(), validated);
            } else if (c.isPrimitive() || c == String.class || c.isEnum()) {
                // OK
            } else if (!Serializable.class.isAssignableFrom(c)) {
                throw new IllegalArgumentException(c + " is not serializable");
            } else if (c.isRecord()) {
                for (var rc : c.getRecordComponents()) {
                    validateType(rc.getGenericType(), validated);
                }
            } else {
                throw new IllegalArgumentException(c + " is not a supported class type");
            }
        } else {
            throw new IllegalArgumentException(t + " is not a known immutable monomorphic type");
        }
    }

    static byte[] serialize(Object o) throws IOException {
        try (var baos = new ByteArrayOutputStream(); var oos = new ObjectOutputStream(baos)) {
            oos.writeObject(o);
            oos.flush();
            return baos.toByteArray();
        }
    }

    @SuppressFBWarnings(value = "OBJECT_DESERIALIZATION", justification = "verified input")
    static Object deserialize(byte[] ser, Class<?> type) throws IOException, ClassNotFoundException {
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

    private AgentToControllerCallableUtils() {}

}
