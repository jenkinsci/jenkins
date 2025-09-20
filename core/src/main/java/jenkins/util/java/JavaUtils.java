/*
 * The MIT License
 *
 * Copyright (c) 2018 CloudBees, Inc.
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

package jenkins.util.java;

import hudson.util.VersionNumber;
import io.jenkins.lib.versionnumber.JavaSpecificationVersion;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Utility class for Java environment management and checks.
 * @author Oleg Nenashev
 */
@Restricted(NoExternalUse.class)
public class JavaUtils {

    private JavaUtils() {
        // Cannot construct
    }

    /**
     * Check whether the current JVM is running with Java 8 or below
     * @return {@code true} if it is Java 8 or older version
     * @deprecated because the current version is at least Java 17,
     * this method is redundant.
     */
    @Deprecated(since = "TODO", forRemoval = true)
    public static boolean isRunningWithJava8OrBelow() {
        return false;
    }

    /**
     * Check whether the current JVM is running with Java 9 or above.
     * @return {@code true} if it is Java 9 or above
     * @deprecated because the current version is at least Java 17,
     * this method is redundant.
     */
    @Deprecated(since = "TODO", forRemoval = true)
    public static boolean isRunningWithPostJava8() {
        return true;
    }

    /**
     * Returns the JVM's current version as a {@link VersionNumber} instance.
     */
    public static JavaSpecificationVersion getCurrentJavaRuntimeVersionNumber() {
        return JavaSpecificationVersion.forCurrentJVM();
    }

    /**
     * Returns the JVM's current version as a {@link String}.
     * See <a href="https://openjdk.org/jeps/223">JEP 223</a> for the expected format.
     * <ul>
     *     <li>Until Java 8 included, the expected format should be starting with {@code 1.x}</li>
     *     <li>Starting with Java 9, cf. JEP-223 linked above, the version got simplified in 9.x, 10.x, etc.</li>
     * </ul>
     *
     * @see System#getProperty(String)
     */
    public static String getCurrentRuntimeJavaVersion() {
        Runtime.Version runtimeVersion = Runtime.version();
        return String.valueOf(runtimeVersion.version().get(0));
    }
}
