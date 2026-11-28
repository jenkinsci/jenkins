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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.Consumer;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;

/**
 * A versioned key-value store with change events, which holds files that would otherwise live under
 * {@code JENKINS_HOME} so that several controllers can share them.
 *
 * <p>Keys are paths relative to {@link jenkins.model.Jenkins#getRootDir}, using {@code /} as the separator,
 * for example {@code jobs/folder/jobs/job/config.xml}. Implementors may have specific behaviors on top of this that
 * are not specified here, e.g. a branch named {code feat/redesign} might have {code Job.fullName} of {@code jobs/feat%2Fcool-feature}
 * but store key of {@code jobs/repo/branches/feat-redesign.abc123/config.xml}.
 * Which files are stored is decided by core, not by the store.
 * Keys whose first segment starts with {@code _} (such as {@code _items/<uuid>/…}) are never produced by routing:
 * they are reserved for code that uses the store directly, such as plugins.
 *
 * <p>Every stored entry carries a version. Versions are drawn from one sequence shared by every key: a change made
 * after another one was committed has a higher version than it, whichever keys the two change. So a key's version
 * increases on each modification, also across its deletion and re-creation, and versions of different keys can be
 * compared (that of an entry moved with that of what is made at its old key afterwards, say).
 * Writes and deletes may be conditional on the version the caller last saw.
 *
 * <p>Events: every change made through any instance of the store, including this one, is delivered to
 * {@linkplain #subscribe subscribers} at least once, in a single total order, which is the commit order.
 * A {@link ChangeEvent.Kind#MOVED} event is ordered before any later event under the destination prefix.
 * A store that cannot give that order is not a valid implementation.
 *
 * <p>With no store (no {@link PersistenceStoreProvider} supplies one) Jenkins behaves exactly as without this API.
 *
 * @see PersistenceStoreProvider
 * @since TODO
 */
@Restricted(Beta.class)
public interface PersistenceStore {

    /**
     * What a change expects of a key, which must hold for the change to be made: checked in the same transaction.
     * Which expectations a change accepts is in its type: {@link ForWrite} for {@link PersistenceStore#write}, {@link ForDelete} for
     * {@link PersistenceStore#delete}, and any for a {@link Precondition} of {@link PersistenceStore#move}.
     */
    sealed interface Expected extends Serializable {

        /**
         * Whether a key meets this expectation.
         *
         * @param stored the version stored at the key, or empty if the key does not exist
         */
        boolean isMetBy(@NonNull OptionalLong stored);

        /** An expectation a {@link PersistenceStore#write} accepts. */
        sealed interface ForWrite extends Expected {}

        /** An expectation a {@link PersistenceStore#delete} accepts. */
        sealed interface ForDelete extends ForWrite {}

        /** Whatever is there, if anything: the change is unconditional. See {@link PersistenceStore#ANY}. */
        record Any() implements ForDelete {
            @Override
            public boolean isMetBy(@NonNull OptionalLong stored) {
                return true;
            }
        }

        /** Nothing is there: a write only creates. See {@link PersistenceStore#ABSENT}. */
        record Absent() implements ForWrite {
            @Override
            public boolean isMetBy(@NonNull OptionalLong stored) {
                return stored.isEmpty();
            }
        }

        /** Something is there, at whatever version. See {@link PersistenceStore#PRESENT}. */
        record Present() implements Expected {
            @Override
            public boolean isMetBy(@NonNull OptionalLong stored) {
                return stored.isPresent();
            }
        }

        /**
         * The key is at a version, typically the one the caller last saw: unchanged since.
         *
         * @param version a version from {@link Entry#version}, {@link PersistenceStore#write} or {@link Moved#version}
         */
        record At(long version) implements ForDelete {
            public At {
                if (version <= 0) {
                    throw new IllegalArgumentException("Versions are positive: " + version);
                }
            }

            @Override
            public boolean isMetBy(@NonNull OptionalLong stored) {
                return stored.isPresent() && stored.getAsLong() == version;
            }

            @Override
            public String toString() {
                return Long.toString(version);
            }
        }

        /** The key is at a version, typically the one the caller last saw. */
        static At at(long version) {
            return new At(version);
        }
    }

    /** Singleton of {@link Expected.Any}: overwrite whatever is there, if anything. */
    Expected.Any ANY = new Expected.Any();

    /** Singleton of {@link Expected.Absent}: only create; fail if the key already exists. */
    Expected.Absent ABSENT = new Expected.Absent();

    /** Singleton of {@link Expected.Present}: the key exists, at whatever version. */
    Expected.Present PRESENT = new Expected.Present();

    /**
     * Reads an entry.
     *
     * @return the entry, or null if the key does not exist
     */
    @CheckForNull
    Entry read(@NonNull String key) throws IOException;

    /**
     * Writes an entry.
     * Writing content identical to the stored content must not create a new version.
     *
     * @param expected {@link #ANY}, {@link #ABSENT}, or {@linkplain Expected#at the version} the caller last saw
     * @return the version of the entry after the write
     * @throws ConflictException if the key does not meet {@code expected}
     */
    long write(@NonNull String key, @NonNull byte[] content, @NonNull Expected.ForWrite expected) throws IOException;

    /**
     * Deletes an entry if it exists. Deleting an absent entry does nothing, whatever {@code expected} is: the
     * caller wanted it gone, and it is.
     *
     * @param expected {@link #ANY} or {@linkplain Expected#at the version} the caller last saw
     * @throws ConflictException if the key exists and does not meet {@code expected}
     */
    void delete(@NonNull String key, @NonNull Expected.ForDelete expected) throws IOException;

    /**
     * Lists the names directly below a key, like listing a directory.
     * For example, with entries {@code jobs/a/config.xml} and {@code jobs/f/jobs/b/config.xml},
     * the children of {@code jobs} are {@code a} and {@code f}.
     *
     * @param parentKey a key without trailing {@code /}
     */
    @NonNull
    Set<String> children(@NonNull String parentKey) throws IOException;

    /**
     * Deletes every key below a prefix ({@code prefix/…}), each with its deletion event: for example all the
     * entries kept about a deleted item. Stores should override this to do it at once instead of key by key.
     *
     * @return how many keys were deleted
     */
    default int deleteAll(@NonNull String prefix) throws IOException {
        int n = 0;
        for (String child : children(prefix)) {
            String key = prefix + "/" + child;
            if (read(key) != null) {
                delete(key, ANY);
                n++;
            }
            n += deleteAll(key);
        }
        return n;
    }

    /**
     * Atomically renames a key and every key below it, as when an item directory is renamed or moved, if every
     * precondition holds; all are checked in the same transaction as the move.
     * For example moving {@code jobs/a} to {@code jobs/f/jobs/a} moves {@code jobs/a/config.xml}
     * and {@code jobs/a/jobs/x/config.xml}, but not {@code jobs/ab/config.xml}.
     * Moved entries get new versions. Their change events are delivered shallower keys first, so that an item's
     * configuration arrives before the entries below it.
     *
     * @param preconditions what is expected of other keys (or of keys being moved, at their source keys)
     * @return each entry moved: its key and version before, and its key and version now
     * @throws ConflictException if a precondition fails, or {@code toPrefix} or anything below it already exists
     */
    @NonNull
    List<Moved> move(@NonNull String fromPrefix, @NonNull String toPrefix, @NonNull List<Precondition> preconditions) throws IOException;

    /**
     * What a key must be for a {@link #move} to happen.
     *
     * @param expected {@link #ANY}, {@link #ABSENT}, {@link #PRESENT}, or {@linkplain Expected#at a version}
     */
    record Precondition(@NonNull String key, @NonNull Expected expected) {}

    /**
     * An entry {@linkplain #move moved}: from {@code from} at {@code fromVersion} to {@code to} at {@code version}.
     */
    record Moved(@NonNull String from, long fromVersion, @NonNull String to, long version) {}

    /**
     * Whether confidential keys are shared through this store, as {@code secrets/<id>}, so that every controller uses
     * the same ones (see {@link jenkins.security.ConfidentialStore}). They stay encrypted with the master key
     * ({@code secrets/master.key}), which is never put in the store: every controller sharing the store must be given
     * the same master key another way, such as a mounted secret, as one with a different master key cannot decrypt
     * them. This states the store's security posture: a store not meant to hold key material, even encrypted, does not
     * opt in.
     */
    default boolean storesConfidentialKeys() {
        return false;
    }

    /**
     * Identifies this store instance (and so this controller) as the origin of the changes it makes.
     */
    @NonNull
    String getOrigin();

    /**
     * Receives every change made through any instance of the store, including this one, in the total order described
     * above, at least once, and not lost across reconnects.
     * Listeners should not throw; one which does is logged, and neither stops the others getting the event nor makes
     * the change that caused it fail.
     *
     * @return a handle which unsubscribes when closed
     */
    @NonNull
    AutoCloseable subscribe(@NonNull Consumer<ChangeEvent> listener);

    /**
     * A change to one key.
     *
     * @param kind what happened
     * @param key the key changed; for {@link Kind#MOVED} the new key
     * @param oldKey for {@link Kind#MOVED} the previous key, else null
     * @param version the version after the change; for {@link Kind#DELETED} too
     * @param origin {@link #getOrigin} of the store instance which made the change
     */
    record ChangeEvent(@NonNull Kind kind, @NonNull String key, @CheckForNull String oldKey, long version, @NonNull String origin) {
        public enum Kind { WRITTEN, DELETED, MOVED }
    }

    /**
     * Stored content with its version.
     *
     * @param content the raw bytes
     * @param version a positive number from the store-wide sequence described above
     */
    record Entry(@NonNull byte[] content, long version) {}
}
