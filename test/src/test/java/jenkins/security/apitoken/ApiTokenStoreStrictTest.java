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
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

import hudson.Util;
import hudson.model.User;
import hudson.util.Secret;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import jenkins.security.ApiTokenProperty;
import jenkins.security.apitoken.ApiTokenPolicyViolationException.Reason;
import jenkins.security.apitoken.ApiTokenStore.HashedToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LogRecorder;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.mockito.MockedStatic;

/**
 * The strict mode of the maximum lifetime of API tokens, at the level of the store: while it is in effect, the Java API
 * cannot create a token that never expires or that outlasts the maximum, nobody is exempt, and a fixed token cannot
 * register a secret that the store holds. Without it, none of that is checked and the store behaves as it always did.
 * <p>
 * The store has no owner and no clock of its own for this: the public methods read the configuration and the day once, before
 * they lock the store, and these tests call the overloads that take the day, so that the clock does not decide anything.
 * The endpoints, which decide what applies to their owner before they call the store, are tested in {@code ApiTokenPropertyTest}.
 */
@Issue("JENKINS-27612")
@WithJenkins
class ApiTokenStoreStrictTest {

    /** Far from the clock, so that a test that uses the clock by mistake does not pass. */
    private static final LocalDate TODAY = LocalDate.of(2030, 6, 15);

    private static final int MAX = 30;

    private static final String SECRET = "0123456789abcdef0123456789abcdef";
    private static final String TOKEN = "11" + SECRET;
    private static final String OTHER_TOKEN = "11fedcba9876543210fedcba9876543210";

    private final LogRecorder logging = new LogRecorder().record(ApiTokenStore.class, Level.WARNING).capture(10);

    private JenkinsRule j;
    private ApiTokenPropertyConfiguration config;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
        config = ApiTokenPropertyConfiguration.get();
    }

    /** The two entry points that register a fixed token, which differ only when the strict mode is not in effect. */
    private enum Variant {
        PLAIN {
            @Override
            String add(ApiTokenStore store, String token, LocalDate expiration, LocalDate today) {
                return store.addFixedNewToken("fixed", token, expiration, today);
            }
        },
        REJECTING {
            @Override
            String add(ApiTokenStore store, String token, LocalDate expiration, LocalDate today) {
                return store.addFixedNewTokenRejectingKnownSecret("fixed", token, expiration, today);
            }
        };

        abstract String add(ApiTokenStore store, String token, LocalDate expiration, LocalDate today);
    }

    private void switchStrictModeOn() {
        config.setMaxTokenLifetimeDays(MAX);
        config.setStrict(true);
    }

    /** The store of a real user, which has its {@link ApiTokenProperty}. */
    private static ApiTokenStore storeOfAUser(String id) {
        ApiTokenProperty property = User.getById(id, true).getProperty(ApiTokenProperty.class);
        assertNotNull(property);
        return property.getTokenStore();
    }

    private static ApiTokenStore storeOfAUser() {
        return storeOfAUser("alice");
    }

    private static int size(ApiTokenStore store) {
        return store.getTokenListSortedByName().size();
    }

    private static HashedToken entry(ApiTokenStore store, String uuid) {
        return store.getTokenListSortedByName().stream()
                .filter(token -> token.getUuid().equals(uuid))
                .findFirst()
                .orElseThrow();
    }

    private static Reason reasonOf(Runnable creation) {
        return assertThrows(ApiTokenPolicyViolationException.class, creation::run).getReason();
    }

    @Test
    void aRandomTokenThatNeverExpiresIsRefusedInStrictMode() {
        switchStrictModeOn();
        ApiTokenStore store = storeOfAUser();

        ApiTokenPolicyViolationException e = assertThrows(ApiTokenPolicyViolationException.class, () -> store.generateNewToken("never", null, TODAY));
        assertEquals(Reason.NEVER_EXPIRES, e.getReason());
        assertTrue(e instanceof IllegalArgumentException);
        assertThat("the message is fixed text, it contains nothing that the caller sent", e.getMessage(), not(containsString("never")));
        // the public methods read the clock: that does not change the answer
        assertEquals(Reason.NEVER_EXPIRES, reasonOf(() -> store.generateNewToken("never")));
        assertEquals(Reason.NEVER_EXPIRES, reasonOf(() -> store.generateNewToken("never", null)));
        assertEquals(0, size(store), "nothing is stored");
    }

    @Test
    void aRandomTokenMustExpireNoLaterThanTodayPlusTheMaximum() {
        switchStrictModeOn();
        ApiTokenStore store = storeOfAUser();

        for (LocalDate expiration : List.of(TODAY, TODAY.plusDays(1), TODAY.plusDays(MAX), TODAY.minusDays(1))) {
            String uuid = store.generateNewToken("accepted", expiration, TODAY).tokenUuid;
            assertEquals(expiration, entry(store, uuid).getExpirationDate(),
                    "the day itself, the last day that is allowed and, as for the endpoints, the past is not the concern of the policy");
        }
        assertEquals(4, size(store));

        for (LocalDate expiration : List.of(TODAY.plusDays(MAX + 1), TODAY.plusYears(1), TODAY.plusDays(Integer.MAX_VALUE))) {
            assertEquals(Reason.OVER_MAXIMUM, reasonOf(() -> store.generateNewToken("refused", expiration, TODAY)), expiration.toString());
        }
        assertEquals(4, size(store), "nothing is stored when a token is refused");

        // the clock decides for the methods without a day: their maximum reaches from today
        LocalDate now = LocalDate.now();
        assertEquals(now.plusDays(10), entry(store, store.generateNewToken("accepted", now.plusDays(10)).tokenUuid).getExpirationDate());
        assertEquals(Reason.OVER_MAXIMUM, reasonOf(() -> store.generateNewToken("refused", now.plusDays(MAX + 5))));
        assertEquals(5, size(store));
    }

    @ParameterizedTest
    @EnumSource(Variant.class)
    void aFixedTokenThatNeverExpiresIsRefusedInStrictMode(Variant variant) {
        switchStrictModeOn();
        ApiTokenStore store = storeOfAUser();

        ApiTokenPolicyViolationException e = assertThrows(ApiTokenPolicyViolationException.class, () -> variant.add(store, TOKEN, null, TODAY));
        assertEquals(Reason.NEVER_EXPIRES, e.getReason());
        assertThat("the message contains no secret", e.getMessage(), not(containsString(SECRET)));
        assertEquals(0, size(store), "nothing is stored");
    }

    @ParameterizedTest
    @EnumSource(Variant.class)
    void aFixedTokenMustExpireNoLaterThanTodayPlusTheMaximum(Variant variant) {
        switchStrictModeOn();
        ApiTokenStore store = storeOfAUser();

        String last = variant.add(store, TOKEN, TODAY.plusDays(MAX), TODAY);
        assertEquals(TODAY.plusDays(MAX), entry(store, last).getExpirationDate(), "today plus the maximum is the last day that is allowed");
        String past = variant.add(store, OTHER_TOKEN, TODAY.minusDays(1), TODAY);
        assertEquals(TODAY.minusDays(1), entry(store, past).getExpirationDate());
        assertEquals(2, size(store));

        String third = "11" + "ab".repeat(16);
        assertEquals(Reason.OVER_MAXIMUM, reasonOf(() -> variant.add(store, third, TODAY.plusDays(MAX + 1), TODAY)));
        assertEquals(Reason.OVER_MAXIMUM, reasonOf(() -> variant.add(store, third, TODAY.plusYears(5), TODAY)));
        assertEquals(2, size(store), "nothing is stored when a token is refused");
    }

    /**
     * S11 strict-store-throws. Given the strict mode is in effect and an expiration that respects the maximum, when
     * {@code addFixedNewToken} is called, with the overload that has a fixed day, for a secret that the store holds, then
     * {@link ApiTokenPolicyViolationException} is thrown with the reason {@link Reason#SECRET_ALREADY_REGISTERED}, even by the
     * method that does not refuse a known secret when the strict mode is not in effect, and nothing is stored. Without the
     * condition on the expiration, the lifetime is what would be refused first.
     */
    @ParameterizedTest
    @EnumSource(Variant.class)
    void s11_aSecretThatTheStoreHoldsIsRefusedInStrictMode(Variant variant) {
        ApiTokenStore store = storeOfAUser();
        final String held = store.addFixedNewToken("held", TOKEN, TODAY.plusDays(5), TODAY);
        // an entry that has expired for the clock too, whatever day the test passes to the store
        LocalDate longAgo = LocalDate.of(2020, 1, 1);
        String expired = store.addFixedNewToken("expired", OTHER_TOKEN, longAgo, TODAY);
        assertTrue(entry(store, expired).isExpired());
        String legacySecret = "a legacy secret for the strict mode";
        store.regenerateTokenFromLegacy(Secret.fromString(legacySecret));
        String legacyAsFixed = "11" + Util.getDigestOf(legacySecret);
        assertEquals(3, size(store));

        switchStrictModeOn();

        ApiTokenPolicyViolationException e = assertThrows(ApiTokenPolicyViolationException.class, () -> variant.add(store, TOKEN, TODAY.plusDays(MAX), TODAY));
        assertEquals(Reason.SECRET_ALREADY_REGISTERED, e.getReason());
        assertThat(e.getMessage(), not(containsString(SECRET)));
        // whichever the letter case, the expiration of the entry, or the version of the entry
        assertEquals(Reason.SECRET_ALREADY_REGISTERED, reasonOf(() -> variant.add(store, "11" + SECRET.toUpperCase(Locale.ROOT), TODAY, TODAY)));
        assertEquals(Reason.SECRET_ALREADY_REGISTERED, reasonOf(() -> variant.add(store, OTHER_TOKEN, TODAY.plusDays(1), TODAY)), "an expired entry holds its secret");
        assertEquals(Reason.SECRET_ALREADY_REGISTERED, reasonOf(() -> variant.add(store, legacyAsFixed, TODAY.plusDays(1), TODAY)), "so does the legacy token");
        assertEquals(3, size(store), "nothing is stored");
        assertEquals(TODAY.plusDays(5), entry(store, held).getExpirationDate(), "the entry was not renewed");
        assertEquals(longAgo, entry(store, expired).getExpirationDate());

        // the secrets that the store does not hold are accepted
        String fresh = variant.add(store, "11" + "cd".repeat(16), TODAY.plusDays(MAX), TODAY);
        assertEquals(4, size(store));
        assertNotNull(entry(store, fresh));
        // and other users do not matter: the store is the one of one user
        variant.add(storeOfAUser("bob"), TOKEN, TODAY.plusDays(MAX), TODAY);
    }

    /**
     * The checks run in a fixed order: the format of the value, which is not a policy violation, then the expiration, then the
     * secret.
     */
    @ParameterizedTest
    @EnumSource(Variant.class)
    void theChecksOfAFixedTokenRunInOrderFormatLifetimeSecret(Variant variant) {
        ApiTokenStore store = storeOfAUser();
        store.addFixedNewToken("held", TOKEN, TODAY.plusDays(5), TODAY);
        switchStrictModeOn();

        // a malformed value that would also be refused for its expiration is a format error, as before
        for (String malformed : List.of("10" + SECRET, "12" + SECRET, TOKEN + "0", "11" + SECRET.substring(1), "11" + "g".repeat(32), "")) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> variant.add(store, malformed, null, TODAY), malformed);
            assertEquals(IllegalArgumentException.class, e.getClass(), "a format error, not a policy violation: " + malformed);
            assertEquals(IllegalArgumentException.class,
                    assertThrows(IllegalArgumentException.class, () -> variant.add(store, malformed, TODAY.plusYears(1), TODAY), malformed).getClass());
        }
        // a secret that is held and an expiration that is not allowed: the expiration is what is refused
        assertEquals(Reason.NEVER_EXPIRES, reasonOf(() -> variant.add(store, TOKEN, null, TODAY)));
        assertEquals(Reason.OVER_MAXIMUM, reasonOf(() -> variant.add(store, TOKEN, TODAY.plusDays(MAX + 1), TODAY)));
        // a secret that is held and an allowed expiration: the secret
        assertEquals(Reason.SECRET_ALREADY_REGISTERED, reasonOf(() -> variant.add(store, TOKEN, TODAY.plusDays(MAX), TODAY)));
        assertEquals(1, size(store));
    }

    /**
     * Whatever the strict mode would refuse, nothing is checked while it is not in effect: it is switched off, there is no
     * maximum, or the maximum is only announced. The plain method does not look at the secrets either, and the one that does
     * refuses only a secret that is held.
     */
    @ParameterizedTest
    @ValueSource(strings = {"strict is off", "no maximum", "warning phase"})
    void nothingIsCheckedWhenTheStrictModeIsNotInEffect(String scenario) {
        switch (scenario) {
            case "strict is off" -> config.setMaxTokenLifetimeDays(MAX);
            case "no maximum" -> config.setStrict(true);
            case "warning phase" -> {
                switchStrictModeOn();
                config.setEnforceFrom(TODAY.plusDays(1).toString());
            }
            default -> throw new AssertionError(scenario);
        }
        ApiTokenStore store = storeOfAUser();

        assertNull(entry(store, store.generateNewToken("never", null, TODAY).tokenUuid).getExpirationDate());
        assertNull(entry(store, store.generateNewToken("never").tokenUuid).getExpirationDate());
        LocalDate far = TODAY.plusYears(50);
        assertEquals(far, entry(store, store.generateNewToken("far", far, TODAY).tokenUuid).getExpirationDate());

        assertNull(entry(store, store.addFixedNewToken("never", TOKEN, null, TODAY)).getExpirationDate());
        assertEquals(far, entry(store, store.addFixedNewToken("far", OTHER_TOKEN, far, TODAY)).getExpirationDate());
        // a secret that is held is accepted again, as it always was, and by the other method it is not, with or without a maximum
        store.addFixedNewToken("again", TOKEN, null, TODAY);
        assertEquals(6, size(store));
        ApiTokenPolicyViolationException e = assertThrows(ApiTokenPolicyViolationException.class, () -> store.addFixedNewTokenRejectingKnownSecret("again", TOKEN, null, TODAY));
        assertEquals(Reason.SECRET_ALREADY_REGISTERED, e.getReason());
        store.addFixedNewTokenRejectingKnownSecret("new", "11" + "ef".repeat(16), far, TODAY);
        assertEquals(7, size(store));
        assertThat("nothing was refused", logging.getMessages(), empty());
    }

    /**
     * The strict mode is in effect from the enforcement date on, that day included: before it, it is only announced.
     */
    @Test
    void strictModeStartsOnTheEnforcementDate() {
        switchStrictModeOn();
        config.setEnforceFrom(TODAY.plusDays(1).toString());
        ApiTokenStore store = storeOfAUser();

        store.generateNewToken("announced", null, TODAY);
        store.generateNewToken("announced", TODAY.plusYears(1), TODAY);
        assertEquals(Reason.NEVER_EXPIRES, reasonOf(() -> store.generateNewToken("started", null, TODAY.plusDays(1))), "the date is the first day");
        assertEquals(Reason.OVER_MAXIMUM, reasonOf(() -> store.generateNewToken("started", TODAY.plusDays(1 + MAX + 1), TODAY.plusDays(1))));
        store.generateNewToken("started", TODAY.plusDays(1 + MAX), TODAY.plusDays(1));
        assertEquals(Reason.NEVER_EXPIRES, reasonOf(() -> store.generateNewToken("later", null, TODAY.plusDays(2))));

        config.setEnforceFrom(TODAY.toString());
        assertEquals(Reason.NEVER_EXPIRES, reasonOf(() -> store.generateNewToken("started", null, TODAY)));
        config.setEnforceFrom(null);
        assertEquals(Reason.NEVER_EXPIRES, reasonOf(() -> store.generateNewToken("started", null, TODAY)));
        config.setEnforceFrom("");
        assertEquals(Reason.NEVER_EXPIRES, reasonOf(() -> store.generateNewToken("started", null, TODAY)));
    }

    /**
     * A stored enforcement date that cannot be parsed counts as an enforcement that has started, so the strict mode applies
     * from the start in that case too.
     */
    @Test
    void anUnparsableEnforcementDateMakesTheStrictModeApplyFromTheStart() {
        switchStrictModeOn();
        config.setEnforceFrom("not-a-date-for-the-strict-store");
        ApiTokenStore store = storeOfAUser();

        assertEquals(Reason.NEVER_EXPIRES, reasonOf(() -> store.generateNewToken("never", null, TODAY)));
        assertEquals(Reason.NEVER_EXPIRES, reasonOf(() -> store.generateNewToken("never", null, TODAY.minusYears(50))));
        assertEquals(Reason.OVER_MAXIMUM, reasonOf(() -> store.generateNewToken("over", TODAY.plusDays(MAX + 1), TODAY)));
        store.generateNewToken("within", TODAY.plusDays(MAX), TODAY);
        assertEquals(1, size(store));
    }

    /**
     * Strict mode reads the configuration, which exists once the extensions are loaded. Where it cannot be looked up there is
     * nothing to ask, so the store behaves as if the strict mode was off, and says so, rather than failing to create a token.
     */
    @Test
    void aConfigurationThatIsNotAvailableYetCountsAsStrictModeOff() {
        ApiTokenStore store = new ApiTokenStore();

        try (MockedStatic<ApiTokenPropertyConfiguration> mocked = mockStatic(ApiTokenPropertyConfiguration.class)) {
            // the extension list has not been loaded yet: no instance of the configuration
            mocked.when(ApiTokenPropertyConfiguration::get).thenReturn(null);
            store.generateNewToken("early", null, TODAY);
            store.addFixedNewToken("early fixed", TOKEN, null, TODAY);
            store.addFixedNewTokenRejectingKnownSecret("early rejecting", OTHER_TOKEN, null, TODAY);
            assertThat(logging.getMessages(), hasItem(containsString("not available yet")));

            // there is no Jenkins yet
            mocked.when(ApiTokenPropertyConfiguration::get).thenThrow(new IllegalStateException("Jenkins.instance is missing"));
            store.generateNewToken("earlier", null, TODAY);
            store.addFixedNewToken("earlier fixed", "11" + "ab".repeat(16), null, TODAY);
        }
        assertEquals(5, size(store));
    }

    /**
     * Existing legacy tokens are migrated when a user is loaded, and that must never throw: the migration is not a creation
     * that the strict mode covers, whatever the store is asked to refuse.
     */
    @Test
    void migrationOfAnExistingLegacyTokenNeverThrowsInStrictMode() throws Exception {
        config.setTokenGenerationOnCreationEnabled(true);
        User user = User.getById("legacy", true);
        ApiTokenProperty property = user.getProperty(ApiTokenProperty.class);
        ApiTokenStore store = property.getTokenStore();
        assertTrue(property.hasLegacyToken());
        // as the user was before the token store existed: the legacy secret is there, its entry is not
        store.revokeAllTokens();
        assertNull(store.getLegacyToken());
        user.save();

        switchStrictModeOn();
        user.load();

        ApiTokenProperty loaded = user.getProperty(ApiTokenProperty.class);
        assertNotNull(loaded.getTokenStore().getLegacyToken(), "the legacy token is migrated again, it is still there");
        assertTrue(loaded.hasLegacyToken());
        // setUser asks for the same, for a store that has the legacy token already, and for one that has not
        loaded.getTokenStore().regenerateTokenFromLegacyIfRequired(Secret.fromString("whatever"));
        ApiTokenStore withoutLegacy = new ApiTokenStore();
        withoutLegacy.regenerateTokenFromLegacyIfRequired(Secret.fromString("whatever"));
        assertNotNull(withoutLegacy.getLegacyToken());
    }

    /**
     * A user that is created while {@code tokenGenerationOnCreationEnabled} is on gets a legacy token that never expires, and
     * the strict mode does not cover it, which is why the configuration page warns about that option. Nor does it cover a direct
     * call to the legacy regeneration of the store.
     */
    @Test
    void anAutoCreatedLegacyTokenAndTheDirectLegacyRegenerationAreNotCoveredInStrictMode() {
        config.setTokenGenerationOnCreationEnabled(true);
        switchStrictModeOn();

        User newcomer = User.getById("newcomer", true);
        ApiTokenProperty property = newcomer.getProperty(ApiTokenProperty.class);
        assertTrue(property.hasLegacyToken());
        HashedToken legacy = property.getTokenStore().getLegacyToken();
        assertNotNull(legacy);
        assertNull(legacy.getExpirationDate());

        ApiTokenStore store = storeOfAUser("other");
        store.regenerateTokenFromLegacy(Secret.fromString("a legacy secret that is regenerated in strict mode"));
        assertNotNull(store.getLegacyToken());
        assertNull(store.getLegacyToken().getExpirationDate());
    }

    /**
     * {@code JenkinsRule.createApiToken}, which the test harness uses to authenticate with a token, generates a token that never
     * expires: it fails on an instance where the strict mode is in effect, and works where it is not.
     */
    @Test
    void theTokenOfTheTestHarnessIsRefusedOnlyInStrictMode() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        User user = User.getById("harness", true);

        j.createWebClient().withBasicApiToken(user);

        switchStrictModeOn();
        assertEquals(Reason.NEVER_EXPIRES,
                assertThrows(ApiTokenPolicyViolationException.class, () -> j.createWebClient().withBasicApiToken(user)).getReason());
    }
}
