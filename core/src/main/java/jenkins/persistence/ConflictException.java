/*
 * The MIT License
 *
 * Copyright (c) 2026, Jenkins project contributors
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

package jenkins.persistence;

import java.io.IOException;
import java.util.OptionalLong;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;

/**
 * Thrown when a conditional change finds a key that does not meet what it {@linkplain PersistenceStore.Expected expected},
 * typically because somebody else modified the entry since the caller last read it.
 *
 * @since TODO
 */
@Restricted(Beta.class)
public class ConflictException extends IOException {

    private final String key;
    private final PersistenceStore.Expected expected;
    /** The version found, or 0 if the key does not exist (versions are positive): not an {@link OptionalLong}, which is not serializable. */
    private final long actualVersion;

    /**
     * @param actualVersion the stored version, or empty if the key does not exist
     */
    public ConflictException(String key, PersistenceStore.Expected expected, OptionalLong actualVersion) {
        super("Conflicting modification of " + key + ": expected " + describe(expected) + " but found "
                + (actualVersion.isPresent() ? "version " + actualVersion.getAsLong() : "nothing"));
        this.key = key;
        this.expected = expected;
        this.actualVersion = actualVersion.orElse(0);
    }

    private static String describe(PersistenceStore.Expected expected) {
        return switch (expected) {
            case PersistenceStore.Expected.Any any -> "anything";
            case PersistenceStore.Expected.Absent absent -> "nothing";
            case PersistenceStore.Expected.Present present -> "an entry";
            case PersistenceStore.Expected.At at -> "version " + at.version();
        };
    }

    public String getKey() {
        return key;
    }

    /** What the change expected of the key. */
    public PersistenceStore.Expected getExpected() {
        return expected;
    }

    /** The version found, or empty if the key does not exist. */
    public OptionalLong getActualVersion() {
        return actualVersion == 0 ? OptionalLong.empty() : OptionalLong.of(actualVersion);
    }
}
