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
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.Util;
import hudson.model.User;
import hudson.util.Secret;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
 * The compliance panel of the maximum lifetime of API tokens looks for secrets that several entries of one store hold, because
 * a secret that is registered again gets a new expiration each time, so the maximum bounds the entries and not the secret.
 * It asks the store which entries share a secret, whether any of them can still authenticate, and it revokes them as a group, in
 * one operation, as it can only do it safely there. It also makes the expiration of an entry earlier, never later. These tests
 * cover those operations of the store, and the accessor that lets the panel scan the entries without sorting them. The days
 * that decide which entries have expired are parameters, so the tests choose them, far from the date of the clock.
 */
@Issue("JENKINS-27612")
@WithJenkins
class ApiTokenStoreSharedSecretsTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";
    private static final String TOKEN = "11" + SECRET;
    private static final String OTHER_TOKEN = "11" + "fedcba9876543210fedcba9876543210";
    private static final String THIRD_TOKEN = "11" + "ab".repeat(16);

    /**
     * The day the tests use for what they ask the store. It is not the day of the clock: the expirations of the entries are
     * relative to it, so a method that read the clock instead of the parameter would not agree with the tests.
     */
    private static final LocalDate TODAY = LocalDate.of(2040, 1, 15);

    private ApiTokenProperty property;
    private ApiTokenStore store;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        User user = User.getById("alice", true);
        property = user.getProperty(ApiTokenProperty.class);
        assertNotNull(property);
        store = property.getTokenStore();
    }

    /** Adds an entry the way scripts do, through the Java API: it does not apply the maximum nor look for a known secret. */
    private String add(String name, String token, LocalDate expiry) throws IOException {
        return property.addFixedNewToken(name, token, expiry);
    }

    private static List<String> uuids(Collection<HashedToken> tokens) {
        return tokens.stream().map(HashedToken::getUuid).toList();
    }

    private HashedToken entry(String uuid) {
        return store.getTokenListSnapshot().stream()
                .filter(token -> token.getUuid().equals(uuid))
                .findFirst()
                .orElseThrow();
    }

    private List<String> storedUuids() {
        return uuids(store.getTokenListSnapshot());
    }

    /**
     * A store of its own, with {@code entries} entries that all have {@link #TOKEN}, but for the stored hash of the entries
     * that {@code replacements} gives, by position, which is replaced as a hand-edited file could have it.
     */
    private static ApiTokenStore storeWithReplacedHashes(int entries, Map<Integer, String> replacements) {
        ApiTokenStore original = new ApiTokenStore();
        for (int i = 0; i < entries; i++) {
            original.addFixedNewToken("entry-" + i, TOKEN, null);
        }
        Matcher hashes = Pattern.compile("<hash>[0-9a-f]+</hash>").matcher(Jenkins.XSTREAM2.toXML(original));
        StringBuilder xml = new StringBuilder();
        int position = 0;
        while (hashes.find()) {
            String replacement = replacements.get(position++);
            hashes.appendReplacement(xml, Matcher.quoteReplacement(replacement == null ? hashes.group() : "<hash>" + replacement + "</hash>"));
        }
        hashes.appendTail(xml);
        ApiTokenStore result = (ApiTokenStore) Jenkins.XSTREAM2.fromXML(xml.toString());
        assertThat(result.getTokenListSnapshot(), hasSize(entries));
        return result;
    }

    /** The legacy token of the store, and the fixed entry that has the same secret as it, as the user registered it with {@code 11} and the value. */
    private record LegacyAndFixed(String legacyUuid, String fixedUuid) {}

    private LegacyAndFixed addLegacyAndFixedWithTheSameSecret(LocalDate fixedExpiry) throws IOException {
        String legacySecret = "a legacy secret";
        store.regenerateTokenFromLegacy(Secret.fromString(legacySecret));
        HashedToken legacy = store.getLegacyToken();
        assertNotNull(legacy);
        String fixedUuid = add("fixed", "11" + Util.getDigestOf(legacySecret), fixedExpiry);
        return new LegacyAndFixed(legacy.getUuid(), fixedUuid);
    }

    // ---- the accessor that does not sort ----

    @Test
    void theSnapshotIsACopyInTheOrderOfInsertionAndIsNotSorted() throws Exception {
        String b = add("b", TOKEN, null);
        String a = add("a", OTHER_TOKEN, null);
        String c = add("C", THIRD_TOKEN, null);

        List<HashedToken> snapshot = store.getTokenListSnapshot();

        assertEquals(List.of(b, a, c), uuids(snapshot));
        assertEquals(List.of(a, b, c), uuids(store.getTokenListSortedByName()), "the other accessor sorts by name, the snapshot does not");

        // it is not backed by the list of the store, in any direction
        snapshot.clear();
        assertEquals(List.of(b, a, c), storedUuids());
        List<HashedToken> before = store.getTokenListSnapshot();
        String d = add("d", "11" + "cd".repeat(16), null);
        assertEquals(List.of(b, a, c), uuids(before));
        assertEquals(List.of(b, a, c, d), storedUuids());
    }

    @Test
    void theSnapshotOfAnEmptyStoreIsEmpty() {
        assertThat(store.getTokenListSnapshot(), empty());
    }

    // ---- hasLiveSharedSecret ----

    @Test
    void hasLiveSharedSecretIsFalseForAnEmptyStore() {
        assertFalse(store.hasLiveSharedSecret(TODAY));
    }

    @Test
    void hasLiveSharedSecretIsTrueForTwoLiveEntriesWithTheSameSecret() throws Exception {
        add("one", TOKEN, TODAY.plusDays(10));
        assertFalse(store.hasLiveSharedSecret(TODAY), "a single entry does not share anything");
        add("two", TOKEN, null);

        assertTrue(store.hasLiveSharedSecret(TODAY));
    }

    @Test
    void hasLiveSharedSecretFindsTheSecretWhateverTheLetterCaseOfTheValueRegistered() throws Exception {
        add("lower", TOKEN, null);
        add("upper", "11" + SECRET.toUpperCase(Locale.ROOT), null);

        assertTrue(store.hasLiveSharedSecret(TODAY));
    }

    @Test
    void hasLiveSharedSecretIsTrueWhenThePairIsNotNextToEachOtherInTheStore() throws Exception {
        add("one", TOKEN, null);
        add("other", OTHER_TOKEN, null);
        add("third", THIRD_TOKEN, null);
        assertFalse(store.hasLiveSharedSecret(TODAY));
        add("two", TOKEN, null);

        assertTrue(store.hasLiveSharedSecret(TODAY));
    }

    @Test
    void hasLiveSharedSecretIsFalseForALiveEntryAndAnExpiredOne() throws Exception {
        add("live", TOKEN, TODAY.plusDays(10));
        add("expired", TOKEN, TODAY.minusDays(1));

        assertFalse(store.hasLiveSharedSecret(TODAY), "the expired entry cannot authenticate, the live entry is not a shared secret");
    }

    @Test
    void hasLiveSharedSecretIsFalseWhenEveryEntryOfTheSecretHasExpired() throws Exception {
        add("expired one", TOKEN, TODAY.minusDays(1));
        add("expired two", TOKEN, TODAY.minusDays(30));
        add("expired three", TOKEN, TODAY.minusDays(100));

        assertFalse(store.hasLiveSharedSecret(TODAY));
    }

    @Test
    void hasLiveSharedSecretIsFalseForDifferentSecrets() throws Exception {
        add("one", TOKEN, null);
        add("two", OTHER_TOKEN, null);
        add("three", THIRD_TOKEN, null);

        assertFalse(store.hasLiveSharedSecret(TODAY));
    }

    @Test
    void hasLiveSharedSecretIsTrueForALegacyTokenAndAFixedTokenWithTheSameSecret() throws Exception {
        LegacyAndFixed pair = addLegacyAndFixedWithTheSameSecret(null);
        assertTrue(entry(pair.legacyUuid()).isLegacy());
        assertFalse(entry(pair.fixedUuid()).isLegacy());

        assertTrue(store.hasLiveSharedSecret(TODAY), "the version is not compared: it is one secret");
    }

    @Test
    void hasLiveSharedSecretIsFalseForALegacyTokenAndAnExpiredFixedToken() throws Exception {
        addLegacyAndFixedWithTheSameSecret(TODAY.minusDays(1));

        assertFalse(store.hasLiveSharedSecret(TODAY));
    }

    /**
     * The day is the one that is given. An entry is valid through its expiration date, and the clock does not matter: the day of
     * the tests is years after the day of the clock.
     */
    @Test
    void hasLiveSharedSecretUsesTheGivenDay() throws Exception {
        add("until today", TOKEN, TODAY);
        add("for five more days", TOKEN, TODAY.plusDays(5));

        assertTrue(store.hasLiveSharedSecret(TODAY), "an entry is valid through its expiration date");
        assertFalse(store.hasLiveSharedSecret(TODAY.plusDays(1)), "the first one has expired, one live entry is left");
        assertFalse(store.hasLiveSharedSecret(TODAY.plusDays(6)));
        assertTrue(store.hasLiveSharedSecret(TODAY.minusDays(100)), "earlier than any expiration");
        // for the real clock, which is years before the days above, both entries are live
        store.getTokenListSnapshot().forEach(token -> assertFalse(token.isExpired()));
    }

    /**
     * A stored hash that cannot be decoded, or that is not the size of a SHA-256, holds no secret that anyone can submit: the
     * entry is ignored, as {@link HashedToken#match} ignores it. Two such entries do not share a secret, even if what they
     * store is the same, and one does not share anything with an entry that has a real secret.
     */
    @ParameterizedTest
    @ValueSource(strings = {"zz", "abc", "", "abcd"})
    void hasLiveSharedSecretIgnoresStoredHashesThatCannotBeDecoded(String storedHash) {
        ApiTokenStore bothCorrupted = storeWithReplacedHashes(2, Map.of(0, storedHash, 1, storedHash));
        assertFalse(bothCorrupted.hasLiveSharedSecret(TODAY));

        ApiTokenStore oneCorrupted = storeWithReplacedHashes(2, Map.of(0, storedHash));
        assertFalse(oneCorrupted.hasLiveSharedSecret(TODAY));

        // the entries that can be decoded are still found, whatever the corrupted entries are
        ApiTokenStore twoGoodOneCorrupted = storeWithReplacedHashes(3, Map.of(1, storedHash));
        assertTrue(twoGoodOneCorrupted.hasLiveSharedSecret(TODAY));
    }

    // ---- getUuidsSharingSecret ----

    @Test
    void getUuidsSharingSecretIsEmptyWhenNothingIsShared() throws Exception {
        assertThat(store.getUuidsSharingSecret().keySet(), empty());
        add("one", TOKEN, null);
        add("two", OTHER_TOKEN, null);
        add("three", THIRD_TOKEN, null);

        assertThat(store.getUuidsSharingSecret().keySet(), empty());
    }

    @Test
    void getUuidsSharingSecretGroupsTheEntriesThatShareASecretLiveOrExpired() throws Exception {
        String a1 = add("a1", TOKEN, null);
        String single = add("single", THIRD_TOKEN, null);
        String b1 = add("b1", OTHER_TOKEN, TODAY.plusDays(3));
        String a2 = add("a2", TOKEN, TODAY.plusDays(10));
        String a3 = add("a3", "11" + SECRET.toUpperCase(Locale.ROOT), TODAY.minusDays(5));
        String b2 = add("b2", OTHER_TOKEN, TODAY.minusDays(1));

        Map<String, List<String>> shared = store.getUuidsSharingSecret();

        assertEquals(Set.of(a1, a2, a3, b1, b2), shared.keySet(), "an entry that is alone is not in the result");
        assertEquals(List.of(a2, a3), shared.get(a1), "the others of the group, never the entry itself");
        assertEquals(List.of(a1, a3), shared.get(a2));
        assertEquals(List.of(a1, a2), shared.get(a3), "an expired member is part of the group");
        assertEquals(List.of(b2), shared.get(b1));
        assertEquals(List.of(b1), shared.get(b2));
        assertFalse(shared.containsKey(single));
    }

    @Test
    void getUuidsSharingSecretGroupsALegacyTokenWithAFixedTokenOfTheSameSecret() throws Exception {
        LegacyAndFixed pair = addLegacyAndFixedWithTheSameSecret(null);
        add("alone", OTHER_TOKEN, null);

        Map<String, List<String>> shared = store.getUuidsSharingSecret();

        assertEquals(Map.of(pair.legacyUuid(), List.of(pair.fixedUuid()), pair.fixedUuid(), List.of(pair.legacyUuid())), shared);
    }

    @Test
    void getUuidsSharingSecretReturnsASnapshotThatNothingChanges() throws Exception {
        String first = add("first", TOKEN, null);
        String second = add("second", TOKEN, null);
        Map<String, List<String>> shared = store.getUuidsSharingSecret();

        assertThrows(UnsupportedOperationException.class, () -> shared.put("other", List.of()));
        assertThrows(UnsupportedOperationException.class, () -> shared.get(first).add("other"));

        String third = add("third", TOKEN, null);
        store.revokeToken(second);
        assertEquals(Map.of(first, List.of(second), second, List.of(first)), shared, "it does not follow the store");
        assertEquals(Map.of(first, List.of(third), third, List.of(first)), store.getUuidsSharingSecret());
    }

    @ParameterizedTest
    @ValueSource(strings = {"zz", "abc", "", "abcd"})
    void getUuidsSharingSecretIgnoresStoredHashesThatCannotBeDecoded(String storedHash) {
        assertThat(storeWithReplacedHashes(2, Map.of(0, storedHash, 1, storedHash)).getUuidsSharingSecret().keySet(), empty());
        assertThat(storeWithReplacedHashes(2, Map.of(0, storedHash)).getUuidsSharingSecret().keySet(), empty());

        ApiTokenStore twoGoodOneCorrupted = storeWithReplacedHashes(3, Map.of(1, storedHash));
        List<String> uuids = uuids(twoGoodOneCorrupted.getTokenListSnapshot());
        assertEquals(Map.of(uuids.get(0), List.of(uuids.get(2)), uuids.get(2), List.of(uuids.get(0))), twoGoodOneCorrupted.getUuidsSharingSecret());
    }

    // ---- revokeLiveEntriesSharingSecretWith ----

    @Test
    void revokeLiveEntriesSharingSecretWithALiveEntryRemovesItAndTheLiveSiblingsAndKeepsTheExpiredOnes() throws Exception {
        String a1 = add("a1", TOKEN, null);
        String b1 = add("b1", OTHER_TOKEN, null);
        String a2 = add("a2", TOKEN, TODAY.plusDays(10));
        String expired = add("expired", TOKEN, TODAY.minusDays(1));
        List<HashedToken> before = store.getTokenListSnapshot();

        List<HashedToken> revoked = store.revokeLiveEntriesSharingSecretWith(a1, TODAY);

        assertEquals(List.of(a1, a2), uuids(revoked));
        assertSame(before.get(0), revoked.get(0), "the entries that were in the store are returned, for the caller to clean up after them");
        assertSame(before.get(2), revoked.get(1));
        assertEquals(List.of(b1, expired), storedUuids(), "the expired sibling and the other secret are kept");
    }

    /** A live row and a sibling that became live or expired do not matter: the target is removed, and so is every live sibling. */
    @Test
    void revokeLiveEntriesSharingSecretWithAnExpiredEntryRemovesItAndTheLiveSiblings() throws Exception {
        String a1 = add("a1", TOKEN, null);
        String expired = add("expired", TOKEN, TODAY.minusDays(1));
        String a2 = add("a2", TOKEN, TODAY.plusDays(10));
        String alsoExpired = add("also expired", TOKEN, TODAY.minusDays(40));
        String b1 = add("b1", OTHER_TOKEN, null);

        List<HashedToken> revoked = store.revokeLiveEntriesSharingSecretWith(expired, TODAY);

        assertEquals(List.of(a1, expired, a2), uuids(revoked));
        assertEquals(List.of(alsoExpired, b1), storedUuids());
    }

    @Test
    void revokeLiveEntriesSharingSecretWithAnExpiredEntryWhoseSiblingsAreExpiredRemovesOnlyThatEntry() throws Exception {
        String expired = add("expired", TOKEN, TODAY.minusDays(1));
        String alsoExpired = add("also expired", TOKEN, TODAY.minusDays(2));

        assertEquals(List.of(expired), uuids(store.revokeLiveEntriesSharingSecretWith(expired, TODAY)));

        assertEquals(List.of(alsoExpired), storedUuids());
    }

    @Test
    void revokeLiveEntriesSharingSecretWithAnEntryThatIsAloneRemovesOnlyThatEntry() throws Exception {
        String alone = add("alone", TOKEN, null);
        String other = add("other", OTHER_TOKEN, null);

        assertEquals(List.of(alone), uuids(store.revokeLiveEntriesSharingSecretWith(alone, TODAY)));

        assertEquals(List.of(other), storedUuids());
    }

    @Test
    void revokeLiveEntriesSharingSecretWithAnUnknownUuidChangesNothing() throws Exception {
        add("a1", TOKEN, null);
        add("a2", TOKEN, null);
        add("b1", OTHER_TOKEN, TODAY.minusDays(1));
        List<String> before = storedUuids();

        assertThat(store.revokeLiveEntriesSharingSecretWith("no such uuid", TODAY), empty());

        assertEquals(before, storedUuids());
        assertThat(new ApiTokenStore().revokeLiveEntriesSharingSecretWith("no such uuid", TODAY), empty());
    }

    @Test
    void revokeLiveEntriesSharingSecretWithUsesTheGivenDay() throws Exception {
        String a1 = add("a1", TOKEN, null);
        String a2 = add("a2", TOKEN, TODAY.plusDays(3));

        // a2 has expired on that day, so it is a sibling that is kept
        assertEquals(List.of(a1), uuids(store.revokeLiveEntriesSharingSecretWith(a1, TODAY.plusDays(4))));
        assertEquals(List.of(a2), storedUuids());
    }

    @Test
    void revokeLiveEntriesSharingSecretWithTheFixedTokenRemovesTheLegacyTokenThatHasTheSameSecret() throws Exception {
        LegacyAndFixed pair = addLegacyAndFixedWithTheSameSecret(null);
        String other = add("other", OTHER_TOKEN, null);

        List<HashedToken> revoked = store.revokeLiveEntriesSharingSecretWith(pair.fixedUuid(), TODAY);

        assertEquals(List.of(pair.legacyUuid(), pair.fixedUuid()), uuids(revoked));
        assertTrue(revoked.get(0).isLegacy(), "the caller has to see that the legacy token is among them");
        assertNull(store.getLegacyToken());
        assertEquals(List.of(other), storedUuids());
    }

    @Test
    void revokeLiveEntriesSharingSecretWithTheLegacyTokenRemovesTheFixedTokenThatHasTheSameSecret() throws Exception {
        LegacyAndFixed pair = addLegacyAndFixedWithTheSameSecret(null);

        List<HashedToken> revoked = store.revokeLiveEntriesSharingSecretWith(pair.legacyUuid(), TODAY);

        assertEquals(List.of(pair.legacyUuid(), pair.fixedUuid()), uuids(revoked));
        assertThat(storedUuids(), empty());
    }

    /**
     * The entries that do not hold a secret that can be decoded hold none to share: one is removed alone, and nothing is
     * removed because another entry stores the same garbage.
     */
    @ParameterizedTest
    @ValueSource(strings = {"zz", "abc", "", "abcd"})
    void revokeLiveEntriesSharingSecretWithAnEntryThatHasNoDecodableHashRemovesOnlyThatEntry(String storedHash) {
        ApiTokenStore corrupted = storeWithReplacedHashes(4, Map.of(0, storedHash, 1, storedHash));
        List<String> uuids = uuids(corrupted.getTokenListSnapshot());

        assertEquals(List.of(uuids.get(0)), uuids(corrupted.revokeLiveEntriesSharingSecretWith(uuids.get(0), TODAY)));

        assertEquals(List.of(uuids.get(1), uuids.get(2), uuids.get(3)), uuids(corrupted.getTokenListSnapshot()));
    }

    /**
     * S14, data part. Given a group of three entries with one secret, two live and one expired, when the group of a live entry is
     * revoked, then both live entries are removed and the expired one stays, so the secret no longer authenticates, and it is
     * still refused when it is registered again, as long as the expired entry is in the store.
     */
    @Test
    void s14_theExpiredSiblingOfARevokedGroupKeepsBlockingTheSecret() throws Exception {
        LocalDate today = LocalDate.now();
        String live1 = add("live 1", TOKEN, null);
        String live2 = add("live 2", TOKEN, today.plusDays(30));
        String expired = add("expired", TOKEN, today.minusDays(1));
        assertNotNull(store.findMatchingToken(TOKEN));

        store.revokeLiveEntriesSharingSecretWith(live1, today);

        assertEquals(List.of(expired), storedUuids());
        assertNull(store.findMatchingToken(TOKEN), "the secret does not authenticate any more");
        ApiTokenPolicyViolationException refused = assertThrows(ApiTokenPolicyViolationException.class,
                () -> store.addFixedNewTokenRejectingKnownSecret("again", TOKEN, today.plusDays(30)));
        assertEquals(Reason.SECRET_ALREADY_REGISTERED, refused.getReason());
        assertFalse(storedUuids().contains(live2));
    }

    /**
     * S15, data part. Given the page showed one entry for a secret, when a sibling for the secret is added and the revocation of
     * the first entry is posted, then the sibling is revoked too: the group is the one in the store when the revocation
     * happens, not the one that was shown.
     */
    @Test
    void s15_theGroupIsTheOneOfTheMomentOfTheRevocation() throws Exception {
        String first = add("first", TOKEN, null);
        assertThat("what the page showed: the secret is not shared", store.getUuidsSharingSecret().keySet(), empty());
        String sibling = add("sibling", TOKEN, null);

        List<HashedToken> revoked = store.revokeLiveEntriesSharingSecretWith(first, TODAY);

        assertEquals(List.of(first, sibling), uuids(revoked));
        assertThat(storedUuids(), empty());
    }

    /**
     * Revoking the group and the {@code addFixedNewTokenRejectingKnownSecret} that follows is the case of a secret that was
     * revoked on purpose: once nothing holds it, expired entries included, the store has forgotten it.
     */
    @Test
    void afterTheLastEntryOfASecretIsRevokedTheSecretCanBeRegisteredAgain() throws Exception {
        String live = add("live", TOKEN, null);
        String expired = add("expired", TOKEN, TODAY.minusDays(1));

        store.revokeLiveEntriesSharingSecretWith(live, TODAY);
        assertThrows(ApiTokenPolicyViolationException.class, () -> store.addFixedNewTokenRejectingKnownSecret("again", TOKEN, null));
        store.revokeLiveEntriesSharingSecretWith(expired, TODAY);

        store.addFixedNewTokenRejectingKnownSecret("again", TOKEN, null);
        assertThat(storedUuids(), hasSize(1));
    }

    /**
     * Several threads revoke the group of the same entry at the same time. The group is removed in one operation, so exactly one
     * of them gets it, all of it, and the others find nothing: none of them sees half of a group.
     */
    @Test
    void concurrentRevocationsOfTheSameGroupRemoveItOnce() throws Exception {
        int threads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            for (int round = 0; round < 10; round++) {
                String secret = String.format("%032x", round + 1);
                List<String> group = new ArrayList<>();
                for (int i = 0; i < 5; i++) {
                    group.add(store.addFixedNewToken("round-" + round + "-entry-" + i, "11" + secret, null));
                }
                String expired = store.addFixedNewToken("round-" + round + "-expired", "11" + secret, LocalDate.now().minusDays(1));
                String target = group.get(round % group.size());

                CountDownLatch ready = new CountDownLatch(threads);
                CountDownLatch start = new CountDownLatch(1);
                List<Future<List<HashedToken>>> results = new ArrayList<>();
                for (int i = 0; i < threads; i++) {
                    results.add(executor.submit(() -> {
                        ready.countDown();
                        start.await();
                        return store.revokeLiveEntriesSharingSecretWith(target, LocalDate.now());
                    }));
                }
                assertTrue(ready.await(30, TimeUnit.SECONDS), "all the threads are waiting");
                start.countDown();

                List<List<String>> nonEmpty = new ArrayList<>();
                for (Future<List<HashedToken>> result : results) {
                    List<String> revoked = uuids(result.get(30, TimeUnit.SECONDS));
                    if (!revoked.isEmpty()) {
                        nonEmpty.add(revoked);
                    }
                }
                assertThat("round " + round, nonEmpty, hasSize(1));
                assertEquals(group, nonEmpty.get(0), "round " + round);
                assertTrue(storedUuids().contains(expired), "round " + round);
                assertFalse(storedUuids().stream().anyMatch(group::contains), "round " + round);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * A thread adds entries with the secret, one after the other, while another one revokes the group of an entry that has it.
     * What the revocation removes is the entries that were there when it ran, so of the entries that were added, those that it
     * removed are the first ones, and the others, which came after it, are all still in the store: nothing is half removed.
     */
    @Test
    void anEntryAddedWhileTheGroupIsRevokedIsEitherRemovedWithItOrKeptWithTheOnesThatCameAfter() throws Exception {
        int added = 300;
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 20; round++) {
                String secret = String.format("%032x", round + 1);
                String target = store.addFixedNewToken("round-" + round + "-target", "11" + secret, null);
                AtomicInteger progress = new AtomicInteger();
                CountDownLatch start = new CountDownLatch(1);

                Future<List<String>> adder = executor.submit(() -> {
                    start.await();
                    List<String> uuids = new ArrayList<>();
                    for (int i = 0; i < added; i++) {
                        uuids.add(store.addFixedNewToken("added-" + i, "11" + secret, null));
                        progress.incrementAndGet();
                    }
                    return uuids;
                });
                Future<List<String>> revoker = executor.submit(() -> {
                    start.await();
                    // let some entries go in first, so that the revocation is in the middle of them
                    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
                    while (progress.get() < 20 && System.nanoTime() < deadline) {
                        Thread.onSpinWait();
                    }
                    return uuids(store.revokeLiveEntriesSharingSecretWith(target, LocalDate.now()));
                });
                start.countDown();

                List<String> addedUuids = adder.get(30, TimeUnit.SECONDS);
                List<String> revoked = revoker.get(30, TimeUnit.SECONDS);

                assertEquals(target, revoked.get(0), "round " + round);
                List<String> revokedAmongAdded = revoked.subList(1, revoked.size());
                assertEquals(addedUuids.subList(0, revokedAmongAdded.size()), revokedAmongAdded,
                        "round " + round + ": the entries that were removed are the first ones that were added");
                List<String> remaining = storedUuids().stream().filter(addedUuids::contains).toList();
                assertEquals(addedUuids.subList(revokedAmongAdded.size(), added), remaining,
                        "round " + round + ": the entries that were added after the revocation are all kept");
                assertFalse(storedUuids().contains(target), "round " + round);
                assertTrue(revokedAmongAdded.size() >= 20, "round " + round + ": the revocation came in the middle");
                // what is left of the round is cleaned up, so that the next round starts from the same store
                for (String uuid : remaining) {
                    store.revokeToken(uuid);
                }
                assertThat(storedUuids(), empty());
            }
        } finally {
            executor.shutdownNow();
        }
    }

    // ---- shortenExpiration ----

    @Test
    void shortenExpirationGivesAnExpirationToAnEntryThatHasNone() throws Exception {
        String uuid = add("never", TOKEN, null);
        assertNull(entry(uuid).getExpirationDate());

        assertTrue(store.shortenExpiration(uuid, TODAY.plusDays(90)));

        assertEquals(TODAY.plusDays(90), entry(uuid).getExpirationDate());
    }

    @Test
    void shortenExpirationMovesALaterExpirationEarlier() throws Exception {
        String uuid = add("later", TOKEN, TODAY.plusDays(400));

        assertTrue(store.shortenExpiration(uuid, TODAY.plusDays(90)));

        assertEquals(TODAY.plusDays(90), entry(uuid).getExpirationDate());
    }

    /** One day earlier is enough, and the new date does not have to be in the future: the entry expires. */
    @Test
    void shortenExpirationAcceptsAnyEarlierDate() throws Exception {
        LocalDate expiry = LocalDate.now().plusDays(10);
        String oneDayEarlier = add("one day earlier", TOKEN, expiry);
        String inThePast = add("in the past", OTHER_TOKEN, expiry);

        assertTrue(store.shortenExpiration(oneDayEarlier, expiry.minusDays(1)));
        assertTrue(store.shortenExpiration(inThePast, LocalDate.now().minusDays(1)));

        assertEquals(expiry.minusDays(1), entry(oneDayEarlier).getExpirationDate());
        assertFalse(entry(oneDayEarlier).isExpired());
        assertTrue(entry(inThePast).isExpired());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 100})
    void shortenExpirationNeverExtendsAndRefusesTheSameDate(int daysAfterTheCurrentExpiration) throws Exception {
        LocalDate expiry = TODAY.plusDays(10);
        String uuid = add("bounded", TOKEN, expiry);

        assertFalse(store.shortenExpiration(uuid, expiry.plusDays(daysAfterTheCurrentExpiration)));

        assertEquals(expiry, entry(uuid).getExpirationDate());
    }

    @Test
    void shortenExpirationOnlyEverMovesTheExpirationEarlier() throws Exception {
        LocalDate expiry = TODAY.plusDays(30);
        String uuid = add("bounded", TOKEN, expiry);

        assertTrue(store.shortenExpiration(uuid, expiry.minusDays(1)));
        assertFalse(store.shortenExpiration(uuid, expiry), "that is the date it had, not an earlier one: it would extend");
        assertTrue(store.shortenExpiration(uuid, expiry.minusDays(2)));
        assertFalse(store.shortenExpiration(uuid, expiry.minusDays(1)));
        assertFalse(store.shortenExpiration(uuid, expiry.minusDays(2)));
        assertFalse(store.shortenExpiration(uuid, expiry.plusDays(5)));

        assertEquals(expiry.minusDays(2), entry(uuid).getExpirationDate());
    }

    @Test
    void shortenExpirationDoesNotChangeTheLegacyToken() throws Exception {
        LegacyAndFixed pair = addLegacyAndFixedWithTheSameSecret(null);

        assertFalse(store.shortenExpiration(pair.legacyUuid(), TODAY.plusDays(90)));
        assertFalse(store.shortenExpiration(pair.legacyUuid(), LocalDate.now().minusDays(1)));

        HashedToken legacy = entry(pair.legacyUuid());
        assertNull(legacy.getExpirationDate(), "a legacy token has no expiration, and does not get one");
        assertFalse(legacy.isExpired());
        assertSame(legacy, store.getLegacyToken());
    }

    @Test
    void shortenExpirationRefusesAnUnknownUuid() throws Exception {
        String uuid = add("known", TOKEN, null);

        assertFalse(store.shortenExpiration("no such uuid", TODAY.plusDays(90)));
        assertFalse(new ApiTokenStore().shortenExpiration("no such uuid", TODAY.plusDays(90)));

        assertNull(entry(uuid).getExpirationDate());
    }

    /** What is shortened is the entry, as the panel does it for the rows that exceed the maximum on their own. */
    @Test
    void shortenExpirationChangesOnlyTheEntryEvenIfItsSecretIsShared() throws Exception {
        String first = add("first", TOKEN, null);
        String sibling = add("sibling", TOKEN, null);

        assertTrue(store.shortenExpiration(first, TODAY.plusDays(90)));

        assertEquals(TODAY.plusDays(90), entry(first).getExpirationDate());
        assertNull(entry(sibling).getExpirationDate());
    }

    /** The expiration that was set is the one that {@code match} and {@code isExpired} use: the entry is valid through it. */
    @Test
    void aShortenedEntryIsValidThroughItsNewExpirationAndExpiredAfterIt() throws Exception {
        LocalDate today = LocalDate.now();
        String uuid = add("shortened", TOKEN, null);
        assertNotNull(store.findMatchingToken(TOKEN));

        assertTrue(store.shortenExpiration(uuid, today));
        assertNotNull(store.findMatchingToken(TOKEN), "valid through its expiration date");
        assertTrue(entry(uuid).isExpired(today.plusDays(1)));
        assertFalse(entry(uuid).isExpired(today));
        assertFalse(entry(uuid).isExpired(today.minusDays(1)));
    }

    /** The overload with a day is the rule of {@code isExpired()}, with the day as a parameter. */
    @Test
    void isExpiredForAGivenDayFollowsTheSameRuleAsIsExpired() throws Exception {
        LocalDate today = LocalDate.now();
        HashedToken never = entry(add("never", TOKEN, null));
        HashedToken expiresToday = entry(add("today", OTHER_TOKEN, today));
        HashedToken expiredYesterday = entry(add("yesterday", THIRD_TOKEN, today.minusDays(1)));

        for (HashedToken token : List.of(never, expiresToday, expiredYesterday)) {
            assertEquals(token.isExpired(), token.isExpired(today), token.getName());
        }
        assertFalse(never.isExpired(LocalDate.MAX), "an entry without expiration never expires");
        assertFalse(expiresToday.isExpired(today));
        assertTrue(expiresToday.isExpired(today.plusDays(1)));
        assertTrue(expiredYesterday.isExpired(today));
    }
}
