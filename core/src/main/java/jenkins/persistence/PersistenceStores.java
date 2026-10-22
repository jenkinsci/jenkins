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
import hudson.ExtensionList;
import hudson.init.InitMilestone;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;

/**
 * Locates the active {@link PersistenceStore}.
 *
 * @since TODO
 */
@Restricted(Beta.class)
public final class PersistenceStores {

    private static final Logger LOGGER = Logger.getLogger(PersistenceStores.class.getName());

    private static volatile Jenkins resolvedFor;
    private static volatile PersistenceStore resolvedStore;

    private PersistenceStores() {}

    /** Set while this thread resolves the store, so that providers reading files do not recurse. */
    private static final ThreadLocal<Boolean> RESOLVING = new ThreadLocal<>();

    /**
     * The active store, or null if none is configured (or extensions are not yet available).
     * The first non-null store of the {@link PersistenceStoreProvider}s, resolved once.
     */
    @Restricted(Beta.class)
    @CheckForNull
    public static PersistenceStore get() {
        Jenkins j = Jenkins.getInstanceOrNull();
        if (j == null) {
            return null;
        }
        if (resolvedFor == j) {
            return resolvedStore;
        }
        if (j.getInitLevel().compareTo(InitMilestone.EXTENSIONS_AUGMENTED) < 0 || RESOLVING.get() != null) {
            return null;
        }
        RESOLVING.set(Boolean.TRUE);
        try {
            // resolved under the lock that loading extensions takes, not one of our own: a thread holding it (as one
            // making an extension that reads its files) may get here, and a provider may load extensions
            synchronized (ExtensionList.loadLock(j)) {
                if (resolvedFor != j) {
                    PersistenceStore store = null;
                    for (PersistenceStoreProvider provider : ExtensionList.lookup(PersistenceStoreProvider.class)) {
                        store = provider.create();
                        if (store != null) {
                            LOGGER.info(() -> "Using persistence store " + provider.getClass().getName());
                            break;
                        }
                    }
                    resolvedStore = store;
                    resolvedFor = j;
                }
                return resolvedStore;
            }
        } finally {
            RESOLVING.remove();
        }
    }
}
