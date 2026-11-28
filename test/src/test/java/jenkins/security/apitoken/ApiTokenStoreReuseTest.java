/*
 * The MIT License
 *
 * Copyright (c) 2026, Aitor Lopez
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

package jenkins.security.apitoken;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.Util;
import hudson.model.User;
import hudson.util.Secret;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import jenkins.model.Jenkins;
import jenkins.security.ApiTokenProperty;
import jenkins.security.apitoken.ApiTokenPolicyViolationException.Reason;
import jenkins.security.apitoken.ApiTokenStore.HashedToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * The maximum lifetime of API tokens bounds token entries, not secrets: a client that registers a fixed token can
 * register the same 32-hex secret again before each expiry, and every registration creates an entry with a new
 * expiration, so the secret never has to be replaced. While the maximum applies to the owner of the token, the
 * store refuses a secret that one of its entries holds already, expired or not. These tests cover that refusal at
 * the level of the store, where it must be atomic: the endpoint decides when it applies and is tested in
 * {@code ApiTokenPropertyTest}.
 */
@Issue("JENKINS-27612")
@WithJenkins
class ApiTokenStoreReuseTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";
    private static final String TOKEN = "11" + SECRET;
    private static final String OTHER_SECRET = "fedcba9876543210fedcba9876543210";
    private static final String OTHER_TOKEN = "11" + OTHER_SECRET;

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    /** The store of a real user, which has its {@link ApiTokenProperty}. */
    private ApiTokenStore storeOfAUser() {
        User user = User.getById("alice", true);
        ApiTokenProperty property = user.getProperty(ApiTokenProperty.class);
        assertNotNull(property);
        return property.getTokenStore();
    }

    private static byte[] hashOf(String secret) throws NoSuchAlgorithmException {
        return MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.US_ASCII));
    }

    private static HashedToken entry(ApiTokenStore store, String uuid) {
        return store.getTokenListSortedByName().stream()
                .filter(token -> token.getUuid().equals(uuid))
                .findFirst()
                .orElseThrow();
    }

    private static long entriesWithSecret(ApiTokenStore store, String secret) throws NoSuchAlgorithmException {
        byte[] hash = hashOf(secret);
        return store.getTokenListSortedByName().stream().filter(token -> token.hasSameSecret(hash)).count();
    }

    @Test
    void hasSameSecretIgnoresTheExpirationAndTheVersion() throws Exception {
        ApiTokenStore store = storeOfAUser();
        HashedToken expired = entry(store, store.addFixedNewToken("expired", TOKEN, LocalDate.now().minusDays(1)));
        HashedToken active = entry(store, store.addFixedNewToken("active", OTHER_TOKEN, LocalDate.now().plusDays(10)));
        HashedToken never = entry(store, store.addFixedNewToken("never", "11" + "ab".repeat(16), null));
        byte[] secretHash = hashOf(SECRET);
        byte[] otherHash = hashOf(OTHER_SECRET);

        assertFalse(expired.match(secretHash), "an expired entry cannot authenticate");
        assertTrue(expired.hasSameSecret(secretHash), "but it holds the secret");
        assertFalse(expired.hasSameSecret(otherHash));
        assertTrue(active.match(otherHash));
        assertTrue(active.hasSameSecret(otherHash));
        assertFalse(active.hasSameSecret(secretHash));
        assertTrue(never.hasSameSecret(hashOf("ab".repeat(16))));
        assertFalse(never.hasSameSecret(secretHash));

        // the version is not compared: the hash of a legacy token is the same SHA-256 of the 32 hex characters of its value
        String legacySecret = "a legacy secret";
        String legacyValue = Util.getDigestOf(legacySecret);
        store.regenerateTokenFromLegacy(Secret.fromString(legacySecret));
        HashedToken legacy = store.getLegacyToken();
        assertNotNull(legacy);
        assertTrue(legacy.isLegacy());
        HashedToken fixedWithTheLegacyValue = entry(store, store.addFixedNewToken("fixed", "11" + legacyValue, null));
        assertFalse(fixedWithTheLegacyValue.isLegacy());
        byte[] legacyHash = hashOf(legacyValue);
        assertTrue(legacy.hasSameSecret(legacyHash));
        assertTrue(fixedWithTheLegacyValue.hasSameSecret(legacyHash));
        assertFalse(legacy.hasSameSecret(secretHash));
        assertFalse(fixedWithTheLegacyValue.hasSameSecret(secretHash));
    }

    /**
     * A stored hash that is not hexadecimal (a hand-edited file, for example) holds no secret that anyone can submit:
     * like {@link HashedToken#match}, {@link HashedToken#hasSameSecret} says no and does not throw, and the entry does not
     * stop a secret from being registered.
     */
    @ParameterizedTest
    @ValueSource(strings = {"zz", "abc", ""})
    void hasSameSecretIsFalseForAStoredHashThatCannotBeDecoded(String storedHash) throws Exception {
        ApiTokenStore store = new ApiTokenStore();
        store.addFixedNewToken("corrupted", TOKEN, null);
        String xml = Jenkins.XSTREAM2.toXML(store);
        assertThat(xml, containsString("<hash>"));
        ApiTokenStore corrupted = (ApiTokenStore) Jenkins.XSTREAM2.fromXML(xml.replaceAll("<hash>[0-9a-f]+</hash>", "<hash>" + storedHash + "</hash>"));
        assertThat(corrupted.getTokenListSortedByName(), hasSize(1));
        HashedToken entry = corrupted.getTokenListSortedByName().iterator().next();

        assertFalse(entry.hasSameSecret(hashOf(SECRET)));

        corrupted.addFixedNewTokenRejectingKnownSecret("new", TOKEN, null);
        assertThat(corrupted.getTokenListSortedByName(), hasSize(2));
    }

    @Test
    void theRejectingEntryPointAcceptsANewSecretAndRejectsAHeldOne() throws Exception {
        ApiTokenStore store = storeOfAUser();

        String uuid = store.addFixedNewTokenRejectingKnownSecret("first", TOKEN, LocalDate.now().plusDays(30));
        assertEquals(1, entriesWithSecret(store, SECRET));
        String otherUuid = store.addFixedNewTokenRejectingKnownSecret("other", OTHER_TOKEN, null);
        assertNotEquals(uuid, otherUuid);
        assertThat(store.getTokenListSortedByName(), hasSize(2));

        ApiTokenPolicyViolationException e = assertThrows(ApiTokenPolicyViolationException.class,
                () -> store.addFixedNewTokenRejectingKnownSecret("again", TOKEN, LocalDate.now().plusDays(30)));
        assertEquals(Reason.SECRET_ALREADY_REGISTERED, e.getReason());
        assertThat("the message is for logs, it must not contain the secret", e.getMessage(), not(containsString(SECRET)));
        assertTrue(e instanceof IllegalArgumentException);
        assertThat("nothing is stored", store.getTokenListSortedByName(), hasSize(2));
        assertEquals(1, entriesWithSecret(store, SECRET));

        // whichever the letter case of the hexadecimal digits
        assertThrows(ApiTokenPolicyViolationException.class,
                () -> store.addFixedNewTokenRejectingKnownSecret("upper", "11" + SECRET.toUpperCase(Locale.ROOT), null));
        assertThat(store.getTokenListSortedByName(), hasSize(2));
    }

    @Test
    void theRejectingEntryPointRejectsAnExpiredEntryToo() throws Exception {
        ApiTokenStore store = storeOfAUser();
        HashedToken expired = entry(store, store.addFixedNewToken("expired", TOKEN, LocalDate.now().minusDays(1)));
        assertTrue(expired.isExpired());

        ApiTokenPolicyViolationException e = assertThrows(ApiTokenPolicyViolationException.class,
                () -> store.addFixedNewTokenRejectingKnownSecret("again", TOKEN, LocalDate.now().plusDays(30)));
        assertEquals(Reason.SECRET_ALREADY_REGISTERED, e.getReason());
        assertThat(store.getTokenListSortedByName(), hasSize(1));
    }

    @Test
    void theRejectingEntryPointRejectsTheValueOfTheLegacyToken() {
        ApiTokenStore store = storeOfAUser();
        String legacySecret = "another legacy secret";
        store.regenerateTokenFromLegacy(Secret.fromString(legacySecret));
        assertThat(store.getTokenListSortedByName(), hasSize(1));

        ApiTokenPolicyViolationException e = assertThrows(ApiTokenPolicyViolationException.class,
                () -> store.addFixedNewTokenRejectingKnownSecret("legacy", "11" + Util.getDigestOf(legacySecret), null));
        assertEquals(Reason.SECRET_ALREADY_REGISTERED, e.getReason());
        assertThat(store.getTokenListSortedByName(), hasSize(1));
    }

    /**
     * The format is validated first, with the exceptions that {@link ApiTokenStore#addFixedNewToken} throws, even if the
     * secret is held: only the {@link ApiTokenPolicyViolationException} tells that the secret is known.
     */
    @Test
    void theRejectingEntryPointValidatesTheFormatBeforeLookingForTheSecret() {
        ApiTokenStore store = storeOfAUser();
        store.addFixedNewToken("first", TOKEN, null);

        for (String malformed : List.of("10" + SECRET, "12" + SECRET, TOKEN + "0", "11" + SECRET.substring(1), "11" + "g".repeat(32), "")) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> store.addFixedNewTokenRejectingKnownSecret("malformed", malformed, null), malformed);
            assertEquals(IllegalArgumentException.class, e.getClass(), "a format error, not a policy violation: " + malformed);
        }
        assertThat(store.getTokenListSortedByName(), hasSize(1));
    }

    @Test
    void theOrdinaryAddFixedNewTokenStillAcceptsAHeldSecret() throws Exception {
        ApiTokenStore store = storeOfAUser();

        String first = store.addFixedNewToken("first", TOKEN, LocalDate.now().plusDays(30));
        String second = store.addFixedNewToken("second", TOKEN, null);
        String third = store.addFixedNewToken("third", "11" + SECRET.toUpperCase(Locale.ROOT), LocalDate.now().minusDays(1));

        assertEquals(3, store.getTokenListSortedByName().size());
        assertEquals(3, List.of(first, second, third).stream().distinct().count());
        assertEquals(3, entriesWithSecret(store, SECRET));
    }

    /**
     * S12 atomic-check-and-insert. Given a user who has an {@link ApiTokenProperty} and no entry for the secret, when
     * several threads call the rejecting entry point with that secret at the same time, then exactly one uuid is
     * returned, the others get the {@link ApiTokenPolicyViolationException}, and the store holds one entry for the secret.
     */
    @Test
    void s12_checkAndInsertAreAtomic() throws Exception {
        ApiTokenStore store = storeOfAUser();
        int threads = 16;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            // a few rounds, each with a secret of its own, to give a race the chance to show
            for (int round = 0; round < 10; round++) {
                String secret = String.format("%032x", round + 1);
                assertEquals(0, entriesWithSecret(store, secret));
                CountDownLatch ready = new CountDownLatch(threads);
                CountDownLatch start = new CountDownLatch(1);
                List<Future<String>> results = new ArrayList<>();
                for (int i = 0; i < threads; i++) {
                    String name = "round-" + round + "-thread-" + i;
                    results.add(executor.submit(() -> {
                        ready.countDown();
                        start.await();
                        try {
                            return store.addFixedNewTokenRejectingKnownSecret(name, "11" + secret, LocalDate.now().plusDays(30));
                        } catch (ApiTokenPolicyViolationException e) {
                            assertEquals(Reason.SECRET_ALREADY_REGISTERED, e.getReason());
                            return null;
                        }
                    }));
                }
                assertTrue(ready.await(30, TimeUnit.SECONDS), "all the threads are waiting");
                start.countDown();

                List<String> uuids = new ArrayList<>();
                for (Future<String> result : results) {
                    String uuid = result.get(30, TimeUnit.SECONDS);
                    if (uuid != null) {
                        uuids.add(uuid);
                    }
                }
                assertThat("round " + round, uuids, hasSize(1));
                assertEquals(1, entriesWithSecret(store, secret), "round " + round);
                assertTrue(entry(store, uuids.get(0)).hasSameSecret(hashOf(secret)), "the uuid that was returned is the entry that holds the secret");
            }
            assertThat(store.getTokenListSortedByName(), hasSize(10));
        } finally {
            executor.shutdownNow();
        }
    }
}
