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
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.ExtensionList;
import hudson.model.AdministrativeMonitor;
import hudson.model.User;
import hudson.model.UserProperty;
import hudson.security.csrf.CrumbIssuer;
import jakarta.servlet.ServletRequest;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.SimpleFormatter;
import java.util.regex.Pattern;
import jenkins.model.Jenkins;
import jenkins.security.ApiTokenProperty;
import jenkins.security.apitoken.ApiTokenLifetimeAdministrativeMonitor.Report;
import jenkins.security.apitoken.ApiTokenLifetimeAdministrativeMonitor.Row;
import jenkins.security.apitoken.ApiTokenLifetimePolicy.Phase;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import net.sf.json.util.JSONUtils;
import org.htmlunit.FailingHttpStatusCodeException;
import org.htmlunit.HttpMethod;
import org.htmlunit.WebRequest;
import org.htmlunit.WebResponse;
import org.htmlunit.html.DomElement;
import org.htmlunit.html.DomNode;
import org.htmlunit.html.HtmlAnchor;
import org.htmlunit.html.HtmlButton;
import org.htmlunit.html.HtmlElement;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.util.NameValuePair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LogRecorder;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.hudson.test.recipes.LocalData;

/**
 * A maximum lifetime of API tokens only stops the tokens that are created afterwards, through the HTTP endpoints. The compliance
 * panel is where an administrator finds the other ones: the tokens that existed before the maximum was set, the legacy tokens,
 * which never expire, and what scripts create. It also finds the secrets that several tokens of one user share, as a secret that
 * is registered again gets a new expiration each time, so that the maximum would bound the tokens and not the secret.
 * <p>
 * These tests cover what makes the monitor active, what its report and its page list for each phase of the policy, what is
 * hidden from the users who are exempt, who can reach it, and that nothing a user chooses is ever shown as markup. Whether a token
 * is a finding depends on the day, which the checks that take one use a fixed one, far from the date of the clock; the tests of
 * the page can only use the clock, so the dates they use are relative to it. See issue #27612.
 * <p>
 * They also cover the two actions of the page, Revoke and Shorten. Both are POSTs that only administrators reach, and that look
 * at the token again when they arrive: Revoke takes with it every token of the user that has not expired and holds the same
 * secret, even one that was added after the page was rendered, and Shorten refuses a token that complies, that has expired, that
 * is the legacy token or that belongs to an exempt user. Requests that are forged or out of date change nothing and are answered
 * with a message that does not repeat them, and every action that is done is logged without any name that users chose.
 */
@Issue("JENKINS-27612")
@WithJenkins
class ApiTokenLifetimeAdministrativeMonitorTest {

    /** The day of the checks that take one. It is not the day of the clock, so a method that read the clock would not agree. */
    private static final LocalDate TODAY = LocalDate.of(2040, 1, 15);

    private static final String PANEL_URL = "administrativeMonitor/apiTokenLifetime";
    private static final String REVOKE_URL = PANEL_URL + "/revoke";
    private static final String SHORTEN_URL = PANEL_URL + "/shorten";

    private final LogRecorder logging = new LogRecorder().record(ApiTokenLifetimeAdministrativeMonitor.class, Level.INFO).capture(50);

    private static final String EVIL_ID = "ev\"il'<b>";
    private static final String EVIL_FULL_NAME = "<img src=x onerror=alert(1)>";
    private static final String EVIL_TOKEN_NAME = "$& $` </td><script>alert(1)</script>\"'";

    private JenkinsRule j;
    private ApiTokenPropertyConfiguration config;
    private ApiTokenLifetimeAdministrativeMonitor monitor;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
        config = ApiTokenPropertyConfiguration.get();
        monitor = ExtensionList.lookupSingleton(ApiTokenLifetimeAdministrativeMonitor.class);
    }

    /** The plain value of the token number {@code n}: tokens with the same number share their secret. */
    private static String token(int n) {
        return "11" + "%032x".formatted(n);
    }

    private ApiTokenProperty propertyOf(String userId) {
        return User.getById(userId, true).getProperty(ApiTokenProperty.class);
    }

    /** Adds a token the way scripts do, through the Java API, which neither applies the maximum nor looks for a known secret. */
    private String add(String userId, String name, int secret, @CheckForNull LocalDate expiry) throws IOException {
        return propertyOf(userId).addFixedNewToken(name, token(secret), expiry);
    }

    private static List<Row> rowsOf(Report report, String userId) {
        return report.getRows().stream().filter(row -> row.getUserId().equals(userId)).toList();
    }

    private static Row rowOf(Report report, String userId, String tokenName) {
        List<Row> rows = report.getRows().stream()
                .filter(row -> row.getUserId().equals(userId) && row.getName().equals(tokenName))
                .toList();
        assertThat("rows of " + userId + " named " + tokenName, rows, hasSize(1));
        return rows.get(0);
    }

    private HtmlPage managePage(JenkinsRule.WebClient wc) throws Exception {
        return wc.goTo(PANEL_URL + "/manage");
    }

    private static List<DomElement> tableRows(HtmlPage page) {
        List<DomElement> rows = new ArrayList<>();
        for (DomNode row : page.querySelectorAll("tr[data-uuid]")) {
            rows.add((DomElement) row);
        }
        return rows;
    }

    private static DomElement tableRowOf(HtmlPage page, String uuid) {
        List<DomElement> rows = tableRows(page).stream().filter(row -> uuid.equals(row.getAttribute("data-uuid"))).toList();
        assertThat("rows of " + uuid, rows, hasSize(1));
        return rows.get(0);
    }

    private static String text(DomNode row, String cellClass) {
        DomNode cell = row.querySelector("td." + cellClass);
        assertNotNull(cell, cellClass);
        return cell.asNormalizedText();
    }

    private static List<String> buttonActions(DomElement row) {
        List<String> actions = new ArrayList<>();
        for (DomNode button : row.querySelectorAll("button[data-token-action]")) {
            actions.add(((DomElement) button).getAttribute("data-token-action"));
        }
        return actions;
    }

    private void setUpSecurity() {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.ADMINISTER).everywhere().to("admin")
                .grant(Jenkins.SYSTEM_READ).everywhere().to("sysreader")
                .grant(Jenkins.READ).everywhere().toEveryone());
    }

    // ---- what makes the monitor active

    @Test
    void theMonitorIsASecurityMonitorWithItsOwnName() {
        assertTrue(monitor.isSecurity());
        assertEquals("apiTokenLifetime", monitor.id);
        assertEquals(PANEL_URL, monitor.getUrl());
        assertEquals("API token lifetime", monitor.getDisplayName());
        assertTrue(monitor.isEnabled());
        assertTrue(j.jenkins.getExtensionList(AdministrativeMonitor.class).stream().anyMatch(m -> m == monitor));
    }

    @Test
    void isNotActivatedWhileThePolicyIsInactive() throws Exception {
        add("alice", "never", 1, null);
        assertFalse(monitor.isActivated(TODAY));
        assertFalse(monitor.isActivated());

        Report report = monitor.scan(TODAY);
        assertEquals(Phase.INACTIVE, report.getPhase());
        assertFalse(report.isActive());
        assertThat(report.getRows(), empty());
        assertEquals(0, report.getNonCompliantCount());
        assertEquals(0, report.getSharedSecretGroupCount());
    }

    @Test
    void isNotActivatedWhenEveryTokenComplies() throws Exception {
        config.setMaxTokenLifetimeDays(30);
        add("alice", "soon", 1, TODAY.plusDays(1));
        add("alice", "last day", 2, TODAY.plusDays(30));
        add("bob", "today", 3, TODAY);
        assertFalse(monitor.isActivated(TODAY));
        assertThat(monitor.scan(TODAY).getRows(), empty());
    }

    @Test
    void isActivatedByATokenThatNeverExpires() throws Exception {
        config.setMaxTokenLifetimeDays(30);
        add("alice", "compliant", 1, TODAY.plusDays(5));
        assertFalse(monitor.isActivated(TODAY));
        add("bob", "never", 2, null);
        assertTrue(monitor.isActivated(TODAY));
        assertTrue(monitor.isActivated(), "with the clock too, the expiration does not depend on it");
    }

    @Test
    void theMaximumItselfIsCompliantAndOneDayMoreIsNot() throws Exception {
        config.setMaxTokenLifetimeDays(30);
        add("alice", "boundary", 1, TODAY.plusDays(30));
        assertFalse(monitor.isActivated(TODAY));

        add("bob", "one day over", 2, TODAY.plusDays(31));
        assertTrue(monitor.isActivated(TODAY));

        Report report = monitor.scan(TODAY);
        assertThat(report.getRows(), hasSize(1));
        Row row = rowOf(report, "bob", "one day over");
        assertTrue(row.isOverMaximum());
        assertEquals(1, row.getDaysOverMaximum());
        assertEquals(TODAY.plusDays(31).toString(), row.getExpiration());
        assertEquals(1, report.getNonCompliantCount());
    }

    @Test
    void anExpiredTokenIsNotAFinding() throws Exception {
        config.setMaxTokenLifetimeDays(30);
        add("alice", "expired", 1, TODAY.minusDays(1));
        add("alice", "last day", 2, TODAY);
        assertFalse(monitor.isActivated(TODAY));
        assertThat(monitor.scan(TODAY).getRows(), empty());
    }

    @Test
    void anExemptOwnersTokensAreHiddenWhileTheMaximumApplies() throws Exception {
        config.setMaxTokenLifetimeDays(30);
        config.setExemptUserIds("alice");
        add("alice", "never", 1, null);
        add("alice", "far away", 2, TODAY.plusDays(1000));
        assertFalse(monitor.isActivated(TODAY));
        Report hidden = monitor.scan(TODAY);
        assertThat(hidden.getRows(), empty());
        assertEquals(0, hidden.getNonCompliantCount());

        add("bob", "never", 3, null);
        assertTrue(monitor.isActivated(TODAY));
        Report report = monitor.scan(TODAY);
        assertThat(report.getRows(), hasSize(1));
        assertEquals("bob", report.getRows().get(0).getUserId());
    }

    @Test
    void aLegacyTokenOfAnOwnerWhoIsNotExemptIsAFinding() throws Exception {
        config.setMaxTokenLifetimeDays(30);
        config.setCreationOfLegacyTokenEnabled(true);
        ApiTokenProperty alice = propertyOf("alice");
        alice.changeApiToken();
        assertTrue(alice.hasLegacyToken());

        assertTrue(monitor.isActivated(TODAY));
        Report report = monitor.scan(TODAY);
        assertThat(report.getRows(), hasSize(1));
        Row row = report.getRows().get(0);
        assertEquals("alice", row.getUserId());
        assertTrue(row.isLegacy());
        assertFalse(row.isNeverExpires(), "a legacy token has a status of its own");
        assertNull(row.getExpiration());
        assertTrue(row.isOverMaximum());
        assertFalse(row.isShortenable(), "a legacy token has no expiration to shorten");

        config.setExemptUserIds("alice");
        assertFalse(monitor.isActivated(TODAY), "the legacy token of an exempt owner is hidden");
    }

    @Test
    void theWarningPhaseListsAndActivatesToo() throws Exception {
        config.setMaxTokenLifetimeDays(30);
        config.setEnforceFrom(TODAY.plusDays(10).toString());
        add("alice", "never", 1, null);

        assertTrue(monitor.isActivated(TODAY));
        Report report = monitor.scan(TODAY);
        assertEquals(Phase.WARNING, report.getPhase());
        assertThat(report.getRows(), hasSize(1));
        assertEquals(1, report.getNonCompliantCount());
    }

    // ---- shared secrets (S19)

    /**
     * S19. Given a maximum above 0 and a non-exempt owner with two live entries that share a secret, created through the Java API,
     * when {@code isActivated()} is evaluated, then it is true, and the report lists the group once, with both entries.
     */
    @Test
    void s19TwoLiveEntriesThatShareASecretActivateTheMonitor() throws Exception {
        config.setMaxTokenLifetimeDays(30);
        add("alice", "first", 1, TODAY.plusDays(10));
        assertFalse(monitor.isActivated(TODAY), "one entry is not shared");
        add("alice", "second", 1, TODAY.plusDays(20));
        assertTrue(monitor.isActivated(TODAY));

        Report report = monitor.scan(TODAY);
        assertEquals(0, report.getNonCompliantCount(), "both comply on their own");
        assertEquals(1, report.getSharedSecretGroupCount());
        assertThat(report.getRows(), hasSize(2));
    }

    /**
     * S19. Given a maximum above 0 and one live entry that complies, plus an expired sibling with the same secret, when
     * {@code isActivated()} is evaluated, then it is false: the secret is not a finding, as only one entry can authenticate.
     */
    @Test
    void s19ALiveEntryAndAnExpiredSiblingAreNotAFinding() throws Exception {
        config.setMaxTokenLifetimeDays(30);
        add("alice", "live", 1, TODAY.plusDays(10));
        add("alice", "expired", 1, TODAY.minusDays(1));
        assertFalse(monitor.isActivated(TODAY));
        Report report = monitor.scan(TODAY);
        assertThat(report.getRows(), empty());
        assertEquals(0, report.getSharedSecretGroupCount());
    }

    /**
     * S19. Given a maximum above 0 and entries that share a secret and have all expired, when {@code isActivated()} is
     * evaluated, then it is false, and the report lists nothing.
     */
    @Test
    void s19EntriesThatAllExpiredAreNotAFinding() throws Exception {
        config.setMaxTokenLifetimeDays(30);
        add("alice", "first", 1, TODAY.minusDays(1));
        add("alice", "second", 1, TODAY.minusDays(5));
        assertFalse(monitor.isActivated(TODAY));
        assertThat(monitor.scan(TODAY).getRows(), empty());
    }

    /**
     * S19. Given a maximum above 0 and an exempt owner with two live entries that share a secret, while strict mode is off (there
     * is no strict mode yet, C4 adds the strict variant), when {@code isActivated()} is evaluated, then it is false.
     */
    @Test
    void s19TheGroupOfAnExemptOwnerIsNotAFinding() throws Exception {
        config.setMaxTokenLifetimeDays(30);
        config.setExemptUserIds("alice");
        add("alice", "first", 1, TODAY.plusDays(10));
        add("alice", "second", 1, TODAY.plusDays(20));
        assertFalse(monitor.isActivated(TODAY));
        Report report = monitor.scan(TODAY);
        assertThat(report.getRows(), empty());
        assertEquals(0, report.getSharedSecretGroupCount());
    }

    @Test
    void theLegacyTokenAndAFixedTokenWithItsValueAreAGroup() throws Exception {
        config.setMaxTokenLifetimeDays(30);
        config.setCreationOfLegacyTokenEnabled(true);
        ApiTokenProperty alice = propertyOf("alice");
        alice.changeApiToken();
        // the legacy secret, as a token of the current version: they have the same hash
        String legacyValue = alice.getApiToken();
        alice.addFixedNewToken("fixed", "11" + legacyValue, TODAY.plusDays(5));

        Report report = monitor.scan(TODAY);
        assertEquals(1, report.getSharedSecretGroupCount());
        assertThat(rowsOf(report, "alice"), hasSize(2));
        assertTrue(rowOf(report, "alice", "fixed").isSharesSecret());
        assertFalse(rowOf(report, "alice", "fixed").isOverMaximum());
    }

    // ---- the enforcement date

    @Test
    @LocalData
    void anUnparsableEnforcementDateActivatesTheMonitorBeforeAnyScan() {
        assertEquals("2027-14-35", config.getEnforceFrom());
        // nobody has a token: nothing could be found by looking at them
        assertTrue(monitor.isActivated(TODAY));
        assertTrue(monitor.isActivated());

        Report report = monitor.scan(TODAY);
        assertEquals(Phase.ENFORCING_INVALID_DATE, report.getPhase());
        assertEquals("Enforcing (invalid start date in configuration)", report.getPhaseLabel());
        assertThat(report.getRows(), empty());
    }

    @Test
    @LocalData
    void anUnparsableEnforcementDateWithoutAMaximumIsInert() {
        assertEquals("2027-14-36", config.getEnforceFrom());
        assertFalse(monitor.isActivated(TODAY));
        assertEquals(Phase.INACTIVE, monitor.scan(TODAY).getPhase());
    }

    @Test
    void thePhaseLabelAndTheCountdownFollowTheEnforcementDate() {
        config.setMaxTokenLifetimeDays(30);

        config.setEnforceFrom(TODAY.plusDays(10).toString());
        Report warning = monitor.scan(TODAY);
        assertEquals(Phase.WARNING, warning.getPhase());
        assertEquals("Warning phase until " + TODAY.plusDays(10), warning.getPhaseLabel());
        assertEquals("The maximum lifetime is enforced from that day on, in 10 days. Until then nothing is rejected.", warning.getCountdown());

        config.setEnforceFrom(TODAY.plusDays(1).toString());
        assertEquals("The maximum lifetime is enforced from that day on, in 1 day. Until then nothing is rejected.", monitor.scan(TODAY).getCountdown());

        config.setEnforceFrom(TODAY.toString());
        Report today = monitor.scan(TODAY);
        assertEquals(Phase.ENFORCING, today.getPhase());
        assertEquals("Enforcing since " + TODAY, today.getPhaseLabel());
        assertNull(today.getCountdown());

        config.setEnforceFrom(TODAY.minusDays(40).toString());
        assertEquals("Enforcing since " + TODAY.minusDays(40), monitor.scan(TODAY).getPhaseLabel());

        config.setEnforceFrom(null);
        Report none = monitor.scan(TODAY);
        assertEquals(Phase.ENFORCING, none.getPhase());
        assertEquals("Enforcing", none.getPhaseLabel(), "without a date there is none to show");
        assertNull(none.getCountdown());

        config.setEnforceFrom("not-a-date-in-the-monitor-test");
        Report invalid = monitor.scan(TODAY);
        assertEquals(Phase.ENFORCING_INVALID_DATE, invalid.getPhase());
        assertEquals("Enforcing (invalid start date in configuration)", invalid.getPhaseLabel());
        assertNull(invalid.getCountdown());
    }

    // ---- what the scan skips and does not do

    @Test
    void usersWithoutAPropertyOrWithoutTokensAreSkippedAndNoUserIsCreated() throws Exception {
        config.setMaxTokenLifetimeDays(30);
        User withoutProperty = User.getById("without property", true);
        // there is no API to remove a property, and a user that is loaded gets the missing ones: only a hand-made state can have none
        Field properties = User.class.getDeclaredField("properties");
        properties.setAccessible(true);
        properties.set(withoutProperty, new ArrayList<UserProperty>());
        assertNull(withoutProperty.getProperty(ApiTokenProperty.class));
        add("empty", "gone", 1, null);
        propertyOf("empty").revokeAllTokens();
        add("alice", "never", 2, null);
        int users = User.getAll().size();

        assertTrue(monitor.isActivated(TODAY));
        Report report = monitor.scan(TODAY);

        assertThat(report.getRows(), hasSize(1));
        assertEquals("alice", report.getRows().get(0).getUserId());
        assertEquals(users, User.getAll().size(), "scanning does not create users");
        assertNull(User.getById("ghost", false));
    }

    @Test
    void aUserThatWasNeverSavedIsListed() throws Exception {
        config.setMaxTokenLifetimeDays(30);
        config.setTokenGenerationOnCreationEnabled(true);
        User phantom = User.getById("phantom", true);
        assertTrue(phantom.getProperty(ApiTokenProperty.class).hasLegacyToken());

        assertTrue(monitor.isActivated(TODAY));
        Report report = monitor.scan(TODAY);
        assertThat(report.getRows(), hasSize(1));
        Row row = report.getRows().get(0);
        assertEquals("phantom", row.getUserId());
        assertTrue(row.isLegacy());
        assertNull(row.getToken().creationDate, "a legacy token that was migrated has no creation date");
    }

    @Test
    void rowsAreSortedByUserIdThenByTokenNameWhateverTheOrderOfCreation() throws Exception {
        config.setMaxTokenLifetimeDays(30);
        add("carol", "alpha", 1, null);
        add("alice", "Zulu", 2, null);
        add("bob", "x", 3, null);
        add("alice", "bravo", 4, null);
        add("alice", "ALPHA", 5, null);

        List<String> order = monitor.scan(TODAY).getRows().stream().map(row -> row.getUserId() + "/" + row.getName()).toList();
        assertEquals(List.of("alice/ALPHA", "alice/bravo", "alice/Zulu", "bob/x", "carol/alpha"), order);
    }

    // ---- shared secrets in the report (S13)

    /**
     * S13. Given duplicates created while the policy was off, and a maximum above 0, when the panel is rendered (here, its report
     * is scanned), then the entries are grouped and each row is marked as sharing its secret with the others, by name.
     * An expired sibling is named as such and is not a row.
     */
    @Test
    void s13TheRowsOfAGroupNameTheOtherEntriesAndExpiredSiblingsAreNotRows() throws Exception {
        config.setMaxTokenLifetimeDays(30);
        add("alice", "first", 1, TODAY.plusDays(10));
        add("alice", "second", 1, TODAY.plusDays(20));
        add("alice", "old", 1, TODAY.minusDays(3));

        Report report = monitor.scan(TODAY);
        assertThat(report.getRows(), hasSize(2));
        assertEquals("second, old (expired)", rowOf(report, "alice", "first").getSharedWith());
        assertEquals("first, old (expired)", rowOf(report, "alice", "second").getSharedWith());
        assertEquals(1, report.getSharedSecretGroupCount());
        assertEquals(0, report.getNonCompliantCount());
    }

    @Test
    void aSharedSecretRowThatIsOverTheMaximumIsOneRowWithBothStatuses() throws Exception {
        config.setMaxTokenLifetimeDays(30);
        add("alice", "never", 1, null);
        add("alice", "compliant", 1, TODAY.plusDays(5));

        Report report = monitor.scan(TODAY);
        assertThat(report.getRows(), hasSize(2));
        Row never = rowOf(report, "alice", "never");
        assertTrue(never.isNeverExpires());
        assertTrue(never.isOverMaximum());
        assertTrue(never.isSharesSecret());
        assertTrue(never.isShortenable());
        Row compliant = rowOf(report, "alice", "compliant");
        assertFalse(compliant.isOverMaximum());
        assertTrue(compliant.isSharesSecret());
        assertFalse(compliant.isShortenable(), "it is compliant on its own");
        assertEquals(1, report.getNonCompliantCount());
        assertEquals(1, report.getSharedSecretGroupCount());
    }

    @Test
    void everyGroupIsCountedOnce() throws Exception {
        config.setMaxTokenLifetimeDays(30);
        for (int i = 0; i < 3; i++) {
            add("alice", "a" + i, 1, TODAY.plusDays(1));
        }
        add("alice", "b0", 2, TODAY.plusDays(1));
        add("alice", "b1", 2, TODAY.plusDays(1));
        add("alice", "alone", 3, TODAY.plusDays(1));
        add("bob", "c0", 4, TODAY.plusDays(1));
        add("bob", "c1", 4, TODAY.plusDays(1));

        Report report = monitor.scan(TODAY);
        assertEquals(3, report.getSharedSecretGroupCount());
        assertThat(report.getRows(), hasSize(7));
        assertEquals(0, report.getNonCompliantCount());
    }

    // ---- the page

    @Test
    void thePageShowsThePhaseAndTheCountdownOfEachPhase() throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient();
        LocalDate now = LocalDate.now();

        assertEquals("No maximum lifetime is configured", phaseLabelOf(managePage(wc)));

        config.setMaxTokenLifetimeDays(30);
        config.setEnforceFrom(now.plusDays(10).toString());
        HtmlPage warning = managePage(wc);
        assertEquals("Warning phase until " + now.plusDays(10), phaseLabelOf(warning));
        assertThat(warning.querySelector(".token-lifetime-countdown").asNormalizedText(), containsString("in 10 days"));

        config.setEnforceFrom(now.minusDays(2).toString());
        HtmlPage since = managePage(wc);
        assertEquals("Enforcing since " + now.minusDays(2), phaseLabelOf(since));
        assertNull(since.querySelector(".token-lifetime-countdown"));

        config.setEnforceFrom(null);
        assertEquals("Enforcing", phaseLabelOf(managePage(wc)));

        config.setEnforceFrom("not-a-date-on-the-panel-page");
        assertEquals("Enforcing (invalid start date in configuration)", phaseLabelOf(managePage(wc)));
    }

    private static String phaseLabelOf(HtmlPage page) {
        return page.querySelector(".token-lifetime-phase-label").asNormalizedText();
    }

    @Test
    void thePageShowsTheMaximumTheCountsAndTheColumns() throws Exception {
        config.setMaxTokenLifetimeDays(30);
        LocalDate now = LocalDate.now();
        add("alice", "never", 1, null);
        add("alice", "over", 2, now.plusDays(100));
        add("bob", "first", 3, now.plusDays(5));
        add("bob", "second", 3, now.plusDays(6));

        HtmlPage page = managePage(j.createWebClient());
        assertEquals("Maximum lifetime of an API token: 30 days.", page.querySelector(".token-lifetime-maximum").asNormalizedText());
        assertEquals("API tokens that do not comply with the maximum lifetime: 2", page.querySelector(".token-lifetime-count-non-compliant").asNormalizedText());
        assertEquals("Secrets shared by several tokens: 1", page.querySelector(".token-lifetime-count-shared-secrets").asNormalizedText());

        List<String> columns = new ArrayList<>();
        for (DomNode header : page.querySelectorAll("table.token-lifetime-table th")) {
            columns.add(header.asNormalizedText());
        }
        assertEquals(List.of("User ID", "User full name", "Token name", "Status", "Created", "Last use", "# of uses", "Actions"), columns);
        assertThat(tableRows(page), hasSize(4));
        assertNull(page.querySelector("tr.no-token-line"));
    }

    @Test
    void thePageIsEmptyWhenThereIsNothingToReport() throws Exception {
        config.setMaxTokenLifetimeDays(30);
        HtmlPage page = managePage(j.createWebClient());
        assertThat(tableRows(page), empty());
        assertNotNull(page.querySelector("tr.no-token-line"));
        assertNotNull(page.querySelector("div.jenkins-table__wrapper > table.token-lifetime-table"), "the empty table is in the wrapper too");
        assertEquals("API tokens that do not comply with the maximum lifetime: 0", page.querySelector(".token-lifetime-count-non-compliant").asNormalizedText());
    }

    @Test
    void aGroupWithOneLiveEntryAndAnExpiredSiblingIsNotListedOnThePage() throws Exception {
        config.setMaxTokenLifetimeDays(30);
        LocalDate now = LocalDate.now();
        add("alice", "live", 1, now.plusDays(10));
        add("alice", "expired", 1, now.minusDays(1));

        HtmlPage page = managePage(j.createWebClient());
        assertThat(tableRows(page), empty());
        assertEquals("Secrets shared by several tokens: 0", page.querySelector(".token-lifetime-count-shared-secrets").asNormalizedText());
    }

    @Test
    void theStatusColumnSaysWhatIsWrongWithEachToken() throws Exception {
        config.setMaxTokenLifetimeDays(30);
        config.setCreationOfLegacyTokenEnabled(true);
        LocalDate now = LocalDate.now();
        String never = add("alice", "never", 1, null);
        String over = add("alice", "over", 2, now.plusDays(100));
        String shared = add("bob", "shared", 3, now.plusDays(5));
        add("bob", "sibling", 3, now.plusDays(6));
        add("bob", "gone", 3, now.minusDays(1));
        String neverShared = add("carol", "never shared", 4, null);
        add("carol", "other", 4, now.plusDays(2));
        propertyOf("dave").changeApiToken();

        HtmlPage page = managePage(j.createWebClient());
        assertEquals("Never expires", text(tableRowOf(page, never), "token-status"));
        assertEquals("Expires on " + now.plusDays(100) + ", 70 days over the maximum", text(tableRowOf(page, over), "token-status"));
        assertEquals("Expires on " + now.plusDays(5) + "\nShares its secret with: sibling, gone (expired)",
                text(tableRowOf(page, shared), "token-status").replace("\r", ""));
        String neverSharedStatus = text(tableRowOf(page, neverShared), "token-status");
        assertThat("both statuses on one row", neverSharedStatus, containsString("Never expires"));
        assertThat(neverSharedStatus, containsString("Shares its secret with: other"));

        List<DomElement> daveRows = tableRows(page).stream().filter(row -> "dave".equals(row.getAttribute("data-user-id"))).toList();
        assertThat(daveRows, hasSize(1));
        assertEquals("Legacy token, never expires", text(daveRows.get(0), "token-status"));

        assertThat(page.querySelector(".token-lifetime-statistics-note").asNormalizedText(), containsString("never used while its secret is in use"));
    }

    @Test
    void theStatisticsOfUseAreShownOrReportedAsDisabled() throws Exception {
        config.setMaxTokenLifetimeDays(30);
        String used = add("alice", "used", 1, null);
        String unused = add("alice", "unused", 2, null);
        assertTrue(propertyOf("alice").matchesPassword(token(1)));

        JenkinsRule.WebClient wc = j.createWebClient();
        HtmlPage page = managePage(wc);
        assertEquals("1", text(tableRowOf(page, used), "token-uses"));
        assertThat(text(tableRowOf(page, used), "token-last-use"), not(containsString("Never used")));
        assertEquals("Never used", text(tableRowOf(page, unused), "token-last-use"));
        assertEquals("0", text(tableRowOf(page, unused), "token-uses"));

        config.setUsageStatisticsEnabled(false);
        page = managePage(wc);
        for (String uuid : List.of(used, unused)) {
            assertEquals("Statistics disabled", text(tableRowOf(page, uuid), "token-last-use"));
            assertEquals("Statistics disabled", text(tableRowOf(page, uuid), "token-uses"));
        }
    }

    @Test
    void anUnknownCreationDateIsShownAsUnknown() throws Exception {
        config.setMaxTokenLifetimeDays(30);
        config.setTokenGenerationOnCreationEnabled(true);
        // migrated from a legacy secret that was there before creation dates were recorded: no creation date
        User.getById("migrated", true);
        String known = add("alice", "known", 1, null);

        HtmlPage page = managePage(j.createWebClient());
        DomElement migrated = tableRows(page).stream().filter(row -> "migrated".equals(row.getAttribute("data-user-id"))).findFirst().orElseThrow();
        assertEquals("Unknown", text(migrated, "token-created"));
        assertThat(text(tableRowOf(page, known), "token-created"), not(containsString("Unknown")));
    }

    @Test
    void rowsAreSortedOnThePage() throws Exception {
        config.setMaxTokenLifetimeDays(30);
        add("carol", "alpha", 1, null);
        add("alice", "Zulu", 2, null);
        add("bob", "x", 3, null);
        add("alice", "ALPHA", 4, null);

        List<String> order = new ArrayList<>();
        for (DomElement row : tableRows(managePage(j.createWebClient()))) {
            order.add(row.getAttribute("data-user-id") + "/" + text(row, "token-name"));
        }
        assertEquals(List.of("alice/ALPHA", "alice/Zulu", "bob/x", "carol/alpha"), order);
    }

    @Test
    void revokeIsOfferedOnEveryRowAndShortenOnlyWhereItApplies() throws Exception {
        config.setMaxTokenLifetimeDays(30);
        config.setCreationOfLegacyTokenEnabled(true);
        LocalDate now = LocalDate.now();
        String never = add("alice", "never", 1, null);
        String groupFirst = add("bob", "group first", 2, now.plusDays(5));
        String groupSecond = add("bob", "group second", 2, now.plusDays(6));
        String over = add("carol", "over", 3, now.plusDays(100));
        String compliantSibling = add("carol", "compliant sibling", 3, now.plusDays(2));
        propertyOf("dave").changeApiToken();
        // the legacy token of dave shares its secret with a fixed token that is over the maximum: still never Shorten for it
        ApiTokenProperty dave = propertyOf("dave");
        String legacyShared = dave.addFixedNewToken("fixed like legacy", "11" + dave.getApiToken(), null);

        HtmlPage page = managePage(j.createWebClient());
        assertEquals(List.of("revoke", "shorten"), buttonActions(tableRowOf(page, never)));
        assertEquals(List.of("revoke"), buttonActions(tableRowOf(page, groupFirst)));
        assertEquals(List.of("revoke"), buttonActions(tableRowOf(page, groupSecond)));
        assertEquals(List.of("revoke", "shorten"), buttonActions(tableRowOf(page, over)));
        assertEquals(List.of("revoke"), buttonActions(tableRowOf(page, compliantSibling)));
        assertEquals(List.of("revoke", "shorten"), buttonActions(tableRowOf(page, legacyShared)));
        for (DomElement row : tableRows(page)) {
            if ("dave".equals(row.getAttribute("data-user-id")) && !legacyShared.equals(row.getAttribute("data-uuid"))) {
                assertEquals(List.of("revoke"), buttonActions(row), "never Shorten for the legacy token");
            }
        }

        DomElement revoke = (DomElement) tableRowOf(page, never).querySelector("button[data-token-action=revoke]");
        assertThat(revoke.getAttribute("data-url"), endsWith(PANEL_URL + "/revoke"));
        assertEquals("Revoke API token", revoke.getAttribute("data-confirm-title"));
        assertEquals("Revoke", revoke.getAttribute("data-ok-text"));
        assertEquals("Cancel", revoke.getAttribute("data-cancel-text"));
        assertThat(revoke.getAttribute("data-confirm-message"), containsString("will stop working"));
        assertThat(revoke.getAttribute("data-confirm-message-shared"), containsString("every one of them that has not expired"));
        DomElement shorten = (DomElement) tableRowOf(page, never).querySelector("button[data-token-action=shorten]");
        assertThat(shorten.getAttribute("data-url"), endsWith(PANEL_URL + "/shorten"));
        assertEquals("Shorten to maximum", shorten.getAttribute("data-ok-text"));
    }

    /**
     * The buttons of a table have a negative vertical margin, so two of them that are not in a row of buttons wrap and overlap in a
     * narrow cell: the buttons of a row sit in one row of buttons, which puts them side by side with a gap. The table is in a wrapper
     * that scrolls on its own, so that a narrow window does not scroll the whole page. HtmlUnit does not lay anything out: how both
     * look is checked in a browser.
     */
    @Test
    void theButtonsOfARowAreInOneRowOfButtonsAndTheTableIsInAScrollingWrapper() throws Exception {
        config.setMaxTokenLifetimeDays(30);
        config.setCreationOfLegacyTokenEnabled(true);
        LocalDate now = LocalDate.now();
        String never = add("alice", "never", 1, null);
        String groupFirst = add("bob", "group first", 2, now.plusDays(5));
        add("bob", "group second", 2, now.plusDays(6));
        propertyOf("dave").changeApiToken();

        HtmlPage page = managePage(j.createWebClient());
        assertEquals(List.of("revoke", "shorten"), buttonActions(tableRowOf(page, never)));
        assertEquals(List.of("revoke"), buttonActions(tableRowOf(page, groupFirst)));
        assertThat(tableRows(page), hasSize(4));
        for (DomElement row : tableRows(page)) {
            DomNode cell = row.querySelector("td.token-actions");
            assertNotNull(cell);
            assertEquals(1, cell.querySelectorAll(".jenkins-buttons-row").size(), "one row of buttons in the cell of the actions");
            DomNode buttonRow = cell.querySelector("div.jenkins-buttons-row");
            assertNotNull(buttonRow);
            assertSame(cell, buttonRow.getParentNode(), "the row of buttons is a direct child of the cell");
            List<DomNode> buttons = new ArrayList<>();
            for (DomNode button : row.querySelectorAll("button[data-token-action]")) {
                buttons.add(button);
                assertSame(buttonRow, button.getParentNode(), "every button of the row is in the row of buttons");
            }
            assertEquals(buttonActions(row).size(), buttons.size());
            assertEquals(buttons.size(), buttonRow.querySelectorAll("button[data-token-action]").size(), "and the row of buttons holds nothing else");
        }
        assertEquals(2, tableRowOf(page, never).querySelectorAll("div.jenkins-buttons-row > button[data-token-action]").size(), "Revoke and Shorten in the same row");

        assertEquals(1, page.querySelectorAll("div.jenkins-table__wrapper").size());
        DomNode table = page.querySelector("table.token-lifetime-table");
        assertNotNull(table);
        assertSame(page.querySelector("div.jenkins-table__wrapper"), table.getParentNode(), "the table is in the wrapper that scrolls");
        // with pane, the table has collapsed borders and the border that jenkins-table draws sticks out of it: the wrapper would scroll by 2 pixels
        assertThat(List.of(((DomElement) table).getAttribute("class").split("\\s+")), not(hasItem("pane")));
    }

    /**
     * The monitor shows in the breadcrumbs by its display name, and the page adds its own item. The style sheet limits an item to
     * {@code max-width: 25ch} ({@code _breadcrumbs.scss}), roughly 25 characters, as {@code ch} is the width of a zero: the display name
     * used to be the title of the page, which is 26 characters long, and the trail repeated it.
     */
    @Test
    void theBreadcrumbsShowTheShortNameOnceAndTheHeadingOfThePageKeepsItsText() throws Exception {
        config.setMaxTokenLifetimeDays(30);
        add("alice", "never", 1, null);

        HtmlPage page = managePage(j.createWebClient());
        List<String> crumbs = new ArrayList<>();
        for (DomNode crumb : page.querySelectorAll("#breadcrumbs li")) {
            crumbs.add(crumb.getTextContent().trim());
        }
        assertEquals(List.of("API token lifetime", "Manage"), crumbs);
        for (String crumb : crumbs) {
            assertTrue(crumb.length() <= 25, "not cut short by the style sheet: " + crumb);
        }
        assertEquals("API token maximum lifetime", page.querySelector("div.api-token-lifetime h1").asNormalizedText());
        assertThat(page.getTitleText(), startsWith("API token maximum lifetime"));
    }

    /** The administrative monitors that the System page lists by their display name, with the description of the monitor under it. */
    @Test
    void theListOfAdministrativeMonitorsShowsTheShortNameWithItsDescription() throws Exception {
        HtmlPage page = j.createWebClient().goTo("configure");
        DomNode checkbox = page.querySelector("input[name=administrativeMonitor][json=apiTokenLifetime]");
        assertNotNull(checkbox, "the monitor is listed");
        assertEquals("API token lifetime", checkbox.getParentNode().querySelector("label").getTextContent().trim());
        DomNode entry = checkbox.getParentNode().getParentNode().getParentNode();
        assertThat(entry.querySelector(".jenkins-checkbox__description").getTextContent().trim(), startsWith("Warns administrators about the API tokens"));
    }

    @Test
    void thePageDoesNotUseWhatTheJavaScriptOfCoreInterpretsNorInlineScripts() throws Exception {
        config.setMaxTokenLifetimeDays(30);
        add("alice", "never", 1, null);
        add("alice", "shared a", 2, LocalDate.now().plusDays(1));
        add("alice", "shared b", 2, LocalDate.now().plusDays(1));

        HtmlPage page = managePage(j.createWebClient());
        DomElement panel = (DomElement) page.querySelector("div.api-token-lifetime");
        assertNotNull(panel);
        String xml = panel.asXml();
        assertThat(xml, not(containsString("dialog-opener")));
        assertThat(xml, not(containsString("data-dialog-url")));
        assertThat(xml, not(containsString("data-post-href")));
        assertThat(xml, not(containsString("data-html-tooltip")));
        assertFalse(Pattern.compile("\\son[a-z]+=").matcher(xml).find(), "no on* attribute");
        assertEquals(0, panel.getElementsByTagName("script").size());
    }

    // ---- who can see what

    @Test
    void onlyAdministratorsReachThePage() throws Exception {
        setUpSecurity();
        config.setMaxTokenLifetimeDays(30);
        add("alice", "never", 1, null);

        for (String user : new String[] {null, "reader", "sysreader"}) {
            JenkinsRule.WebClient wc = user == null ? j.createWebClient() : j.createWebClient().withBasicCredentials(user);
            for (String url : new String[] {PANEL_URL + "/manage", PANEL_URL}) {
                FailingHttpStatusCodeException e = assertThrows(FailingHttpStatusCodeException.class, () -> wc.goTo(url), url + " as " + user);
                assertEquals(403, e.getStatusCode(), url + " as " + user);
            }
        }

        HtmlPage page = managePage(j.createWebClient().withBasicCredentials("admin"));
        assertThat(tableRows(page), hasSize(1));
    }

    @Test
    void theBannerIsOnlyShownToAdministrators() throws Exception {
        setUpSecurity();
        config.setMaxTokenLifetimeDays(30);
        add("alice", "never", 1, null);
        add("bob", "first", 2, LocalDate.now().plusDays(1));
        add("bob", "second", 2, LocalDate.now().plusDays(1));

        HtmlPage admin = j.createWebClient().withBasicCredentials("admin").goTo("manage");
        DomElement banner = (DomElement) admin.querySelector("[data-monitor-id='apiTokenLifetime']");
        assertNotNull(banner);
        assertThat(banner.asNormalizedText(), containsString("Enforcing. API tokens that do not comply with the maximum lifetime: 1. Secrets shared by several tokens: 1."));
        HtmlAnchor link = (HtmlAnchor) banner.querySelector("a");
        assertThat(link.getHrefAttribute(), endsWith(PANEL_URL + "/manage"));
        assertThat(link.click().getWebResponse().getContentAsString(), containsString("token-lifetime-table"));

        HtmlPage sysReader = j.createWebClient().withBasicCredentials("sysreader").goTo("manage");
        assertNull(sysReader.querySelector("[data-monitor-id='apiTokenLifetime']"));
    }

    @Test
    void theBannerIsNotShownWhenThereIsNothingToReport() throws Exception {
        config.setMaxTokenLifetimeDays(30);
        add("alice", "compliant", 1, LocalDate.now().plusDays(1));
        assertNull(j.createWebClient().goTo("manage").querySelector("[data-monitor-id='apiTokenLifetime']"));
    }

    // ---- the sink policy: what users choose is only ever shown as text

    @Test
    void whatUsersChooseIsOnlyEverShownAsText() throws Exception {
        config.setMaxTokenLifetimeDays(30);
        User evil = User.getById(EVIL_ID, true);
        evil.setFullName(EVIL_FULL_NAME);
        evil.save();
        String uuid = add(EVIL_ID, EVIL_TOKEN_NAME, 1, null);
        add(EVIL_ID, "sibling " + EVIL_TOKEN_NAME, 1, LocalDate.now().plusDays(1));

        List<String> alerts = new ArrayList<>();
        JenkinsRule.WebClient wc = j.createWebClient();
        wc.setAlertHandler((page, message) -> alerts.add(message));

        HtmlPage page = managePage(wc);
        String html = page.getWebResponse().getContentAsString();
        assertThat(alerts, empty());
        assertThat(html, not(containsString("<script>alert(1)</script>")));
        assertThat(html, not(containsString("<img src=x")));
        assertThat(html, not(containsString("<b>")));
        assertEquals(0, page.querySelectorAll("img[src='x']").size());
        for (DomElement script : page.getElementsByTagName("script")) {
            assertThat(script.getTextContent(), not(containsString("alert(1)")));
        }

        DomElement row = tableRowOf(page, uuid);
        assertEquals(EVIL_ID, row.getAttribute("data-user-id"));
        assertEquals(EVIL_ID, row.querySelector("td.token-user-id").getTextContent().trim());
        assertEquals(EVIL_FULL_NAME, row.querySelector("td.token-user-full-name").getTextContent().trim());
        assertEquals(EVIL_TOKEN_NAME, row.querySelector("td.token-name").getTextContent().trim());
        // the names that Revoke removes with this token, as the JSON array that the script reads, whose entries are the raw names
        JSONArray sharedWith = JSONArray.fromObject(row.getAttribute("data-shared-with"));
        assertEquals(1, sharedWith.size());
        assertEquals("sibling " + EVIL_TOKEN_NAME, sharedWith.getString(0));
        assertEquals("true", row.getAttribute("data-shares-secret"));
        assertEquals("Shares its secret with: sibling " + EVIL_TOKEN_NAME, row.querySelector(".token-status-shared").getTextContent().trim());
        assertThat(((HtmlAnchor) row.querySelector("td.token-user-id a")).getHrefAttribute(), endsWith("/" + evil.getUrl()));
        assertThat(evil.getUrl(), not(containsString("<")));
        assertThat(evil.getUrl(), not(containsString("\"")));

        // the banner has no user string at all
        HtmlPage manage = wc.goTo("manage");
        DomElement banner = (DomElement) manage.querySelector("[data-monitor-id='apiTokenLifetime']");
        assertNotNull(banner);
        String bannerXml = banner.asXml();
        assertThat(bannerXml, not(containsString("alert(1)")));
        assertThat(bannerXml, not(containsString("ev\"il")));
        assertThat(bannerXml, not(containsString("onerror")));
        assertThat(bannerXml, not(containsString("$&")));
        assertThat(alerts, empty());
    }

    // ---- the link and the help

    @Test
    void theConfigurationLinksToThePanelOnlyWhileAMaximumIsConfigured() throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient();
        String selector = "a[href$='" + PANEL_URL + "/manage']";
        assertNull(wc.goTo("configureSecurity").querySelector(selector));

        config.setMaxTokenLifetimeDays(30);
        HtmlPage page = wc.goTo("configureSecurity");
        HtmlAnchor link = (HtmlAnchor) page.querySelector(selector);
        assertNotNull(link);
        assertEquals("Review the API tokens that exceed the maximum lifetime or share a secret", link.asNormalizedText());
        assertThat(link.click().getWebResponse().getContentAsString(), containsString("token-lifetime-table"));

        config.setMaxTokenLifetimeDays(0);
        assertNull(wc.goTo("configureSecurity").querySelector(selector));
    }

    /**
     * The page of the panel needs ADMINISTER, so the link to it is for administrators: a user who can only read the Security page
     * (SYSTEM_READ) sees the section of the maximum, without the link that would lead to a 403.
     */
    @Test
    void theLinkToThePanelIsOnlyShownToAdministrators() throws Exception {
        setUpSecurity();
        config.setMaxTokenLifetimeDays(30);
        String selector = "a[href$='" + PANEL_URL + "/manage']";
        String maximumField = "Maximum lifetime of an API token (days)";

        HtmlPage admin = j.createWebClient().withBasicCredentials("admin").goTo("configureSecurity");
        assertThat(admin.asNormalizedText(), containsString(maximumField));
        HtmlAnchor link = (HtmlAnchor) admin.querySelector(selector);
        assertNotNull(link, "an administrator sees the link while a maximum is configured");
        assertThat(link.click().getWebResponse().getContentAsString(), containsString("token-lifetime-table"));

        HtmlPage sysReader = j.createWebClient().withBasicCredentials("sysreader").goTo("configureSecurity");
        assertThat("the user can read the section", sysReader.asNormalizedText(), containsString(maximumField));
        assertNull(sysReader.querySelector(selector), "the link is not shown to a user who cannot follow it");
        assertThat(sysReader.asNormalizedText(), not(containsString("Review the API tokens that exceed the maximum lifetime")));
    }

    @Test
    void theHelpOfTheMaximumMentionsThePanelAndThatDismissingItsBannerHidesItForEveryAdministrator() throws Exception {
        String help = j.createWebClient()
                .goTo("descriptorByName/" + ApiTokenPropertyConfiguration.class.getName() + "/help/maxTokenLifetimeDays", "text/html")
                .getWebResponse().getContentAsString()
                .replaceAll("\\s+", " ");
        assertThat(help, containsString("compliance panel lists the tokens that exceed the maximum"));
        assertThat(help, containsString("secrets that are shared by"));
        assertThat(help, containsString("Dismissing that banner hides it for every administrator, until the monitor is enabled again under "
                + "Manage Jenkins &gt; System &gt; Administrative monitors."));
        assertThat(help, containsString("The panel itself stays reachable through the link in this section."));
        assertThat(help, not(containsString("for good")));
        assertThat(help, not(containsString("hides these violations")));
    }

    @Test
    void dismissingTheBannerHidesItButNotThePage() throws Exception {
        config.setMaxTokenLifetimeDays(30);
        add("alice", "never", 1, null);
        JenkinsRule.WebClient wc = j.createWebClient();
        assertNotNull(wc.goTo("manage").querySelector("[data-monitor-id='apiTokenLifetime']"));

        monitor.disable(true);

        assertTrue(monitor.isActivated(), "the findings are still there");
        assertNull(wc.goTo("manage").querySelector("[data-monitor-id='apiTokenLifetime']"));
        assertThat(tableRows(managePage(wc)), hasSize(1));
    }

    // ---- the actions: helpers

    /** A client that is answered with the status, not an exception, logged in as {@code user} (its password is its ID), or anonymous. */
    private JenkinsRule.WebClient client(@CheckForNull String user) throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient().withThrowExceptionOnFailingStatusCode(false);
        // the answer to a user who may not is a 403 with a page that redirects to the login page: the status is what is checked
        wc.setRefreshHandler((page, url, seconds) -> { });
        wc.getOptions().setRedirectEnabled(false);
        return user == null ? wc : wc.login(user);
    }

    private JenkinsRule.WebClient adminClient() throws Exception {
        setUpSecurity();
        return client("admin");
    }

    private static String quoted(@CheckForNull String value) {
        return value == null ? "null" : JSONUtils.quote(value);
    }

    /** The body that the page sends. */
    private static String json(@CheckForNull String userId, @CheckForNull String uuid) {
        return "{\"userId\":" + quoted(userId) + ",\"uuid\":" + quoted(uuid) + "}";
    }

    private WebResponse post(JenkinsRule.WebClient wc, String url, String contentType, String body, boolean withCrumb) throws Exception {
        WebRequest request = new WebRequest(new URL(j.getURL(), url), HttpMethod.POST);
        request.setAdditionalHeader("Content-Type", contentType);
        // the bytes, as fetch sends them, whatever the content type says
        request.setCharset(StandardCharsets.UTF_8);
        request.setRequestBody(body);
        if (withCrumb) {
            // as JenkinsRule.WebClient#addCrumb does, but in a header: that one adds a parameter, which a body hides
            CrumbIssuer issuer = j.jenkins.getCrumbIssuer();
            request.setAdditionalHeader(issuer.getDescriptor().getCrumbRequestField(), issuer.getCrumb((ServletRequest) null));
        }
        // no scripts: the 403 for a user who may not is a page that sends the browser to the login page with one
        boolean scripts = wc.getOptions().isJavaScriptEnabled();
        wc.getOptions().setJavaScriptEnabled(false);
        try {
            return wc.getPage(request).getWebResponse();
        } finally {
            wc.getOptions().setJavaScriptEnabled(scripts);
        }
    }

    private WebResponse post(JenkinsRule.WebClient wc, String url, String body, boolean withCrumb) throws Exception {
        return post(wc, url, "application/json", body, withCrumb);
    }

    private WebResponse revoke(JenkinsRule.WebClient wc, String userId, String uuid) throws Exception {
        return post(wc, REVOKE_URL, json(userId, uuid), true);
    }

    private WebResponse shorten(JenkinsRule.WebClient wc, String userId, String uuid) throws Exception {
        return post(wc, SHORTEN_URL, json(userId, uuid), true);
    }

    private static void assertDone(WebResponse response) {
        assertEquals(200, response.getStatusCode(), response.getContentAsString());
        assertEquals("ok", JSONObject.fromObject(response.getContentAsString()).getString("status"));
    }

    /** The answer to a request that was refused: the status and the static message, no stack trace, nothing of the request. */
    private static void assertRefused(int status, String message, WebResponse response) {
        String content = response.getContentAsString();
        assertEquals(status, response.getStatusCode(), content);
        assertThat(content, containsString(message));
        assertThat(content, not(containsString("Exception")));
        assertThat(content, not(containsString("\tat ")));
        assertThat(content, not(containsString("Stack trace")));
    }

    private static List<String> tokenUuids(String userId) {
        return User.getById(userId, false).getProperty(ApiTokenProperty.class).getTokenStore().getTokenListSnapshot().stream()
                .map(ApiTokenStore.HashedToken::getUuid)
                .toList();
    }

    private static @CheckForNull LocalDate expirationOf(String userId, String uuid) {
        return User.getById(userId, false).getProperty(ApiTokenProperty.class).getTokenStore().getTokenListSnapshot().stream()
                .filter(token -> token.getUuid().equals(uuid))
                .findFirst().orElseThrow()
                .getExpirationDate();
    }

    /** What a refused request must leave as it was: the tokens with their expiration, and the file of the user. */
    private record State(List<String> tokens, @CheckForNull String configXml) {}

    private static State stateOf(String userId) throws IOException {
        User user = User.getById(userId, false);
        List<String> tokens = user.getProperty(ApiTokenProperty.class).getTokenStore().getTokenListSnapshot().stream()
                .map(token -> token.getUuid() + "/" + token.getExpirationDate())
                .toList();
        File configFile = new File(user.getRootDir(), "config.xml");
        return new State(tokens, configFile.exists() ? Files.readString(configFile.toPath(), StandardCharsets.UTF_8) : null);
    }

    /** The status of a request that authenticates with the token: 200 if it works, 401 if it does not. */
    private int statusOfAuthenticating(String userId, String plainValue) throws Exception {
        return j.createWebClient().withBasicCredentials(userId, plainValue)
                .withThrowExceptionOnFailingStatusCode(false)
                .goTo("whoAmI/api/xml", null)
                .getWebResponse().getStatusCode();
    }

    /** Registers a secret through the endpoint that the UI uses, as the user, so that the policy and the known secrets apply. */
    private JSONObject addFixedTokenThroughHttp(String userId, String name, String plainValue, int days) throws Exception {
        JenkinsRule.WebClient wc = client(userId);
        WebRequest request = new WebRequest(
                new URL(j.getURL(), "user/" + userId + "/descriptorByName/" + ApiTokenProperty.class.getName() + "/addFixedToken"),
                HttpMethod.POST);
        request.setRequestParameters(List.of(
                new NameValuePair("newTokenName", name),
                new NameValuePair("newTokenPlainValue", plainValue),
                new NameValuePair("expirationDuration", Integer.toString(days))));
        return JSONObject.fromObject(wc.getPage(wc.addCrumb(request)).getWebResponse().getContentAsString());
    }

    /** The messages that were logged at INFO or above, the oldest first (the recorder lists the newest first). */
    private List<String> infoMessages() {
        SimpleFormatter formatter = new SimpleFormatter();
        List<String> messages = new ArrayList<>(logging.getRecords().stream()
                .filter(record -> record.getLevel().intValue() >= Level.INFO.intValue())
                .map(formatter::formatMessage)
                .toList());
        Collections.reverse(messages);
        return messages;
    }

    // ---- Revoke

    @Test
    void revokeRemovesATokenThatDoesNotShareItsSecretAndNothingElse() throws Exception {
        JenkinsRule.WebClient admin = adminClient();
        config.setMaxTokenLifetimeDays(30);
        String target = add("alice", "target", 1, null);
        String other = add("alice", "other", 2, null);
        String bobs = add("bob", "bobs", 1, null);
        assertEquals(200, statusOfAuthenticating("alice", token(1)));
        ApiTokenProperty alice = propertyOf("alice");
        assertEquals(1, alice.getTokenStats().findTokenStatsById(target).getUseCounter());

        WebResponse response = revoke(admin, "alice", target);

        assertDone(response);
        assertEquals(List.of(other), tokenUuids("alice"));
        assertEquals(List.of(bobs), tokenUuids("bob"), "the same secret of another user is another credential");
        assertEquals(401, statusOfAuthenticating("alice", token(1)));
        assertEquals(200, statusOfAuthenticating("alice", token(2)));
        assertEquals(0, alice.getTokenStats().findTokenStatsById(target).getUseCounter(), "the statistics go with the token");

        // persisted
        User.getById("alice", false).load();
        assertEquals(List.of(other), tokenUuids("alice"));
    }

    /**
     * S14. Given a group of three entries with one secret, two live and one expired, created through the Java API, when Revoke is
     * posted for one live row, then both live entries are removed, the expired one stays, the secret no longer authenticates and
     * adding it again is still rejected.
     */
    @Test
    void s14RevokeRemovesTheLiveEntriesOfAGroupKeepsTheExpiredOneAndTheSecretCannotBeRegisteredAgain() throws Exception {
        JenkinsRule.WebClient admin = adminClient();
        config.setMaxTokenLifetimeDays(30);
        LocalDate now = LocalDate.now();
        String first = add("alice", "first", 1, null);
        String second = add("alice", "second", 1, now.plusDays(5));
        String expired = add("alice", "expired", 1, now.minusDays(1));
        String other = add("alice", "other", 2, now.plusDays(5));
        assertEquals(200, statusOfAuthenticating("alice", token(1)));

        assertDone(revoke(admin, "alice", second));

        assertEquals(List.of(expired, other), tokenUuids("alice"), "both live entries are gone, the expired one and the other secret stay");
        assertEquals(401, statusOfAuthenticating("alice", token(1)));
        assertEquals(200, statusOfAuthenticating("alice", token(2)));
        // the expired entry still holds the secret, so it cannot be registered again while the policy is enforcing
        JSONObject again = addFixedTokenThroughHttp("alice", "again", token(1), 10);
        assertEquals("error", again.getString("status"), again.toString());
        assertEquals(jenkins.security.Messages.ApiTokenProperty_SecretAlreadyRegistered(), again.getString("message"));
        assertEquals(List.of(expired, other), tokenUuids("alice"));
        assertThat(tokenUuids("alice"), not(hasItem(first)));

        User.getById("alice", false).load();
        assertEquals(List.of(expired, other), tokenUuids("alice"));
        assertThat(tableRows(managePage(admin)), empty());
    }

    /**
     * S15. Given a page rendered with one entry for a secret, which is not compliant so it has a row, when a sibling for the secret
     * is added and then Revoke is posted for the first, then the sibling is revoked too.
     */
    @Test
    void s15TheGroupIsWorkedOutWhenTheRevokeArrivesAndNotWhenThePageWasRendered() throws Exception {
        JenkinsRule.WebClient admin = adminClient();
        config.setMaxTokenLifetimeDays(30);
        String first = add("alice", "first", 1, null);
        HtmlPage page = managePage(admin);
        DomElement row = tableRowOf(page, first);
        assertEquals("false", row.getAttribute("data-shares-secret"), "when the page was rendered, nothing else had the secret");
        assertEquals(0, JSONArray.fromObject(row.getAttribute("data-shared-with")).size());

        String late = add("alice", "late sibling", 1, LocalDate.now().plusDays(5));
        assertEquals(200, statusOfAuthenticating("alice", token(1)));

        assertDone(revoke(admin, row.getAttribute("data-user-id"), row.getAttribute("data-uuid")));

        assertThat(tokenUuids("alice"), empty());
        assertEquals(401, statusOfAuthenticating("alice", token(1)));
        assertThat(tokenUuids("alice"), not(hasItem(late)));
    }

    @Test
    void revokeOfAnExpiredTargetRemovesItAndTheLiveEntriesThatShareItsSecret() throws Exception {
        JenkinsRule.WebClient admin = adminClient();
        config.setMaxTokenLifetimeDays(30);
        LocalDate now = LocalDate.now();
        String target = add("alice", "target", 1, now.minusDays(2));
        add("alice", "live", 1, now.plusDays(3));
        add("alice", "live too", 1, null);
        String olderSibling = add("alice", "expired sibling", 1, now.minusDays(9));
        String other = add("alice", "other", 2, null);

        // a row that expired between the rendering and the click is still removed, with the live entries of its secret
        assertDone(revoke(admin, "alice", target));

        assertEquals(List.of(olderSibling, other), tokenUuids("alice"), "the expired sibling is kept, as it cannot be used");
        assertEquals(401, statusOfAuthenticating("alice", token(1)));
    }

    /**
     * S17. Given an owner with a legacy token W and a fixed token {@code 11}+W, which the panel lists as a group, when an administrator
     * revokes the legacy token in the legacy token monitor, then {@code 11}+W still authenticates and is no longer a group: it is
     * listed only because it does not comply on its own.
     */
    @Test
    void s17TheLegacyMonitorRevokesOneEntryOfAGroupAndThePanelThenListsTheOtherOneAlone() throws Exception {
        JenkinsRule.WebClient admin = adminClient();
        config.setMaxTokenLifetimeDays(30);
        config.setCreationOfLegacyTokenEnabled(true);
        ApiTokenProperty alice = propertyOf("alice");
        alice.changeApiToken();
        String legacyValue = alice.getApiToken();
        String legacyUuid = alice.getTokenStore().getLegacyToken().getUuid();
        // the legacy secret as a token of the current version: both hold one secret
        String fixed = alice.addFixedNewToken("fixed", "11" + legacyValue, null);

        Report before = monitor.scan(LocalDate.now());
        assertEquals(1, before.getSharedSecretGroupCount(), "the panel lists the pair as a group");
        assertThat(rowsOf(before, "alice"), hasSize(2));
        assertTrue(rowOf(before, "alice", "fixed").isSharesSecret());

        // the single revoke of the page of the legacy API token usage
        WebResponse response = post(admin, "administrativeMonitor/legacyApiToken/revokeAllSelected", "{\"values\":[" + json("alice", legacyUuid) + "]}", true);
        assertEquals(200, response.getStatusCode());

        assertEquals(List.of(fixed), tokenUuids("alice"));
        assertEquals(200, statusOfAuthenticating("alice", "11" + legacyValue), "the other entry still works");
        Report after = monitor.scan(LocalDate.now());
        assertEquals(0, after.getSharedSecretGroupCount());
        assertThat(rowsOf(after, "alice"), hasSize(1));
        Row remaining = rowOf(after, "alice", "fixed");
        assertFalse(remaining.isSharesSecret(), "it is not a group any more");
        assertTrue(remaining.isOverMaximum(), "it is listed because it does not comply on its own");
    }

    /**
     * S20. Given an owner with a legacy token W and a fixed token {@code 11}+W, a group, when the panel's Revoke is posted for the
     * group and the user is reloaded, then the legacy token is not resurrected and {@code 11}+W no longer authenticates.
     */
    @Test
    void s20RevokeOfAGroupThatHoldsTheLegacyTokenDoesNotBringTheLegacyTokenBack() throws Exception {
        JenkinsRule.WebClient admin = adminClient();
        config.setMaxTokenLifetimeDays(30);
        config.setCreationOfLegacyTokenEnabled(true);

        // once from the row of the fixed token, once from the row of the legacy token
        for (boolean fromTheLegacyRow : new boolean[] {false, true}) {
            String userId = fromTheLegacyRow ? "bob" : "alice";
            User user = User.getById(userId, true);
            ApiTokenProperty property = user.getProperty(ApiTokenProperty.class);
            property.changeApiToken();
            String legacyValue = property.getApiToken();
            String legacyUuid = property.getTokenStore().getLegacyToken().getUuid();
            String fixed = property.addFixedNewToken("fixed", "11" + legacyValue, LocalDate.now().plusDays(5));
            assertEquals(200, statusOfAuthenticating(userId, legacyValue), userId);
            assertEquals(200, statusOfAuthenticating(userId, "11" + legacyValue), userId);

            assertDone(revoke(admin, userId, fromTheLegacyRow ? legacyUuid : fixed));

            assertFalse(property.hasLegacyToken(), userId);
            assertThat(tokenUuids(userId), empty());
            // what happens at the next start: the legacy token is created again from its secret, unless that was dropped
            user.load();
            ApiTokenProperty reloaded = user.getProperty(ApiTokenProperty.class);
            assertFalse(reloaded.hasLegacyToken(), userId + " the legacy token must not be resurrected");
            assertThat(reloaded.getTokenStore().getTokenListSnapshot(), empty());
            assertEquals(401, statusOfAuthenticating(userId, legacyValue), userId);
            assertEquals(401, statusOfAuthenticating(userId, "11" + legacyValue), userId);
        }
    }

    @Test
    void revokeOfAUserThatWasNeverSavedSavesTheUser() throws Exception {
        JenkinsRule.WebClient admin = adminClient();
        config.setMaxTokenLifetimeDays(30);
        config.setTokenGenerationOnCreationEnabled(true);
        User phantom = User.getById("phantom", true);
        ApiTokenProperty property = phantom.getProperty(ApiTokenProperty.class);
        assertTrue(property.hasLegacyToken());
        File configFile = new File(phantom.getRootDir(), "config.xml");
        assertFalse(configFile.exists(), "nothing saved this user yet");

        assertDone(revoke(admin, "phantom", property.getTokenStore().getLegacyToken().getUuid()));

        assertFalse(property.hasLegacyToken());
        assertTrue(configFile.exists(), "the revocation must persist, and that saves the user");
        phantom.load();
        assertFalse(phantom.getProperty(ApiTokenProperty.class).hasLegacyToken());
    }

    @Test
    void aUserWhoseIdIsTheTextNullCanBeRevoked() throws Exception {
        JenkinsRule.WebClient admin = adminClient();
        config.setMaxTokenLifetimeDays(30);
        add("null", "never", 1, null);
        String uuid = tokenUuids("null").get(0);

        // the JSON string "null" is the ID, not a missing one, which the legacy monitor has to work around
        assertDone(revoke(admin, "null", uuid));

        assertThat(tokenUuids("null"), empty());
    }

    @Test
    void revokeWorksWhateverThePolicyIs() throws Exception {
        JenkinsRule.WebClient admin = adminClient();
        String uuid = add("alice", "never", 1, null);
        assertFalse(new ApiTokenLifetimePolicy().isActive());

        assertDone(revoke(admin, "alice", uuid));

        assertThat(tokenUuids("alice"), empty());
    }

    @Test
    void aSecondRevokeOfTheSameTokenIsAnswered404() throws Exception {
        JenkinsRule.WebClient admin = adminClient();
        config.setMaxTokenLifetimeDays(30);
        String uuid = add("alice", "never", 1, null);
        add("alice", "other", 2, null);
        assertDone(revoke(admin, "alice", uuid));
        State state = stateOf("alice");

        assertRefused(404, Messages.ApiTokenLifetimeAdministrativeMonitor_Action_NotFound(), revoke(admin, "alice", uuid));

        assertEquals(state, stateOf("alice"));
    }

    // ---- Shorten

    @Test
    void shortenMakesTheExpirationTodayPlusTheMaximumAndPersistsIt() throws Exception {
        JenkinsRule.WebClient admin = adminClient();
        config.setMaxTokenLifetimeDays(30);
        LocalDate now = LocalDate.now();
        String never = add("alice", "never", 1, null);
        String over = add("alice", "over", 2, now.plusDays(100));
        String compliant = add("alice", "compliant", 3, now.plusDays(10));

        assertDone(shorten(admin, "alice", never));
        assertDone(shorten(admin, "alice", over));

        assertEquals(now.plusDays(30), expirationOf("alice", never), "no expiration counts as the furthest one");
        assertEquals(now.plusDays(30), expirationOf("alice", over));
        assertEquals(now.plusDays(10), expirationOf("alice", compliant), "the tokens that were not asked for are not touched");
        assertEquals(200, statusOfAuthenticating("alice", token(1)), "a shortened token still works until it expires");

        User.getById("alice", false).load();
        assertEquals(now.plusDays(30), expirationOf("alice", never));
        assertEquals(now.plusDays(30), expirationOf("alice", over));
        assertFalse(monitor.isActivated(), "nothing is left to report");
        assertThat(tableRows(managePage(admin)), empty());
    }

    @Test
    void shortenAlsoWorksInTheWarningPhase() throws Exception {
        JenkinsRule.WebClient admin = adminClient();
        config.setMaxTokenLifetimeDays(30);
        config.setEnforceFrom(LocalDate.now().plusDays(10).toString());
        String never = add("alice", "never", 1, null);

        assertDone(shorten(admin, "alice", never));

        assertEquals(LocalDate.now().plusDays(30), expirationOf("alice", never));
    }

    @Test
    void shortenRefusesATokenThatAlreadyCompliesOrHasExpiredAndChangesNothing() throws Exception {
        JenkinsRule.WebClient admin = adminClient();
        config.setMaxTokenLifetimeDays(30);
        LocalDate now = LocalDate.now();
        String boundary = add("alice", "boundary", 1, now.plusDays(30));
        String soon = add("alice", "soon", 2, now.plusDays(1));
        String today = add("alice", "last day", 3, now);
        String expired = add("alice", "expired", 4, now.minusDays(1));
        State state = stateOf("alice");

        for (String uuid : List.of(boundary, soon, today, expired)) {
            assertRefused(409, Messages.ApiTokenLifetimeAdministrativeMonitor_Action_ShortenNotNeeded(), shorten(admin, "alice", uuid));
        }

        assertEquals(state, stateOf("alice"), "not even the file of the user was written");
    }

    @Test
    void shortenRefusesTheLegacyTokenAndThePageDoesNotOfferIt() throws Exception {
        JenkinsRule.WebClient admin = adminClient();
        config.setMaxTokenLifetimeDays(30);
        config.setCreationOfLegacyTokenEnabled(true);
        ApiTokenProperty alice = propertyOf("alice");
        alice.changeApiToken();
        String legacyUuid = alice.getTokenStore().getLegacyToken().getUuid();
        State state = stateOf("alice");

        assertEquals(List.of("revoke"), buttonActions(tableRowOf(managePage(admin), legacyUuid)));
        assertRefused(400, Messages.ApiTokenLifetimeAdministrativeMonitor_Action_ShortenLegacy(), shorten(admin, "alice", legacyUuid));

        assertEquals(state, stateOf("alice"));
        assertTrue(alice.hasLegacyToken());
    }

    @Test
    void shortenRefusesATokenOfAnExemptUserAndWhenThereIsNoMaximum() throws Exception {
        JenkinsRule.WebClient admin = adminClient();
        config.setMaxTokenLifetimeDays(30);
        String never = add("alice", "never", 1, null);
        State state = stateOf("alice");

        // the page was rendered before alice was exempted
        config.setExemptUserIds("alice");
        assertRefused(409, Messages.ApiTokenLifetimeAdministrativeMonitor_Action_ShortenNotNeeded(), shorten(admin, "alice", never));
        assertEquals(state, stateOf("alice"));

        // or before the maximum was removed
        config.setExemptUserIds("");
        config.setMaxTokenLifetimeDays(0);
        assertRefused(409, Messages.ApiTokenLifetimeAdministrativeMonitor_Action_ShortenNotNeeded(), shorten(admin, "alice", never));
        assertEquals(state, stateOf("alice"));
        assertNull(expirationOf("alice", never));
    }

    // ---- who may act, and with what

    @Test
    void nonAdministratorsAreRefusedBothActionsAndNothingChanges() throws Exception {
        setUpSecurity();
        config.setMaxTokenLifetimeDays(30);
        String never = add("alice", "never", 1, null);
        add("alice", "shared", 1, LocalDate.now().plusDays(1));
        State state = stateOf("alice");

        for (String user : new String[] {null, "reader", "sysreader"}) {
            JenkinsRule.WebClient wc = client(user);
            assertEquals(403, revoke(wc, "alice", never).getStatusCode(), "revoke as " + user);
            assertEquals(403, shorten(wc, "alice", never).getStatusCode(), "shorten as " + user);
            assertEquals(state, stateOf("alice"), "after " + user);
        }

        // the administrator is not refused
        assertDone(shorten(client("admin"), "alice", never));
    }

    @Test
    void aPostWithoutTheCrumbIsRejectedAndNothingChanges() throws Exception {
        JenkinsRule.WebClient admin = adminClient();
        config.setMaxTokenLifetimeDays(30);
        String never = add("alice", "never", 1, null);
        State state = stateOf("alice");

        assertEquals(403, post(admin, REVOKE_URL, json("alice", never), false).getStatusCode());
        assertEquals(403, post(admin, SHORTEN_URL, json("alice", never), false).getStatusCode());
        assertEquals(state, stateOf("alice"));

        // with the crumb the same request is done: it was the crumb
        assertDone(shorten(admin, "alice", never));
    }

    @Test
    void aGetToTheActionUrlsDoesNotActAndIsNotAllowed() throws Exception {
        JenkinsRule.WebClient admin = adminClient();
        config.setMaxTokenLifetimeDays(30);
        String never = add("alice", "never", 1, null);
        State state = stateOf("alice");

        for (String url : List.of(REVOKE_URL, SHORTEN_URL)) {
            for (String query : List.of("", "?userId=alice&uuid=" + never)) {
                WebResponse response = admin.getPage(new WebRequest(new URL(j.getURL(), url + query), HttpMethod.GET)).getWebResponse();
                assertEquals(405, response.getStatusCode(), url + query);
            }
        }

        assertEquals(state, stateOf("alice"));
    }

    @Test
    void forgedOrStaleRequestsAreAnsweredWithAStaticMessageAndChangeNothing() throws Exception {
        JenkinsRule.WebClient admin = adminClient();
        config.setMaxTokenLifetimeDays(30);
        String alicesToken = add("alice", "never", 1, null);
        String bobsToken = add("bob", "never", 2, null);
        State alice = stateOf("alice");
        State bob = stateOf("bob");
        int users = User.getAll().size();
        String hostile = "<script>alert(1)</script>";
        String notFound = Messages.ApiTokenLifetimeAdministrativeMonitor_Action_NotFound();
        String invalid = Messages.ApiTokenLifetimeAdministrativeMonitor_Action_Invalid();

        record Case(String description, String body, int status, String message) {}

        List<Case> cases = List.of(
                new Case("unknown user", json("nobody-known", alicesToken), 404, notFound),
                new Case("hostile user", json(hostile, alicesToken), 404, notFound),
                new Case("unknown token", json("alice", "no-such-token"), 404, notFound),
                new Case("hostile token", json("alice", hostile), 404, notFound),
                new Case("the token of another user", json("alice", bobsToken), 404, notFound),
                new Case("the token of another user, the other way", json("bob", alicesToken), 404, notFound),
                new Case("no fields", "{}", 400, invalid),
                new Case("no user", "{\"uuid\":" + quoted(alicesToken) + "}", 400, invalid),
                new Case("a number for the user", "{\"userId\":5,\"uuid\":" + quoted(alicesToken) + "}", 400, invalid),
                new Case("an object for the token", "{\"userId\":\"alice\",\"uuid\":{\"a\":1}}", 400, invalid),
                new Case("an array", "[1]", 400, invalid),
                new Case("not JSON", "{not json", 400, invalid),
                new Case("empty body", "", 400, invalid),
                // json-lib reads it as a null object, whose every read throws: it must not escape as a 500
                new Case("JSON null", "null", 400, invalid),
                new Case("null fields", "{\"userId\":null,\"uuid\":null}", 400, invalid),
                new Case("no token", "{\"userId\":\"alice\"}", 400, invalid),
                new Case("empty token", json("alice", ""), 400, invalid),
                new Case("blank token", json("alice", "   "), 400, invalid),
                new Case("empty user", json("", alicesToken), 400, invalid),
                new Case("blank user", json("  ", alicesToken), 400, invalid));
        for (String url : List.of(REVOKE_URL, SHORTEN_URL)) {
            for (Case c : cases) {
                String label = url + ": " + c.description();
                WebResponse response = post(admin, url, c.body(), true);
                assertRefused(c.status(), c.message(), response);
                String content = response.getContentAsString();
                for (String echoed : List.of("<script>", "alert(1)", "no-such-token", "nobody-known")) {
                    assertThat(label, content, not(containsString(echoed)));
                }
            }
        }

        assertEquals(alice, stateOf("alice"));
        assertEquals(bob, stateOf("bob"));
        assertEquals(users, User.getAll().size());
        assertNull(User.getById("nobody-known", false), "a request never creates a user");
        assertNull(User.getById(hostile, false));
    }

    @Test
    void aBodyThatIsNotJsonIsAnswered400AndChangesNothing() throws Exception {
        JenkinsRule.WebClient admin = adminClient();
        config.setMaxTokenLifetimeDays(30);
        String never = add("alice", "never", 1, null);
        State state = stateOf("alice");
        String invalid = Messages.ApiTokenLifetimeAdministrativeMonitor_Action_Invalid();

        for (String url : List.of(REVOKE_URL, SHORTEN_URL)) {
            // what a form that another site makes up would send: the crumb is the first guard, this is the second
            assertRefused(400, invalid, post(admin, url, "application/x-www-form-urlencoded", "userId=alice&uuid=" + never, true));
            assertRefused(400, invalid, post(admin, url, "text/plain", json("alice", never), true));
            assertRefused(400, invalid, post(admin, url, "application/json; charset=UTF-8", "{not json", true));
        }

        assertEquals(state, stateOf("alice"));
    }

    /**
     * The body of the page followed by whitespace, so that it is {@code bytes} long. It is complete and valid JSON from its first
     * bytes on, so a request that read only the beginning of it, and did not look at its size, would act on it.
     */
    private static String paddedTo(String json, int bytes) {
        assertTrue(json.length() <= bytes && json.length() == json.getBytes(StandardCharsets.UTF_8).length, "the body to pad is ASCII and fits");
        return json + " ".repeat(bytes - json.length());
    }

    /**
     * Both actions read at most 8 KiB of the body, and answer 400 with the static message, before parsing it, to one that is
     * larger: the body of one byte over the limit and the one of 100 KiB are valid JSON from their first bytes on, that would be
     * acted on if they were not too large, and nothing is changed. One of exactly 8 KiB is still acted on.
     */
    @Test
    void aBodyLargerThan8KiBIsAnswered400WithoutBeingParsedAndChangesNothing() throws Exception {
        JenkinsRule.WebClient admin = adminClient();
        config.setMaxTokenLifetimeDays(30);
        String never = add("alice", "never", 1, null);
        String other = add("alice", "other", 2, null);
        State state = stateOf("alice");
        String invalid = Messages.ApiTokenLifetimeAdministrativeMonitor_Action_Invalid();

        for (int size : new int[] {8 * 1024 + 1, 100 * 1024}) {
            String body = paddedTo(json("alice", never), size);
            for (String url : List.of(REVOKE_URL, SHORTEN_URL)) {
                WebResponse response = post(admin, url, body, true);
                assertRefused(400, invalid, response);
                assertThat(url + " " + size, response.getContentAsString(), not(containsString(never)));
            }
        }
        assertEquals(state, stateOf("alice"));

        // the limit is inclusive
        assertDone(post(admin, SHORTEN_URL, paddedTo(json("alice", never), 8 * 1024), true));
        assertEquals(LocalDate.now().plusDays(30), expirationOf("alice", never));
        assertDone(post(admin, REVOKE_URL, paddedTo(json("alice", other), 8 * 1024), true));
        assertEquals(List.of(never), tokenUuids("alice"));
    }

    @Test
    void theBodyIsReadAsUtf8() throws Exception {
        JenkinsRule.WebClient admin = adminClient();
        config.setMaxTokenLifetimeDays(30);
        // letters with accents and ideograms, spelled with their code points so that the source stays ASCII
        String id = "zo" + (char) 0xeb + " " + (char) 0xfc + "n" + (char) 0xef + " " + (char) 0x65e5 + (char) 0x672c;
        add(id, "never", 1, null);
        String uuid = tokenUuids(id).get(0);

        // the script of the page sends application/json, with no charset, as UTF-8
        assertDone(revoke(admin, id, uuid));

        assertThat(tokenUuids(id), empty());
    }

    // ---- the log

    @Test
    void everyActionThatIsDoneLogsOneInfoRecordWithTheIdsAndWithoutNames() throws Exception {
        JenkinsRule.WebClient admin = adminClient();
        config.setMaxTokenLifetimeDays(30);
        LocalDate now = LocalDate.now();
        User evil = User.getById(EVIL_ID, true);
        evil.setFullName(EVIL_FULL_NAME);
        evil.save();
        add(EVIL_ID, EVIL_TOKEN_NAME, 1, null);
        add(EVIL_ID, "second " + EVIL_TOKEN_NAME, 1, now.plusDays(5));
        add(EVIL_ID, "over " + EVIL_TOKEN_NAME, 2, now.plusDays(100));
        // the two that share a secret, and the one that is shortened
        List<String> uuids = tokenUuids(EVIL_ID);
        List<String> group = List.of(uuids.get(0), uuids.get(1));
        String over = uuids.get(2);

        assertDone(shorten(admin, EVIL_ID, over));
        assertDone(revoke(admin, EVIL_ID, group.get(0)));
        // what is refused is not logged as done
        assertEquals(404, revoke(admin, EVIL_ID, "no-such-token").getStatusCode());
        assertEquals(409, shorten(admin, EVIL_ID, over).getStatusCode());
        assertEquals(400, shorten(admin, EVIL_ID, "").getStatusCode());

        List<String> messages = infoMessages();
        assertThat(messages, hasSize(2));
        String shortened = messages.get(0);
        assertThat(shortened, containsString("shortened"));
        assertThat(shortened, containsString("administrator admin,"));
        assertThat(shortened, containsString("owner " + EVIL_ID + ","));
        assertThat(shortened, containsString("token " + over + ","));
        assertThat(shortened, containsString("expiration " + now.plusDays(100) + " to " + now.plusDays(30)));
        String revoked = messages.get(1);
        assertThat(revoked, containsString("revoked"));
        assertThat(revoked, containsString("administrator admin,"));
        assertThat(revoked, containsString("owner " + EVIL_ID + ","));
        for (String uuid : group) {
            assertThat(revoked, containsString(uuid));
        }
        for (String message : messages) {
            assertThat(message, not(containsString(EVIL_TOKEN_NAME)));
            assertThat(message, not(containsString("alert(1)")));
            assertThat(message, not(containsString(EVIL_FULL_NAME)));
            assertThat(message, not(containsString("second ")));
            assertThat(message, not(containsString("over ")));
        }
    }

    @Test
    void theLogSaysWhenTheExpirationWasNone() throws Exception {
        JenkinsRule.WebClient admin = adminClient();
        config.setMaxTokenLifetimeDays(30);
        String never = add("alice", "never", 1, null);

        assertDone(shorten(admin, "alice", never));

        assertThat(infoMessages(), hasSize(1));
        assertThat(infoMessages().get(0), containsString("token " + never + ", expiration never to " + LocalDate.now().plusDays(30)));
    }

    // ---- the script of the page, driven through the real dialog

    private static HtmlElement actionButton(HtmlPage page, String uuid, String action) {
        HtmlElement button = (HtmlElement) tableRowOf(page, uuid).querySelector("button[data-token-action=" + action + "]");
        assertNotNull(button, action + " of " + uuid);
        return button;
    }

    /** The dialog that has the OK button: the layout has other dialogs. */
    private static DomElement openDialog(HtmlPage page) {
        List<DomNode> dialogs = page.querySelectorAll("dialog").stream()
                .filter(dialog -> ((DomElement) dialog).querySelector("button[data-id=ok]") != null)
                .toList();
        assertThat("the dialog of the confirmation", dialogs, hasSize(1));
        return (DomElement) dialogs.get(0);
    }

    @Test
    void clickingRevokeOnARowThatSharesItsSecretListsTheNamesAsTextAndRevokesTheGroup() throws Exception {
        JenkinsRule.WebClient admin = adminClient();
        config.setMaxTokenLifetimeDays(30);
        User evil = User.getById(EVIL_ID, true);
        evil.setFullName(EVIL_FULL_NAME);
        evil.save();
        add(EVIL_ID, EVIL_TOKEN_NAME, 1, null);
        add(EVIL_ID, "sibling " + EVIL_TOKEN_NAME, 1, LocalDate.now().plusDays(1));
        add(EVIL_ID, "expired sibling", 1, LocalDate.now().minusDays(1));
        add(EVIL_ID, "unrelated", 2, null);
        // in the order of the store: the target, its sibling, the sibling that has expired, and a token of another secret
        List<String> uuids = tokenUuids(EVIL_ID);
        String target = uuids.get(0);
        List<String> alerts = new ArrayList<>();
        admin.setAlertHandler((page, message) -> alerts.add(message));
        HtmlPage page = managePage(admin);

        actionButton(page, target, "revoke").click();

        DomElement dialog = openDialog(page);
        assertEquals("Revoke API token", dialog.querySelector(".jenkins-dialog__title span").getTextContent().trim());
        assertEquals("Revoke", dialog.querySelector("button[data-id=ok]").getTextContent().trim(), "the static text of the property");
        assertEquals("Cancel", dialog.querySelector("button[data-id=cancel]").getTextContent().trim());
        String message = dialog.querySelector(".jenkins-form-item").getTextContent();
        assertThat(message, containsString("shares its secret with other tokens"));
        // the names that users chose are text: whatever they hold is shown as it is, including what a replacement would interpret
        assertThat(message, containsString("- sibling " + EVIL_TOKEN_NAME));
        assertThat("the expired sibling is not revoked, so it is not listed", message, not(containsString("expired sibling")));
        assertEquals(0, dialog.querySelectorAll("script, img").size());
        assertThat(alerts, empty());
        assertEquals(uuids, tokenUuids(EVIL_ID), "nothing is done before the confirmation");

        ((HtmlElement) dialog.querySelector("button[data-id=ok]")).click();
        admin.waitForBackgroundJavaScript(10_000);

        assertEquals(List.of(uuids.get(2), uuids.get(3)), tokenUuids(EVIL_ID), "the group, and nothing else, is revoked");
        assertThat(alerts, empty());
    }

    @Test
    void clickingRevokeOnARowThatDoesNotShareItsSecretConfirmsWithTheStaticTextAndRevokesOnlyThatToken() throws Exception {
        JenkinsRule.WebClient admin = adminClient();
        config.setMaxTokenLifetimeDays(30);
        String hostile = add(EVIL_ID, EVIL_TOKEN_NAME, 1, null);
        String other = add(EVIL_ID, "other", 2, null);
        List<String> alerts = new ArrayList<>();
        admin.setAlertHandler((page, message) -> alerts.add(message));
        HtmlPage page = managePage(admin);

        actionButton(page, hostile, "revoke").click();

        DomElement dialog = openDialog(page);
        assertEquals("Revoke this API token? Whatever uses it will stop working.", dialog.querySelector(".jenkins-form-item").getTextContent().trim());
        assertEquals("Revoke", dialog.querySelector("button[data-id=ok]").getTextContent().trim());
        ((HtmlElement) dialog.querySelector("button[data-id=ok]")).click();
        admin.waitForBackgroundJavaScript(10_000);

        assertEquals(List.of(other), tokenUuids(EVIL_ID));
        assertThat(alerts, empty());
    }

    @Test
    void cancellingTheConfirmationDoesNothing() throws Exception {
        JenkinsRule.WebClient admin = adminClient();
        config.setMaxTokenLifetimeDays(30);
        String never = add("alice", "never", 1, null);
        State state = stateOf("alice");
        HtmlPage page = managePage(admin);

        actionButton(page, never, "shorten").click();
        DomElement dialog = openDialog(page);
        assertEquals("Shorten to maximum", dialog.querySelector("button[data-id=ok]").getTextContent().trim());
        ((HtmlElement) dialog.querySelector("button[data-id=cancel]")).click();
        admin.waitForBackgroundJavaScript(10_000);

        assertEquals(state, stateOf("alice"));
    }

    @Test
    void clickingShortenShortensTheTokenOfTheRow() throws Exception {
        JenkinsRule.WebClient admin = adminClient();
        config.setMaxTokenLifetimeDays(30);
        LocalDate now = LocalDate.now();
        String over = add("alice", "over", 1, now.plusDays(100));
        String other = add("alice", "other", 2, now.plusDays(100));
        HtmlPage page = managePage(admin);

        actionButton(page, over, "shorten").click();
        ((HtmlElement) openDialog(page).querySelector("button[data-id=ok]")).click();
        admin.waitForBackgroundJavaScript(10_000);

        assertEquals(now.plusDays(30), expirationOf("alice", over));
        assertEquals(now.plusDays(100), expirationOf("alice", other));
    }

    @Test
    void whenTheServerRefusesTheActionThePageSaysSoAndIsNotReloaded() throws Exception {
        JenkinsRule.WebClient admin = adminClient();
        config.setMaxTokenLifetimeDays(30);
        LocalDate now = LocalDate.now();
        String never = add("alice", "never", 1, null);
        HtmlPage page = managePage(admin);
        HtmlElement shorten = actionButton(page, never, "shorten");
        // meanwhile the token comes to comply, so Shorten is refused
        assertTrue(propertyOf("alice").shortenExpiration(never, now.plusDays(5)));

        shorten.click();
        ((HtmlElement) openDialog(page).querySelector("button[data-id=ok]")).click();
        admin.waitForBackgroundJavaScript(10_000);

        assertSame(page, admin.getCurrentWindow().getEnclosedPage(), "the page was not reloaded");
        List<String> titles = new ArrayList<>();
        for (DomNode title : page.querySelectorAll("dialog .jenkins-dialog__title span")) {
            titles.add(title.getTextContent().trim());
        }
        assertThat(titles, hasItem("The action was not done"));
        assertThat(page.asNormalizedText(), containsString("The API token could not be changed. It may have been revoked, or may have changed, since this page was loaded."));
        assertEquals(now.plusDays(5), expirationOf("alice", never), "nothing was changed");
        assertFalse(((HtmlButton) shorten).isDisabled(), "the button can be used again");
    }
}
