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
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;

/**
 * A versioned key-value store with change events, which holds files that would otherwise live under
 * {@code JENKINS_HOME} so that several controllers can share them.
 *
 * <p>Keys are paths relative to {@link jenkins.model.Jenkins#getRootDir}, using {@code /} as the separator,
 * for example {@code jobs/folder/jobs/job/config.xml}. Which files are stored is decided by core, not by the store.
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
     * Expected version meaning "overwrite whatever is there, if anything".
     */
    long ANY = -1;

    /**
     * Expected version meaning "only create; fail if the key already exists".
     */
    long ABSENT = 0;

    /**
     * Expected version, in a {@link Precondition} only, meaning "the key exists, at whatever version".
     */
    long PRESENT = -2;

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
     * @param expectedVersion {@link #ANY}, {@link #ABSENT}, or the version the caller last saw
     * @return the version of the entry after the write
     * @throws ConflictException if {@code expectedVersion} does not match the stored version
     */
    long write(@NonNull String key, @NonNull byte[] content, long expectedVersion) throws IOException;

    /**
     * Deletes an entry if it exists. Deleting an absent entry does nothing, whatever {@code expectedVersion} is: the
     * caller wanted it gone, and it is.
     *
     * @param expectedVersion {@link #ANY} or the version the caller last saw
     * @throws ConflictException if {@code expectedVersion} does not match the stored version
     */
    void delete(@NonNull String key, long expectedVersion) throws IOException;

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
     * @param preconditions versions expected of other keys (or of keys being moved, at their source keys)
     * @return each entry moved: its key and version before, and its key and version now
     * @throws ConflictException if a precondition fails, or {@code toPrefix} or anything below it already exists
     */
    @NonNull
    List<Moved> move(@NonNull String fromPrefix, @NonNull String toPrefix, @NonNull List<Precondition> preconditions) throws IOException;

    /**
     * A version a key must have for a {@link #move} to happen.
     *
     * @param expectedVersion {@link #ANY}, {@link #ABSENT}, {@link #PRESENT}, or a version
     */
    record Precondition(@NonNull String key, long expectedVersion) {}

    /**
     * An entry {@linkplain #move moved}: from {@code from} at {@code fromVersion} to {@code to} at {@code version}.
     */
    record Moved(@NonNull String from, long fromVersion, @NonNull String to, long version) {}

    /**
     * Whether confidential keys are shared through this store, as {@code secrets/<id>}, so that every controller uses
     * the same ones (see {@link jenkins.security.ConfidentialStore}). They stay encrypted with the master key, which is
     * never shared. This states the store's security posture: a store not meant to hold key material, even encrypted,
     * does not opt in.
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
