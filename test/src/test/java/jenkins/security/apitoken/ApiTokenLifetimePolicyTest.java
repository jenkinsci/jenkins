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
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.User;
import hudson.security.HudsonPrivateSecurityRealm;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.logging.Level;
import jenkins.model.IdStrategy;
import jenkins.security.ApiTokenProperty;
import jenkins.security.apitoken.ApiTokenLifetimePolicy.Phase;
import jenkins.security.apitoken.ApiTokenLifetimePolicy.Violation;
import jenkins.security.apitoken.ApiTokenStore.HashedToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LogRecorder;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.hudson.test.recipes.LocalData;

/**
 * The policy remembers the last invalid enforcement date that it logged in a static field, which outlives a test:
 * every test that asserts that a warning is logged once must use an unparsable value of its own.
 */
@WithJenkins
class ApiTokenLifetimePolicyTest {

    /** Every date-dependent method takes the date as a parameter, so there is no clock to mock. */
    private static final LocalDate TODAY = LocalDate.of(2030, 6, 15);

    private final LogRecorder logging = new LogRecorder()
            .record(ApiTokenLifetimePolicy.class, Level.WARNING)
            .record(ApiTokenPropertyConfiguration.class, Level.WARNING)
            .capture(20);

    private final ApiTokenLifetimePolicy policy = new ApiTokenLifetimePolicy();

    private JenkinsRule j;
    private ApiTokenPropertyConfiguration config;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
        config = ApiTokenPropertyConfiguration.get();
    }

    @Test
    void isActive() {
        assertFalse(policy.isActive(), "no maximum by default");
        config.setMaxTokenLifetimeDays(1);
        assertTrue(policy.isActive());
        config.setMaxTokenLifetimeDays(365);
        assertTrue(policy.isActive());
        config.setMaxTokenLifetimeDays(0);
        assertFalse(policy.isActive());
    }

    @Test
    void isEnforcing() {
        // Inactive: never enforcing, whatever the date
        assertFalse(policy.isEnforcing(TODAY));
        config.setEnforceFrom(TODAY.minusDays(1).toString());
        assertFalse(policy.isEnforcing(TODAY));

        config.setMaxTokenLifetimeDays(30);

        config.setEnforceFrom(null);
        assertTrue(policy.isEnforcing(TODAY), "no date: enforcing right away");

        config.setEnforceFrom(TODAY.plusDays(1).toString());
        assertFalse(policy.isEnforcing(TODAY), "warning phase until the day before");
        assertTrue(policy.isEnforcing(TODAY.plusDays(1)), "the date itself is the first day of enforcement");
        assertTrue(policy.isEnforcing(TODAY.plusDays(2)));

        config.setEnforceFrom(TODAY.toString());
        assertTrue(policy.isEnforcing(TODAY));

        config.setEnforceFrom(TODAY.minusDays(1).toString());
        assertTrue(policy.isEnforcing(TODAY), "a date in the past behaves like no date");

        config.setEnforceFrom("   ");
        assertTrue(policy.isEnforcing(TODAY), "blank is stored as null");
        assertNull(config.getEnforceFrom());

        config.setEnforceFrom(" " + TODAY.plusDays(5) + " ");
        assertEquals(TODAY.plusDays(5).toString(), config.getEnforceFrom(), "the setter trims");
        assertFalse(policy.isEnforcing(TODAY));
    }

    @Test
    void getPendingEnforceFrom() {
        assertNull(policy.getPendingEnforceFrom(TODAY), "inactive");
        config.setEnforceFrom(TODAY.plusDays(3).toString());
        assertNull(policy.getPendingEnforceFrom(TODAY), "inactive, even with a future date");

        config.setMaxTokenLifetimeDays(30);
        assertEquals(TODAY.plusDays(3), policy.getPendingEnforceFrom(TODAY));
        assertNull(policy.getPendingEnforceFrom(TODAY.plusDays(3)), "reached");
        assertNull(policy.getPendingEnforceFrom(TODAY.plusDays(4)), "passed");

        config.setEnforceFrom(null);
        assertNull(policy.getPendingEnforceFrom(TODAY));
        config.setEnforceFrom("not-a-date-for-pending");
        assertNull(policy.getPendingEnforceFrom(TODAY), "unparsable is not pending, it is enforcing");
    }

    @Test
    void unparsableEnforceFromFailsClosedAndIsLoggedOncePerValue() {
        config.setMaxTokenLifetimeDays(30);

        config.setEnforceFrom("not-a-date-one");
        assertTrue(policy.isEnforcing(TODAY));
        assertTrue(policy.isEnforcing(TODAY.minusYears(50)), "enforcing whatever the date");
        assertEquals(1, countWarnings("not-a-date-one"), "logged once for two calls");

        config.setEnforceFrom("not-a-date-two");
        assertTrue(policy.isEnforcing(TODAY));
        assertEquals(1, countWarnings("not-a-date-two"), "a distinct value is logged again");
        assertEquals(1, countWarnings("not-a-date-one"));
    }

    @Test
    void unparsableEnforceFromIsLoggedWithoutControlCharacters() {
        config.setMaxTokenLifetimeDays(30);

        config.setEnforceFrom("not-a-date-ctrl\r\nforged line\tend");
        assertTrue(policy.isEnforcing(TODAY));
        assertTrue(policy.isEnforcing(TODAY), "the raw value is compared, so the second call logs nothing");
        assertEquals(1, countWarnings("not-a-date-ctrl??forged line?end"), "logged once, control characters replaced");
    }

    @Test
    @LocalData
    void unparsableEnforceFromFailsClosed() {
        assertEquals("2026-13-01", config.getEnforceFrom());
        assertTrue(policy.isActive());
        assertTrue(policy.isEnforcing(TODAY));
        assertTrue(policy.isEnforcing(LocalDate.of(2000, 1, 1)));
        assertEquals(1, countWarnings("2026-13-01"), "logged once over two calls");
        assertNull(policy.getPendingEnforceFrom(TODAY));
    }

    @Test
    @LocalData
    void invalidEnforceFromWithoutMaximumIsInert() {
        assertEquals("2027-02-30", config.getEnforceFrom());
        assertFalse(policy.isActive());
        assertFalse(policy.isEnforcing(TODAY));
        assertEquals(0, countWarnings("2027-02-30"), "inert: nothing is parsed, nothing is logged");
        assertNull(policy.validate(User.getById("bob", true), null, TODAY));
    }

    @Test
    @LocalData
    void blankEnforceFromInFileEnforcesWithoutWarning() {
        // Descriptor#load does not go through the setter, which would have stored null
        assertEquals("  ", config.getEnforceFrom());
        assertTrue(policy.isEnforcing(TODAY));
        assertThat(logging.getMessages(), empty());
    }

    @Test
    void isExempt() {
        User alice = User.getById("alice", true);
        assertFalse(policy.isExempt(alice, TODAY), "null list");

        config.setExemptUserIds("");
        assertFalse(policy.isExempt(alice, TODAY), "empty list");

        config.setExemptUserIds(" \n\r\n  \n");
        assertFalse(policy.isExempt(alice, TODAY), "only blank lines");

        config.setExemptUserIds("Alice");
        assertTrue(policy.isExempt(alice, TODAY), "the default strategy ignores the case");
        assertFalse(policy.isExempt(User.getById("alic", true), TODAY), "no partial match");
        assertFalse(policy.isExempt(User.getById("alice2", true), TODAY), "no partial match");

        config.setExemptUserIds(null);
        assertFalse(policy.isExempt(alice, TODAY), "null again");
    }

    @Test
    void exemptUserIdsFormat() {
        config.setExemptUserIds("bob\r\n  carol  \n\n\rService Account\n\tdave\t\n");
        for (String id : List.of("bob", "carol", "Service Account", "dave")) {
            assertTrue(policy.isExempt(User.getById(id, true), TODAY), id);
        }
        assertFalse(policy.isExempt(User.getById("Service", true), TODAY), "IDs with spaces are not split");
        assertFalse(policy.isExempt(User.getById("Account", true), TODAY), "IDs with spaces are not split");
        assertFalse(policy.isExempt(User.getById("bob carol", true), TODAY), "lines are not joined");
        assertEquals(List.of("bob", "carol", "Service Account", "dave"), ApiTokenLifetimePolicy.parseUserIds(config.getExemptUserIds()));
        assertThat(ApiTokenLifetimePolicy.parseUserIds(null), empty());
    }

    @Test
    void isExemptFollowsTheIdStrategy() {
        j.jenkins.setSecurityRealm(new IdStrategySpecifyingSecurityRealm(new IdStrategy.CaseSensitive()));
        config.setExemptUserIds("alice\nBob");
        assertTrue(policy.isExempt(User.getById("alice", true), TODAY));
        assertFalse(policy.isExempt(User.getById("Alice", true), TODAY), "case sensitive");
        assertTrue(policy.isExempt(User.getById("Bob", true), TODAY));
        assertFalse(policy.isExempt(User.getById("bob", true), TODAY), "case sensitive");

        j.jenkins.setSecurityRealm(new IdStrategySpecifyingSecurityRealm(new IdStrategy.CaseInsensitive()));
        assertTrue(policy.isExempt(User.getById("ALICE", true), TODAY));
        assertTrue(policy.isExempt(User.getById("bob", true), TODAY));
    }

    @Test
    @LocalData
    void exemptionsAreReadFromTheStoredList() {
        // Descriptor#load fills the field without calling the setter: nothing can be cached there
        assertThat(ApiTokenLifetimePolicy.parseUserIds(config.getExemptUserIds()), hasSize(3));
        assertTrue(policy.isExempt(User.getById("alice", true), TODAY));
        assertTrue(policy.isExempt(User.getById("BOB", true), TODAY));
        assertTrue(policy.isExempt(User.getById("service account", true), TODAY));
        assertFalse(policy.isExempt(User.getById("carol", true), TODAY));

        // Changing the list takes effect at once
        config.setExemptUserIds("carol");
        assertFalse(policy.isExempt(User.getById("alice", true), TODAY));
        assertTrue(policy.isExempt(User.getById("carol", true), TODAY));
    }

    @Test
    void maxExpiration() {
        config.setMaxTokenLifetimeDays(30);
        assertEquals(LocalDate.of(2030, 7, 15), policy.maxExpiration(TODAY));
        assertEquals(LocalDate.of(2031, 1, 19), policy.maxExpiration(LocalDate.of(2030, 12, 20)), "across a year");
        config.setMaxTokenLifetimeDays(1);
        assertEquals(TODAY.plusDays(1), policy.maxExpiration(TODAY));
        config.setMaxTokenLifetimeDays(Integer.MAX_VALUE);
        assertEquals(TODAY.plusDays(Integer.MAX_VALUE), policy.maxExpiration(TODAY));
    }

    @Test
    void validateBoundaries() {
        User bob = User.getById("bob", true);
        config.setMaxTokenLifetimeDays(30);

        assertNull(policy.validate(bob, TODAY.plusDays(30), TODAY), "today plus the maximum is accepted");
        assertEquals(Violation.OVER_MAXIMUM, policy.validate(bob, TODAY.plusDays(31), TODAY));
        assertEquals(Violation.OVER_MAXIMUM, policy.validate(bob, TODAY.plusYears(10), TODAY));
        assertNull(policy.validate(bob, TODAY.plusDays(1), TODAY));
        assertNull(policy.validate(bob, TODAY, TODAY), "expires at the end of today");
        assertEquals(Violation.NEVER_NOT_ALLOWED, policy.validate(bob, null, TODAY));
        assertNull(policy.validate(bob, TODAY.minusDays(1), TODAY), "the past is not the concern of the policy");

        config.setMaxTokenLifetimeDays(1);
        assertNull(policy.validate(bob, TODAY.plusDays(1), TODAY));
        assertEquals(Violation.OVER_MAXIMUM, policy.validate(bob, TODAY.plusDays(2), TODAY));
    }

    @Test
    void validateAcceptsEverythingWhileInactive() {
        User bob = User.getById("bob", true);
        assertNull(policy.validate(bob, null, TODAY));
        assertNull(policy.validate(bob, TODAY.plusYears(100), TODAY));
        config.setEnforceFrom(TODAY.minusDays(1).toString());
        assertNull(policy.validate(bob, null, TODAY), "a date alone does not activate the policy");
    }

    @Test
    void validateAcceptsEverythingInTheWarningPhase() {
        User bob = User.getById("bob", true);
        config.setMaxTokenLifetimeDays(30);
        config.setEnforceFrom(TODAY.plusDays(1).toString());
        assertNull(policy.validate(bob, null, TODAY));
        assertNull(policy.validate(bob, TODAY.plusDays(31), TODAY));

        assertEquals(Violation.NEVER_NOT_ALLOWED, policy.validate(bob, null, TODAY.plusDays(1)), "enforced from that day");
        assertEquals(Violation.OVER_MAXIMUM, policy.validate(bob, TODAY.plusDays(32), TODAY.plusDays(1)));
        assertNull(policy.validate(bob, TODAY.plusDays(31), TODAY.plusDays(1)));
    }

    @Test
    void validateAcceptsEverythingForExemptOwners() {
        User service = User.getById("Service Account", true);
        User bob = User.getById("bob", true);
        config.setMaxTokenLifetimeDays(30);
        config.setExemptUserIds("service account");

        assertNull(policy.validate(service, null, TODAY));
        assertNull(policy.validate(service, TODAY.plusDays(31), TODAY));
        assertEquals(Violation.NEVER_NOT_ALLOWED, policy.validate(bob, null, TODAY));
        assertEquals(Violation.OVER_MAXIMUM, policy.validate(bob, TODAY.plusDays(31), TODAY));
    }

    @Test
    void validateRejectsNeverWhenEnforceFromIsUnparsable() {
        User bob = User.getById("bob", true);
        config.setMaxTokenLifetimeDays(30);
        config.setEnforceFrom("not-a-date-for-validate");
        assertEquals(Violation.NEVER_NOT_ALLOWED, policy.validate(bob, null, TODAY));
        assertEquals(Violation.OVER_MAXIMUM, policy.validate(bob, TODAY.plusDays(31), TODAY));
        assertNull(policy.validate(bob, TODAY.plusDays(30), TODAY));
    }

    @Test
    void appliesToNobodyWhileInactive() {
        User bob = User.getById("bob", true);
        assertFalse(policy.appliesTo(bob, TODAY));
        config.setEnforceFrom(TODAY.minusDays(1).toString());
        assertFalse(policy.appliesTo(bob, TODAY), "a date alone does not activate the policy");
    }

    @Test
    void appliesToNobodyInTheWarningPhase() {
        User bob = User.getById("bob", true);
        config.setMaxTokenLifetimeDays(30);
        config.setEnforceFrom(TODAY.plusDays(1).toString());
        assertFalse(policy.appliesTo(bob, TODAY));
        assertTrue(policy.appliesTo(bob, TODAY.plusDays(1)), "enforced from that day");
    }

    @Test
    void appliesToNoExemptOwner() {
        User service = User.getById("Service Account", true);
        config.setMaxTokenLifetimeDays(30);
        config.setExemptUserIds("service account");
        assertFalse(policy.appliesTo(service, TODAY));

        config.setEnforceFrom("not-a-date-for-applies-to-exempt");
        assertFalse(policy.appliesTo(service, TODAY), "an exempt owner stays exempt when the date fails closed");
    }

    @Test
    void appliesToOwnersWhoAreNotExemptWhenEnforcing() {
        User service = User.getById("Service Account", true);
        User bob = User.getById("bob", true);
        config.setMaxTokenLifetimeDays(30);
        config.setExemptUserIds("service account");
        assertTrue(policy.appliesTo(bob, TODAY), "no date: enforcing right away");
        assertFalse(policy.appliesTo(service, TODAY));

        config.setEnforceFrom(TODAY.toString());
        assertTrue(policy.appliesTo(bob, TODAY), "the date itself is the first day of enforcement");
    }

    @Test
    void appliesToFailsClosedWhenEnforceFromIsUnparsable() {
        User bob = User.getById("bob", true);
        config.setMaxTokenLifetimeDays(30);
        config.setEnforceFrom("not-a-date-for-applies-to");
        assertTrue(policy.appliesTo(bob, TODAY));
    }

    @Test
    void strictModeIsOffByDefault() {
        assertFalse(config.isStrict());
        config.setMaxTokenLifetimeDays(30);
        assertFalse(policy.isStrict(TODAY), "a maximum alone is not strict");
        assertFalse(policy.isStrict(TODAY.plusYears(10)));
    }

    @Test
    void strictModeIsInEffectOnceTheMaximumIsEnforced() {
        config.setStrict(true);
        assertFalse(policy.isStrict(TODAY), "no maximum, so nothing to be strict about");
        config.setEnforceFrom(TODAY.minusDays(1).toString());
        assertFalse(policy.isStrict(TODAY), "a date alone does not activate the policy, strict or not");

        config.setMaxTokenLifetimeDays(30);
        config.setEnforceFrom(null);
        assertTrue(policy.isStrict(TODAY), "no date: enforcing right away");

        config.setEnforceFrom(TODAY.plusDays(1).toString());
        assertFalse(policy.isStrict(TODAY), "the warning phase: strict is scheduled, it is not in effect");
        assertTrue(policy.isStrict(TODAY.plusDays(1)), "the date itself is the first day");
        assertTrue(policy.isStrict(TODAY.plusDays(2)));

        config.setEnforceFrom(TODAY.toString());
        assertTrue(policy.isStrict(TODAY));
        config.setEnforceFrom(TODAY.minusDays(1).toString());
        assertTrue(policy.isStrict(TODAY), "a date in the past behaves like no date");
        config.setEnforceFrom("   ");
        assertTrue(policy.isStrict(TODAY), "blank is stored as null");

        config.setMaxTokenLifetimeDays(0);
        assertFalse(policy.isStrict(TODAY), "no maximum again");
    }

    @Test
    void strictModeSwitchedOffIsNotInEffect() {
        config.setMaxTokenLifetimeDays(30);
        config.setStrict(true);
        assertTrue(policy.isStrict(TODAY));
        config.setStrict(false);
        assertFalse(policy.isStrict(TODAY));
    }

    @Test
    void strictModeCountsAnUnparsableEnforcementDateAsStarted() {
        config.setMaxTokenLifetimeDays(30);
        config.setStrict(true);
        config.setEnforceFrom("not-a-date-for-is-strict");
        assertTrue(policy.isStrict(TODAY));
        assertTrue(policy.isStrict(TODAY.minusYears(50)), "in effect whatever the day");
        config.setStrict(false);
        assertFalse(policy.isStrict(TODAY), "and not at all when it is switched off");
    }

    @Test
    void strictModeRemovesTheExemptions() {
        User service = User.getById("Service Account", true);
        User bob = User.getById("bob", true);
        config.setMaxTokenLifetimeDays(30);
        config.setExemptUserIds("service account");

        assertTrue(policy.isExempt(service, TODAY));
        assertFalse(policy.appliesTo(service, TODAY));
        config.setStrict(true);
        assertFalse(policy.isExempt(service, TODAY), "nobody is exempt in strict mode");
        assertFalse(policy.isExempt(bob, TODAY));
        assertTrue(policy.appliesTo(service, TODAY), "so the maximum applies to every owner");
        assertTrue(policy.appliesTo(bob, TODAY));
        assertEquals(Violation.NEVER_NOT_ALLOWED, policy.validate(service, null, TODAY));
        assertEquals(Violation.OVER_MAXIMUM, policy.validate(service, TODAY.plusDays(31), TODAY));
        assertNull(policy.validate(service, TODAY.plusDays(30), TODAY));
        assertTrue(policy.isMonitored(service, TODAY), "an owner who is no longer exempt is monitored");

        // the exemptions stay in the configuration and apply again when strict mode is off
        config.setStrict(false);
        assertTrue(policy.isExempt(service, TODAY));
        assertFalse(policy.appliesTo(service, TODAY));
        assertNull(policy.validate(service, null, TODAY));
        assertEquals("service account", config.getExemptUserIds());
    }

    @Test
    void strictModeScheduledForALaterDateLeavesTheExemptionsAlone() {
        User service = User.getById("Service Account", true);
        config.setMaxTokenLifetimeDays(30);
        config.setExemptUserIds("service account");
        config.setStrict(true);
        config.setEnforceFrom(TODAY.plusDays(1).toString());

        assertFalse(policy.isStrict(TODAY));
        assertTrue(policy.isExempt(service, TODAY), "exempt until strict mode is in effect");
        assertFalse(policy.appliesTo(service, TODAY));
        assertNull(policy.validate(service, null, TODAY));
        assertFalse(policy.isMonitored(service, TODAY), "exempt owners are still not monitored");

        assertFalse(policy.isExempt(service, TODAY.plusDays(1)), "from the enforcement date on");
        assertTrue(policy.appliesTo(service, TODAY.plusDays(1)));
        assertEquals(Violation.NEVER_NOT_ALLOWED, policy.validate(service, null, TODAY.plusDays(1)));
    }

    @Test
    void strictModeWithoutAMaximumLeavesTheExemptionsAlone() {
        User service = User.getById("Service Account", true);
        config.setExemptUserIds("service account");
        config.setStrict(true);

        assertTrue(policy.isExempt(service, TODAY));
        assertFalse(policy.appliesTo(service, TODAY));
        assertFalse(policy.isActive());
    }

    @Test
    void strictModeIsScheduledOnlyWhileTheWarningPhaseLasts() {
        // switched off: never
        config.setMaxTokenLifetimeDays(30);
        config.setEnforceFrom(TODAY.plusDays(1).toString());
        assertFalse(policy.isStrictScheduled(TODAY), "a maximum and a date, but strict is off");

        config.setStrict(true);
        assertTrue(policy.isStrictScheduled(TODAY), "switched on, and the date is in the future");
        assertTrue(policy.isStrictScheduled(TODAY.minusYears(1)));
        assertFalse(policy.isStrictScheduled(TODAY.plusDays(1)), "the date itself is the first day of strict mode: it is in effect");
        assertTrue(policy.isStrict(TODAY.plusDays(1)));
        assertFalse(policy.isStrictScheduled(TODAY.plusDays(2)));

        config.setEnforceFrom(TODAY.toString());
        assertFalse(policy.isStrictScheduled(TODAY), "today: in effect");
        config.setEnforceFrom(TODAY.minusDays(1).toString());
        assertFalse(policy.isStrictScheduled(TODAY), "in the past: in effect");
        config.setEnforceFrom(null);
        assertFalse(policy.isStrictScheduled(TODAY), "no date: in effect");
        config.setEnforceFrom("  ");
        assertFalse(policy.isStrictScheduled(TODAY), "a blank date is no date");
        config.setEnforceFrom("not-a-date-for-is-strict-scheduled");
        assertFalse(policy.isStrictScheduled(TODAY), "a date that cannot be read counts as started");
        assertTrue(policy.isStrict(TODAY));

        // no maximum: inert
        config.setEnforceFrom(TODAY.plusDays(1).toString());
        assertTrue(policy.isStrictScheduled(TODAY));
        config.setMaxTokenLifetimeDays(0);
        assertFalse(policy.isStrictScheduled(TODAY), "no maximum, so nothing is scheduled");
        assertFalse(policy.isStrict(TODAY));
    }

    @Test
    void anExemptOwnerIsExemptUntilTheDayStrictModeStarts() {
        User service = User.getById("Service Account", true);
        User bob = User.getById("bob", true);
        config.setMaxTokenLifetimeDays(30);
        config.setExemptUserIds("service account");
        config.setStrict(true);
        LocalDate start = TODAY.plusDays(10);
        config.setEnforceFrom(start.toString());

        assertEquals(start, policy.getExemptionEnd(service, TODAY), "exempt now, and not from the date of the strict mode");
        assertEquals(start, policy.getExemptionEnd(service, start.minusDays(1)), "the last day of the exemption");
        assertNull(policy.getExemptionEnd(bob, TODAY), "an owner who is not exempt has no exemption to end");
        assertTrue(policy.isExempt(service, TODAY), "exempt as before: only the report changes");
        assertNull(policy.getExemptionEnd(service, start), "from the date nobody is exempt");
        assertFalse(policy.isExempt(service, start));
        assertNull(policy.getExemptionEnd(service, start.plusDays(1)));
    }

    @Test
    void theExemptionEndsOnlyWhenStrictModeIsSwitchedOnAndHasAMaximumToApplyAndADateToWaitFor() {
        User service = User.getById("Service Account", true);
        config.setExemptUserIds("service account");
        LocalDate future = TODAY.plusDays(10);

        // no maximum
        config.setStrict(true);
        config.setEnforceFrom(future.toString());
        assertNull(policy.getExemptionEnd(service, TODAY), "no maximum: inert");

        // strict off: the exemption is not ending
        config.setMaxTokenLifetimeDays(30);
        config.setStrict(false);
        assertNull(policy.getExemptionEnd(service, TODAY), "strict is off: the exemption does not end");
        assertTrue(policy.isExempt(service, TODAY));

        // strict on, no date or a date that has come: already in effect, nobody is exempt
        config.setStrict(true);
        for (String date : new String[] {null, "", TODAY.toString(), TODAY.minusDays(30).toString(), "not-a-date-for-exemption-end"}) {
            config.setEnforceFrom(date);
            assertNull(policy.getExemptionEnd(service, TODAY), "date: " + date);
            assertFalse(policy.isExempt(service, TODAY), "in effect: nobody is exempt, date: " + date);
        }

        // only the owner who is on the list has an exemption to end
        config.setEnforceFrom(future.toString());
        assertEquals(future, policy.getExemptionEnd(service, TODAY));
        config.setExemptUserIds("someone else");
        assertNull(policy.getExemptionEnd(service, TODAY));
        config.setExemptUserIds(null);
        assertNull(policy.getExemptionEnd(service, TODAY));
    }

    @Test
    void exceedsMaximumIsTheCheckOfATokenAlone() throws Exception {
        User alice = User.getById("alice", true);
        HashedToken never = addToken(alice, "never", 1, null);
        HashedToken far = addToken(alice, "far", 2, TODAY.plusDays(31));
        HashedToken last = addToken(alice, "last", 3, TODAY.plusDays(30));
        HashedToken today = addToken(alice, "today", 4, TODAY);
        HashedToken expired = addToken(alice, "expired", 5, TODAY.minusDays(1));
        assertFalse(policy.exceedsMaximum(never, TODAY), "no maximum: nothing is exceeded");

        config.setMaxTokenLifetimeDays(30);
        assertTrue(policy.exceedsMaximum(never, TODAY));
        assertTrue(policy.exceedsMaximum(far, TODAY));
        assertFalse(policy.exceedsMaximum(last, TODAY), "the last day that is allowed");
        assertFalse(policy.exceedsMaximum(today, TODAY));
        assertFalse(policy.exceedsMaximum(expired, TODAY), "a token that has expired cannot be used");
        assertFalse(policy.exceedsMaximum(far, TODAY.plusDays(1)), "a day later, the maximum reaches further");

        // whoever the owner is: exemptions are not looked at
        config.setExemptUserIds("alice");
        assertTrue(policy.exceedsMaximum(never, TODAY));
        assertTrue(policy.isCompliant(alice, never, TODAY), "while the token of an exempt owner stays compliant");
    }

    @Test
    void theTokensOfAnOwnerWhoIsNoLongerExemptAreCheckedInStrictMode() throws Exception {
        User service = User.getById("Service Account", true);
        config.setMaxTokenLifetimeDays(30);
        config.setExemptUserIds("service account");
        HashedToken never = addToken(service, "never", 1, null);
        HashedToken far = addToken(service, "far", 2, TODAY.plusDays(31));
        HashedToken within = addToken(service, "within", 3, TODAY.plusDays(30));
        assertTrue(policy.isCompliant(service, never, TODAY));
        assertTrue(policy.isCompliant(service, far, TODAY));

        config.setStrict(true);
        assertFalse(policy.isCompliant(service, never, TODAY));
        assertFalse(policy.isCompliant(service, far, TODAY));
        assertTrue(policy.isCompliant(service, within, TODAY));

        config.setEnforceFrom(TODAY.plusDays(1).toString());
        assertTrue(policy.isCompliant(service, never, TODAY), "strict is only scheduled");
        assertFalse(policy.isCompliant(service, never, TODAY.plusDays(1)));
    }

    private static HashedToken addToken(User owner, String name, int secret, LocalDate expiry) throws IOException {
        ApiTokenProperty property = owner.getProperty(ApiTokenProperty.class);
        String uuid = property.addFixedNewToken(name, "11" + "%032x".formatted(secret), expiry);
        return property.getTokenStore().getTokenListSnapshot().stream()
                .filter(token -> token.getUuid().equals(uuid))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void everythingIsCompliantWhileInactive() throws Exception {
        User bob = User.getById("bob", true);
        assertTrue(policy.isCompliant(bob, addToken(bob, "never", 1, null), TODAY));
        assertTrue(policy.isCompliant(bob, addToken(bob, "far", 2, TODAY.plusYears(100)), TODAY));
        config.setEnforceFrom(TODAY.minusDays(1).toString());
        assertTrue(policy.isCompliant(bob, addToken(bob, "never again", 3, null), TODAY), "a date alone does not activate the policy");
    }

    @Test
    void isCompliantBoundaries() throws Exception {
        User bob = User.getById("bob", true);
        config.setMaxTokenLifetimeDays(30);

        assertFalse(policy.isCompliant(bob, addToken(bob, "never", 1, null), TODAY), "never expires");
        assertTrue(policy.isCompliant(bob, addToken(bob, "maximum", 2, TODAY.plusDays(30)), TODAY), "today plus the maximum");
        assertFalse(policy.isCompliant(bob, addToken(bob, "one over", 3, TODAY.plusDays(31)), TODAY));
        assertFalse(policy.isCompliant(bob, addToken(bob, "far", 4, TODAY.plusYears(10)), TODAY));
        assertTrue(policy.isCompliant(bob, addToken(bob, "tomorrow", 5, TODAY.plusDays(1)), TODAY));
        assertTrue(policy.isCompliant(bob, addToken(bob, "last day", 6, TODAY), TODAY), "valid through today");
        assertTrue(policy.isCompliant(bob, addToken(bob, "expired", 7, TODAY.minusDays(1)), TODAY), "an expired token cannot be used");
        assertTrue(policy.isCompliant(bob, addToken(bob, "long expired", 8, TODAY.minusYears(5)), TODAY));

        // the day decides: the same tokens, a month later
        HashedToken maximum = addToken(bob, "moves", 9, TODAY.plusDays(30));
        assertTrue(policy.isCompliant(bob, maximum, TODAY));
        assertTrue(policy.isCompliant(bob, maximum, TODAY.plusDays(5)), "the maximum reaches further");
        assertFalse(policy.isCompliant(bob, maximum, TODAY.minusDays(1)), "the maximum reaches one day less far");
        assertFalse(policy.isCompliant(bob, maximum, TODAY.minusDays(5)));
        assertTrue(policy.isCompliant(bob, maximum, TODAY.plusDays(31)), "it has expired");
    }

    @Test
    void theLegacyTokenIsNotCompliantAsItNeverExpires() throws Exception {
        User bob = User.getById("bob", true);
        config.setMaxTokenLifetimeDays(30);
        config.setCreationOfLegacyTokenEnabled(true);
        ApiTokenProperty property = bob.getProperty(ApiTokenProperty.class);
        property.changeApiToken();
        HashedToken legacy = property.getTokenStore().getLegacyToken();
        assertNotNull(legacy);
        assertFalse(policy.isCompliant(bob, legacy, TODAY));
        assertFalse(policy.isCompliant(bob, legacy, TODAY.plusYears(50)));

        config.setMaxTokenLifetimeDays(0);
        assertTrue(policy.isCompliant(bob, legacy, TODAY), "no maximum");
    }

    @Test
    void tokensAreCheckedInTheWarningPhaseToo() throws Exception {
        User bob = User.getById("bob", true);
        config.setMaxTokenLifetimeDays(30);
        config.setEnforceFrom(TODAY.plusDays(1).toString());
        assertFalse(policy.isCompliant(bob, addToken(bob, "never", 1, null), TODAY), "validate accepts it, the panel does not");
        assertFalse(policy.isCompliant(bob, addToken(bob, "far", 2, TODAY.plusDays(31)), TODAY));
        assertTrue(policy.isCompliant(bob, addToken(bob, "maximum", 3, TODAY.plusDays(30)), TODAY));
    }

    @Test
    void tokensOfExemptOwnersAreCompliant() throws Exception {
        User service = User.getById("Service Account", true);
        User bob = User.getById("bob", true);
        config.setMaxTokenLifetimeDays(30);
        config.setExemptUserIds("service account");

        assertTrue(policy.isCompliant(service, addToken(service, "never", 1, null), TODAY));
        assertTrue(policy.isCompliant(service, addToken(service, "far", 2, TODAY.plusDays(31)), TODAY));
        assertFalse(policy.isCompliant(bob, addToken(bob, "never", 3, null), TODAY));
        assertFalse(policy.isCompliant(bob, addToken(bob, "far", 4, TODAY.plusDays(31)), TODAY));
    }

    @Test
    void tokensAreCheckedWhenEnforceFromIsUnparsable() throws Exception {
        User bob = User.getById("bob", true);
        config.setMaxTokenLifetimeDays(30);
        config.setEnforceFrom("not-a-date-for-is-compliant");
        assertFalse(policy.isCompliant(bob, addToken(bob, "never", 1, null), TODAY));
        assertTrue(policy.isCompliant(bob, addToken(bob, "maximum", 2, TODAY.plusDays(30)), TODAY));
    }

    @Test
    void isMonitoredInEveryPhaseExceptForExemptOwners() {
        User service = User.getById("Service Account", true);
        User bob = User.getById("bob", true);
        assertFalse(policy.isMonitored(bob, TODAY), "inactive");

        config.setMaxTokenLifetimeDays(30);
        config.setExemptUserIds("service account");
        assertTrue(policy.isMonitored(bob, TODAY), "no date");
        assertFalse(policy.isMonitored(service, TODAY), "exempt");

        config.setEnforceFrom(TODAY.plusDays(1).toString());
        assertFalse(policy.appliesTo(bob, TODAY), "not enforcing yet");
        assertTrue(policy.isMonitored(bob, TODAY), "the warning phase is monitored");
        assertFalse(policy.isMonitored(service, TODAY));

        config.setEnforceFrom("not-a-date-for-is-monitored");
        assertTrue(policy.isMonitored(bob, TODAY));
        assertFalse(policy.isMonitored(service, TODAY), "an exempt owner stays exempt when the date fails closed");
    }

    @Test
    void getPhase() {
        assertEquals(Phase.INACTIVE, policy.getPhase(TODAY));
        config.setEnforceFrom(TODAY.plusDays(3).toString());
        assertEquals(Phase.INACTIVE, policy.getPhase(TODAY), "a date alone does not activate the policy");

        config.setMaxTokenLifetimeDays(30);
        assertEquals(Phase.WARNING, policy.getPhase(TODAY));
        assertEquals(Phase.WARNING, policy.getPhase(TODAY.plusDays(2)));
        assertEquals(Phase.ENFORCING, policy.getPhase(TODAY.plusDays(3)), "the date is the first day of enforcement");
        assertEquals(Phase.ENFORCING, policy.getPhase(TODAY.plusDays(4)));

        config.setEnforceFrom(null);
        assertEquals(Phase.ENFORCING, policy.getPhase(TODAY));
        config.setEnforceFrom(TODAY.minusDays(9).toString());
        assertEquals(Phase.ENFORCING, policy.getPhase(TODAY));
        config.setEnforceFrom("not-a-date-for-get-phase");
        assertEquals(Phase.ENFORCING_INVALID_DATE, policy.getPhase(TODAY));
        assertEquals(Phase.ENFORCING_INVALID_DATE, policy.getPhase(TODAY.minusYears(50)));

        config.setMaxTokenLifetimeDays(0);
        assertEquals(Phase.INACTIVE, policy.getPhase(TODAY));
    }

    @Test
    void getEnforcementStart() {
        assertNull(policy.getEnforcementStart(), "inactive");
        config.setEnforceFrom(TODAY.plusDays(3).toString());
        assertNull(policy.getEnforcementStart(), "inactive, even with a date");

        config.setMaxTokenLifetimeDays(30);
        assertEquals(TODAY.plusDays(3), policy.getEnforcementStart());
        config.setEnforceFrom(TODAY.minusDays(3).toString());
        assertEquals(TODAY.minusDays(3), policy.getEnforcementStart(), "a date that has passed is still the start");
        config.setEnforceFrom(null);
        assertNull(policy.getEnforcementStart(), "enforcing from the start: no date");
        config.setEnforceFrom("not-a-date-for-get-enforcement-start");
        assertNull(policy.getEnforcementStart(), "unparsable: no date");
    }

    private long countWarnings(String value) {
        return logging.getMessages().stream().filter(m -> m.contains(value)).count();
    }

    private static class IdStrategySpecifyingSecurityRealm extends HudsonPrivateSecurityRealm {
        private final IdStrategy idStrategy;

        IdStrategySpecifyingSecurityRealm(IdStrategy idStrategy) {
            super(true, false, null);
            this.idStrategy = idStrategy;
        }

        @Override
        public IdStrategy getUserIdStrategy() {
            return idStrategy;
        }
    }
}
