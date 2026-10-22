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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import jenkins.model.Jenkins;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Resolving the store while extensions load.
 */
@WithJenkins
class PersistenceStoreResolutionTest {

    /** Counted down by a thread loading extensions, once it is about to ask for the store. */
    static final CountDownLatch LOADING = new CountDownLatch(1);

    @Test
    void aProviderLoadingExtensionsWhileAThreadLoadingThemAsksForTheStoreDoesNotDeadlock(JenkinsRule r) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            // one thread resolves the store, and its provider loads extensions
            Future<PersistenceStore> resolving = pool.submit(PersistenceStores::get);
            // meanwhile another, loading extensions (as one whose extension reads its files), asks for the store
            Future<PersistenceStore> loading = pool.submit(() -> {
                Provider.CREATING.await();
                synchronized (ExtensionList.loadLock(Jenkins.get())) {
                    LOADING.countDown();
                    return PersistenceStores.get();
                }
            });
            PersistenceStore store = resolving.get(30, TimeUnit.SECONDS);
            assertNotNull(store);
            assertSame(store, loading.get(30, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
    }

    /** Not looked up before the store is resolved. */
    public interface Unloaded extends ExtensionPoint {}

    @TestExtension
    public static final class Provider implements PersistenceStoreProvider {
        static final CountDownLatch CREATING = new CountDownLatch(1);

        @Override
        public PersistenceStore create() {
            CREATING.countDown();
            try {
                // lets the other thread take the lock loading extensions takes, if it can
                LOADING.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            ExtensionList.lookup(Unloaded.class).size();
            return new InMemoryPersistenceStore();
        }
    }
}
