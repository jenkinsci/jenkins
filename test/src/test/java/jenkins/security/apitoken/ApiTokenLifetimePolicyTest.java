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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.User;
import hudson.security.HudsonPrivateSecurityRealm;
import java.time.LocalDate;
import java.util.List;
import java.util.logging.Level;
import jenkins.model.IdStrategy;
import jenkins.security.apitoken.ApiTokenLifetimePolicy.Violation;
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
