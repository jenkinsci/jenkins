/*
 * The MIT License
 *
 * Copyright (c) 2018, CloudBees, Inc.
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
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.XmlFile;
import hudson.model.Descriptor.FormException;
import hudson.model.Saveable;
import hudson.model.User;
import hudson.model.listeners.SaveableListener;
import hudson.util.FormValidation;
import java.io.File;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import jenkins.model.Jenkins;
import jenkins.security.ApiTokenProperty;
import jenkins.security.Messages;
import net.sf.json.JSONNull;
import net.sf.json.JSONObject;
import org.htmlunit.FailingHttpStatusCodeException;
import org.htmlunit.HttpMethod;
import org.htmlunit.Page;
import org.htmlunit.WebRequest;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LogRecorder;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.hudson.test.recipes.LocalData;
import org.kohsuke.stapler.Stapler;

@WithJenkins
class ApiTokenPropertyConfigurationTest {

    private static final String FUTURE_NOTICE = "tokens created through the HTTP endpoints must expire within";
    private static final String LEGACY_NOTICE = "Legacy API tokens never expire, and the maximum lifetime does not apply to them";

    private final LogRecorder logging = new LogRecorder().record(ApiTokenPropertyConfiguration.class, Level.WARNING).capture(20);

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
        SaveCounter.COUNT.set(0);
    }

    @Test
    @Issue("JENKINS-32776")
    void newUserTokenConfiguration() {
        ApiTokenPropertyConfiguration config = ApiTokenPropertyConfiguration.get();

        config.setTokenGenerationOnCreationEnabled(true);
        {
            User userWith = User.getById("userWith", true);
            ApiTokenProperty withToken = userWith.getProperty(ApiTokenProperty.class);
            assertTrue(withToken.hasLegacyToken());
            assertEquals(1, withToken.getTokenList().size());

            String tokenValue = withToken.getApiToken();
            assertNotEquals(Messages.ApiTokenProperty_NoLegacyToken(), tokenValue);
        }

        config.setTokenGenerationOnCreationEnabled(false);
        {
            User userWithout = User.getById("userWithout", true);
            ApiTokenProperty withoutToken = userWithout.getProperty(ApiTokenProperty.class);
            assertFalse(withoutToken.hasLegacyToken());
            assertEquals(0, withoutToken.getTokenList().size());

            String tokenValue = withoutToken.getApiToken();
            assertEquals(Messages.ApiTokenProperty_NoLegacyToken(), tokenValue);
        }
    }

    @Test
    void defaultsChangeNothing() {
        ApiTokenPropertyConfiguration config = ApiTokenPropertyConfiguration.get();
        assertEquals(0, config.getMaxTokenLifetimeDays());
        assertNull(config.getExemptUserIds());
        assertNull(config.getEnforceFrom());
        assertFalse(new ApiTokenLifetimePolicy().isActive());
    }

    @Test
    @LocalData
    void oldConfigurationLoadsWithDefaults() {
        // The file has none of the new fields
        ApiTokenPropertyConfiguration config = ApiTokenPropertyConfiguration.get();
        assertTrue(config.isTokenGenerationOnCreationEnabled());
        assertFalse(config.isCreationOfLegacyTokenEnabled());
        assertFalse(config.isUsageStatisticsEnabled());
        assertEquals(0, config.getMaxTokenLifetimeDays());
        assertNull(config.getExemptUserIds());
        assertNull(config.getEnforceFrom());
        ApiTokenLifetimePolicy policy = new ApiTokenLifetimePolicy();
        assertFalse(policy.isActive());
        assertFalse(policy.isEnforcing(LocalDate.now()));
        assertFalse(policy.isExempt(User.getById("alice", true), LocalDate.now()));
    }

    @Test
    void settersNeverThrowAndNormalise() {
        ApiTokenPropertyConfiguration config = ApiTokenPropertyConfiguration.get();

        // What a configuration as code file with a negative number ends up calling
        config.setMaxTokenLifetimeDays(-5);
        assertEquals(0, config.getMaxTokenLifetimeDays());
        assertThat(logging.getMessages(), hasItem(containsString("cannot be negative (-5)")));
        config.setMaxTokenLifetimeDays(90);
        assertEquals(90, config.getMaxTokenLifetimeDays());
        config.setMaxTokenLifetimeDays(Integer.MIN_VALUE);
        assertEquals(0, config.getMaxTokenLifetimeDays());

        config.setEnforceFrom("  ");
        assertNull(config.getEnforceFrom());
        config.setEnforceFrom(null);
        assertNull(config.getEnforceFrom());
        config.setEnforceFrom("");
        assertNull(config.getEnforceFrom());
        config.setEnforceFrom("  2030-01-31  ");
        assertEquals("2030-01-31", config.getEnforceFrom());
        config.setEnforceFrom("not a date");
        assertEquals("not a date", config.getEnforceFrom(), "any other value is stored as is");

        config.setExemptUserIds(null);
        assertNull(config.getExemptUserIds());
        config.setExemptUserIds(" alice \n\nbob ");
        assertEquals(" alice \n\nbob ", config.getExemptUserIds(), "stored as is");
    }

    // The /configureSecurity form: ApiTokenPropertyConfiguration is in the Security category, so
    // JenkinsRule#configRoundtrip, which submits /configure, does not reach it.

    @Test
    void submitWithoutChangesKeepsEverything() throws Exception {
        ApiTokenPropertyConfiguration config = ApiTokenPropertyConfiguration.get();
        config.setTokenGenerationOnCreationEnabled(true);
        config.setCreationOfLegacyTokenEnabled(false);
        config.setUsageStatisticsEnabled(false);
        SaveCounter.COUNT.set(0);

        j.submit(securityForm());

        assertTrue(config.isTokenGenerationOnCreationEnabled());
        assertFalse(config.isCreationOfLegacyTokenEnabled());
        assertFalse(config.isUsageStatisticsEnabled());
        assertEquals(0, config.getMaxTokenLifetimeDays());
        assertThat(config.getExemptUserIds(), emptyOrNullString());
        assertNull(config.getEnforceFrom());
        assertEquals(1, SaveCounter.COUNT.get(), "one write for the whole form");
    }

    @Test
    void submitWithoutChangesKeepsTheLegacyFlagsWhileThePolicyIsActive() throws Exception {
        ApiTokenPropertyConfiguration config = ApiTokenPropertyConfiguration.get();
        config.setMaxTokenLifetimeDays(30);
        config.setTokenGenerationOnCreationEnabled(true);
        config.setCreationOfLegacyTokenEnabled(true);

        j.submit(securityForm());

        assertTrue(config.isTokenGenerationOnCreationEnabled());
        assertTrue(config.isCreationOfLegacyTokenEnabled());
        assertTrue(config.isUsageStatisticsEnabled());
        assertEquals(30, config.getMaxTokenLifetimeDays());
    }

    @Test
    void submitBlankNumberFieldIsAccepted() throws Exception {
        ApiTokenPropertyConfiguration config = ApiTokenPropertyConfiguration.get();
        config.setMaxTokenLifetimeDays(30);

        HtmlForm form = securityForm();
        form.getInputByName("_.maxTokenLifetimeDays").setValue("");
        j.submit(form);

        assertEquals(0, config.getMaxTokenLifetimeDays(), "blank is 0, no limit");
    }

    @Test
    void submitSetValues() throws Exception {
        ApiTokenPropertyConfiguration config = ApiTokenPropertyConfiguration.get();
        String date = LocalDate.now().plusDays(10).toString();

        HtmlForm form = securityForm();
        form.getInputByName("_.maxTokenLifetimeDays").setValue("30");
        form.getTextAreaByName("_.exemptUserIds").setText("alice\nService Account");
        form.getInputByName("_.enforceFrom").setValue(" " + date + " ");
        SaveCounter.COUNT.set(0);
        j.submit(form);

        assertEquals(30, config.getMaxTokenLifetimeDays());
        assertEquals(List.of("alice", "Service Account"), ApiTokenLifetimePolicy.parseUserIds(config.getExemptUserIds()));
        assertEquals(date, config.getEnforceFrom());
        assertEquals(1, SaveCounter.COUNT.get(), "one write for the whole form");

        String xml = Files.readString(new File(j.jenkins.getRootDir(), ApiTokenPropertyConfiguration.class.getName() + ".xml").toPath(), StandardCharsets.UTF_8);
        assertThat(xml, containsString("<maxTokenLifetimeDays>30</maxTokenLifetimeDays>"));
        assertThat(xml, containsString("<enforceFrom>" + date + "</enforceFrom>"));

        // The form shows what was saved, and saving it again changes nothing
        HtmlForm again = securityForm();
        assertEquals("30", again.getInputByName("_.maxTokenLifetimeDays").getValue());
        assertEquals(date, again.getInputByName("_.enforceFrom").getValue());
        j.submit(again);
        assertEquals(30, config.getMaxTokenLifetimeDays());
        assertEquals(date, config.getEnforceFrom());
        assertEquals(List.of("alice", "Service Account"), ApiTokenLifetimePolicy.parseUserIds(config.getExemptUserIds()));
    }

    @Test
    void submitClearedDateStoresNull() throws Exception {
        ApiTokenPropertyConfiguration config = ApiTokenPropertyConfiguration.get();
        config.setMaxTokenLifetimeDays(30);
        config.setEnforceFrom(LocalDate.now().plusDays(10).toString());

        HtmlForm form = securityForm();
        form.getInputByName("_.enforceFrom").setValue("");
        j.submit(form);

        assertNull(config.getEnforceFrom());
        assertEquals(30, config.getMaxTokenLifetimeDays());
    }

    @Test
    void submitNegativeMaximumIsRejected() throws Exception {
        ApiTokenPropertyConfiguration config = ApiTokenPropertyConfiguration.get();
        config.setMaxTokenLifetimeDays(10);

        HtmlForm form = securityForm();
        form.getInputByName("_.maxTokenLifetimeDays").setValue("-1");
        form.getTextAreaByName("_.exemptUserIds").setText("mallory");
        SaveCounter.COUNT.set(0);
        FailingHttpStatusCodeException e = assertThrows(FailingHttpStatusCodeException.class, () -> j.submit(form));

        assertEquals(400, e.getStatusCode());
        assertThat(e.getResponse().getContentAsString(), containsString(jenkins.security.apitoken.Messages.ApiTokenPropertyConfiguration_MaxTokenLifetimeDays_Rejected()));
        assertEquals(10, config.getMaxTokenLifetimeDays());
        assertNull(config.getExemptUserIds(), "nothing was bound");
        assertEquals(0, SaveCounter.COUNT.get(), "nothing was saved");
    }

    @Test
    void submitInvalidDateIsRejectedWithoutPartialBind() throws Exception {
        ApiTokenPropertyConfiguration config = ApiTokenPropertyConfiguration.get();
        config.setMaxTokenLifetimeDays(10);
        config.setUsageStatisticsEnabled(true);

        HtmlForm form = securityForm();
        form.getInputByName("_.maxTokenLifetimeDays").setValue("60");
        form.getTextAreaByName("_.exemptUserIds").setText("mallory");
        form.getInputByName("_.enforceFrom").setValue("2030-13-01");
        SaveCounter.COUNT.set(0);
        FailingHttpStatusCodeException e = assertThrows(FailingHttpStatusCodeException.class, () -> j.submit(form));

        assertEquals(400, e.getStatusCode());
        String response = e.getResponse().getContentAsString();
        assertThat(response, containsString(jenkins.security.apitoken.Messages.ApiTokenPropertyConfiguration_EnforceFrom_Rejected()));
        assertThat("the message does not echo the value", response, not(containsString("2030-13-01")));
        assertEquals(10, config.getMaxTokenLifetimeDays(), "the valid number was not bound either");
        assertNull(config.getExemptUserIds());
        assertNull(config.getEnforceFrom());
        assertTrue(config.isUsageStatisticsEnabled());
        assertEquals(0, SaveCounter.COUNT.get(), "nothing was saved");
    }

    // configure(StaplerRequest2, JSONObject) with the exact JSON, as the form cannot post every shape

    @Test
    void configureLeavesAbsentKeysUnchanged() throws Exception {
        ApiTokenPropertyConfiguration config = ApiTokenPropertyConfiguration.get();
        config.setMaxTokenLifetimeDays(10);
        config.setExemptUserIds("alice");
        config.setEnforceFrom("2099-01-01");
        config.setUsageStatisticsEnabled(false);

        configure(new JSONObject());
        assertEquals(10, config.getMaxTokenLifetimeDays());
        assertEquals("alice", config.getExemptUserIds());
        assertEquals("2099-01-01", config.getEnforceFrom());
        assertFalse(config.isUsageStatisticsEnabled());

        configure(new JSONObject().element("usageStatisticsEnabled", true));
        assertTrue(config.isUsageStatisticsEnabled());
        assertEquals(10, config.getMaxTokenLifetimeDays());
        assertEquals("alice", config.getExemptUserIds());
        assertEquals("2099-01-01", config.getEnforceFrom());
    }

    @Test
    void configureNormalisesTheValues() throws Exception {
        ApiTokenPropertyConfiguration config = ApiTokenPropertyConfiguration.get();

        configure(new JSONObject().element("maxTokenLifetimeDays", "  45 ").element("enforceFrom", " 2099-01-01 "));
        assertEquals(45, config.getMaxTokenLifetimeDays());
        assertEquals("2099-01-01", config.getEnforceFrom());

        configure(new JSONObject().element("maxTokenLifetimeDays", 12));
        assertEquals(12, config.getMaxTokenLifetimeDays());
        assertEquals("2099-01-01", config.getEnforceFrom(), "absent: unchanged");

        configure(new JSONObject().element("maxTokenLifetimeDays", "+7").element("enforceFrom", ""));
        assertEquals(7, config.getMaxTokenLifetimeDays());
        assertNull(config.getEnforceFrom());

        config.setEnforceFrom("2099-01-01");
        configure(new JSONObject().element("maxTokenLifetimeDays", "").element("enforceFrom", "  "));
        assertEquals(0, config.getMaxTokenLifetimeDays());
        assertNull(config.getEnforceFrom());

        config.setMaxTokenLifetimeDays(5);
        config.setEnforceFrom("2099-01-01");
        configure(new JSONObject().element("maxTokenLifetimeDays", JSONNull.getInstance()).element("enforceFrom", JSONNull.getInstance()));
        assertEquals(0, config.getMaxTokenLifetimeDays());
        assertNull(config.getEnforceFrom());
    }

    @Test
    void configureAcceptsWarningsAndInertCombinations() throws Exception {
        ApiTokenPropertyConfiguration config = ApiTokenPropertyConfiguration.get();

        // 1 to 6 days only warns
        configure(new JSONObject().element("maxTokenLifetimeDays", "3"));
        assertEquals(3, config.getMaxTokenLifetimeDays());

        // a date with no maximum, and a date in the past, are accepted
        configure(new JSONObject().element("maxTokenLifetimeDays", "0").element("enforceFrom", "2099-01-01"));
        assertEquals(0, config.getMaxTokenLifetimeDays());
        assertEquals("2099-01-01", config.getEnforceFrom());
        configure(new JSONObject().element("enforceFrom", "2000-01-01"));
        assertEquals("2000-01-01", config.getEnforceFrom());
    }

    @Test
    void configureRejectsInvalidMaximum() throws Exception {
        ApiTokenPropertyConfiguration config = ApiTokenPropertyConfiguration.get();
        config.setMaxTokenLifetimeDays(10);
        SaveCounter.COUNT.set(0);

        for (String invalid : List.of("abc", "-1", "-0.5", "1.5", "1e3", "99999999999", "12 days", "--1")) {
            FormException e = assertThrows(FormException.class, () -> configure(new JSONObject().element("maxTokenLifetimeDays", invalid).element("exemptUserIds", "mallory")), invalid);
            assertEquals("maxTokenLifetimeDays", e.getFormField());
            assertEquals(jenkins.security.apitoken.Messages.ApiTokenPropertyConfiguration_MaxTokenLifetimeDays_Rejected(), e.getMessage());
            assertThat("the message does not echo the value", e.getMessage(), not(containsString(invalid)));
        }
        assertEquals(10, config.getMaxTokenLifetimeDays());
        assertNull(config.getExemptUserIds());
        assertEquals(0, SaveCounter.COUNT.get());
    }

    @Test
    void configureRejectsInvalidDate() throws Exception {
        ApiTokenPropertyConfiguration config = ApiTokenPropertyConfiguration.get();
        config.setEnforceFrom("2099-01-01");
        SaveCounter.COUNT.set(0);

        for (String invalid : List.of("2026-13-01", "2030-02-30", "tomorrow", "2030-1-1", "31/12/2030", "20301231", "2030-12-31T00:00")) {
            FormException e = assertThrows(FormException.class, () -> configure(new JSONObject().element("enforceFrom", invalid).element("maxTokenLifetimeDays", "30")), invalid);
            assertEquals("enforceFrom", e.getFormField());
            assertEquals(jenkins.security.apitoken.Messages.ApiTokenPropertyConfiguration_EnforceFrom_Rejected(), e.getMessage());
            assertThat("the message does not echo the value", e.getMessage(), not(containsString(invalid)));
        }
        assertEquals("2099-01-01", config.getEnforceFrom());
        assertEquals(0, config.getMaxTokenLifetimeDays(), "the valid number was not bound");
        assertEquals(0, SaveCounter.COUNT.get());
    }

    // doCheck methods

    @Test
    void doCheckMaxTokenLifetimeDays() {
        ApiTokenPropertyConfiguration config = ApiTokenPropertyConfiguration.get();
        FormValidation.Kind ok = FormValidation.Kind.OK;
        FormValidation.Kind warning = FormValidation.Kind.WARNING;
        FormValidation.Kind error = FormValidation.Kind.ERROR;

        assertEquals(ok, config.doCheckMaxTokenLifetimeDays("", "").kind);
        assertEquals(ok, config.doCheckMaxTokenLifetimeDays(null, null).kind);
        assertEquals(ok, config.doCheckMaxTokenLifetimeDays("  ", "  ").kind);
        assertEquals(ok, config.doCheckMaxTokenLifetimeDays("0", "").kind);
        assertEquals(error, config.doCheckMaxTokenLifetimeDays("-1", "").kind);
        assertEquals(error, config.doCheckMaxTokenLifetimeDays("abc", "").kind);
        assertEquals(error, config.doCheckMaxTokenLifetimeDays("1.5", "").kind);
        assertEquals(error, config.doCheckMaxTokenLifetimeDays("99999999999", "").kind);
        assertEquals(warning, config.doCheckMaxTokenLifetimeDays("1", "").kind);
        assertEquals(warning, config.doCheckMaxTokenLifetimeDays("3", "").kind);
        assertEquals(warning, config.doCheckMaxTokenLifetimeDays("6", "").kind);
        assertEquals(ok, config.doCheckMaxTokenLifetimeDays("7", "").kind, "a token is flagged when it expires in fewer than 7 days");
        assertEquals(ok, config.doCheckMaxTokenLifetimeDays("30", "").kind);
        assertEquals(ok, config.doCheckMaxTokenLifetimeDays(" 365000 ", "").kind, "no upper bound");

        assertThat(config.doCheckMaxTokenLifetimeDays("3", "").renderHtml(), containsString("less than 7 days"));
        assertThat(config.doCheckMaxTokenLifetimeDays("abc", "").renderHtml(), not(containsString("abc")));

        // A date has no effect without a maximum
        assertEquals(warning, config.doCheckMaxTokenLifetimeDays("0", "2099-01-01").kind);
        assertEquals(warning, config.doCheckMaxTokenLifetimeDays("", "2099-01-01").kind);
        assertEquals(ok, config.doCheckMaxTokenLifetimeDays("30", "2099-01-01").kind);
        assertEquals(warning, config.doCheckMaxTokenLifetimeDays("3", "2099-01-01").kind);
        assertEquals(error, config.doCheckMaxTokenLifetimeDays("-1", "2099-01-01").kind);
    }

    @Test
    void doCheckEnforceFrom() {
        ApiTokenPropertyConfiguration config = ApiTokenPropertyConfiguration.get();
        String nothingToSay = FormValidation.ok().renderHtml();

        assertEquals(nothingToSay, config.doCheckEnforceFrom("").renderHtml());
        assertEquals(nothingToSay, config.doCheckEnforceFrom(null).renderHtml());
        assertEquals(nothingToSay, config.doCheckEnforceFrom("   ").renderHtml());
        assertEquals(nothingToSay, config.doCheckEnforceFrom(LocalDate.now().plusDays(30).toString()).renderHtml());
        assertEquals(nothingToSay, config.doCheckEnforceFrom(LocalDate.now().toString()).renderHtml(), "today is not in the past");
        assertEquals(nothingToSay, config.doCheckEnforceFrom(" " + LocalDate.now().plusDays(1) + " ").renderHtml());

        FormValidation past = config.doCheckEnforceFrom("2020-01-01");
        assertEquals(FormValidation.Kind.OK, past.kind, "informative only");
        assertThat(past.renderHtml(), containsString("already enforced"));

        for (String invalid : List.of("garbage", "2026-13-01", "2030-02-30", "2030-1-1", "31/12/2030")) {
            FormValidation garbage = config.doCheckEnforceFrom(invalid);
            assertEquals(FormValidation.Kind.ERROR, garbage.kind, invalid);
            assertThat(garbage.renderHtml(), not(containsString(invalid)));
        }
    }

    @Test
    void doCheckExemptUserIdsWarnsAboutUnknownIdsOnly() throws Exception {
        setUpUsers();
        User.getById("alice", true);
        JenkinsRule.WebClient wc = j.createWebClient().withBasicCredentials("admin");

        String html = checkExemptUserIds(wc, HttpMethod.POST, "alice\n  ghost \n\nALICE\nphantom\n");
        assertThat(html, containsString("ghost, phantom"));
        assertThat(html, not(containsString("alice")));
        assertThat(html, not(containsString("ALICE")));
        assertThat(html, containsString("class=\"warning\""));

        assertNull(User.getById("ghost", false), "checking creates no user");
        assertNull(User.getById("phantom", false), "checking creates no user");

        assertEquals(FormValidation.ok().renderHtml(), checkExemptUserIds(wc, HttpMethod.POST, "alice\nadmin"));
        assertEquals(FormValidation.ok().renderHtml(), checkExemptUserIds(wc, HttpMethod.POST, ""));
        assertEquals(FormValidation.ok().renderHtml(), checkExemptUserIds(wc, HttpMethod.POST, " \n \n"));
    }

    @Test
    void doCheckExemptUserIdsDoesNotMatchFullNames() throws Exception {
        setUpUsers();
        User alice = User.getById("alice", true);
        alice.setFullName("Alice Liddell");
        alice.save();
        JenkinsRule.WebClient wc = j.createWebClient().withBasicCredentials("admin");

        String html = checkExemptUserIds(wc, HttpMethod.POST, "Alice Liddell");
        assertThat(html, containsString("Alice Liddell"));
        assertThat(html, containsString("not known to Jenkins yet"));
        assertNull(User.getById("Alice Liddell", false));
    }

    @Test
    void doCheckExemptUserIdsEscapesTheIds() throws Exception {
        setUpUsers();
        JenkinsRule.WebClient wc = j.createWebClient().withBasicCredentials("admin");

        String html = checkExemptUserIds(wc, HttpMethod.POST, "<b>bold</b>\n<script>alert(1)</script>");
        assertThat(html, containsString("&lt;b&gt;bold&lt;/b&gt;"));
        assertThat(html, containsString("&lt;script&gt;"));
        assertThat(html, not(containsString("<b>")));
        assertThat(html, not(containsString("<script>")));
        assertNull(User.getById("<b>bold</b>", false));
    }

    @Test
    void doChecksAreSilentForNonAdministrators() throws Exception {
        setUpUsers();
        User.getById("alice", true);
        String nothingToSay = FormValidation.ok().renderHtml();
        JenkinsRule.WebClient reader = j.createWebClient().withBasicCredentials("reader");

        assertEquals(nothingToSay, checkExemptUserIds(reader, HttpMethod.POST, "alice"));
        assertEquals(nothingToSay, checkExemptUserIds(reader, HttpMethod.POST, "ghost"));
        assertEquals(nothingToSay, check(reader, HttpMethod.POST, "checkMaxTokenLifetimeDays", "value=-1&enforceFrom=garbage"));
        assertEquals(nothingToSay, check(reader, HttpMethod.POST, "checkMaxTokenLifetimeDays", "value=0&enforceFrom=2099-01-01"));
        assertEquals(nothingToSay, check(reader, HttpMethod.POST, "checkEnforceFrom", "value=garbage"));
        assertNull(User.getById("ghost", false));

        // while an administrator does get the answers
        JenkinsRule.WebClient admin = j.createWebClient().withBasicCredentials("admin");
        assertThat(check(admin, HttpMethod.POST, "checkMaxTokenLifetimeDays", "value=-1&enforceFrom="), containsString("class=\"error\""));
        assertThat(check(admin, HttpMethod.POST, "checkMaxTokenLifetimeDays", "value=0&enforceFrom=2099-01-01"), containsString("class=\"warning\""));
        assertThat(check(admin, HttpMethod.POST, "checkEnforceFrom", "value=garbage"), containsString("class=\"error\""));
    }

    @Test
    void doChecksAnswerPostOnly() throws Exception {
        setUpUsers();
        JenkinsRule.WebClient wc = j.createWebClient().withBasicCredentials("admin");
        wc.getOptions().setThrowExceptionOnFailingStatusCode(false);

        // The status of a GET is not asserted, only that it does not return a validation result
        String html = checkExemptUserIds(wc, HttpMethod.GET, "ghost");
        assertThat(html, not(containsString("not known to Jenkins yet")));
        assertNull(User.getById("ghost", false));
        assertThat(check(wc, HttpMethod.GET, "checkMaxTokenLifetimeDays", "value=-1&enforceFrom="), not(containsString("class=\"error\"")));
        assertThat(check(wc, HttpMethod.GET, "checkEnforceFrom", "value=garbage"), not(containsString("class=\"error\"")));
    }

    // Rendering

    @Test
    void theFieldsAreWiredToTheirChecks() throws Exception {
        HtmlForm form = securityForm();
        assertEquals("0", form.getInputByName("_.maxTokenLifetimeDays").getValue(), "no limit is shown as 0");
        assertThat(form.getInputByName("_.maxTokenLifetimeDays").getAttribute("checkUrl"), endsWith("/checkMaxTokenLifetimeDays"));
        assertThat(form.getInputByName("_.maxTokenLifetimeDays").getAttribute("checkDependsOn"), equalTo("enforceFrom"));
        assertThat(form.getInputByName("_.maxTokenLifetimeDays").getAttribute("min"), emptyOrNullString());
        assertThat(form.getInputByName("_.enforceFrom").getAttribute("checkUrl"), endsWith("/checkEnforceFrom"));
        assertEquals("yyyy-MM-dd", form.getInputByName("_.enforceFrom").getAttribute("placeholder"));
        assertEquals("text", form.getInputByName("_.enforceFrom").getTypeAttribute());
        assertThat(form.getTextAreaByName("_.exemptUserIds").getAttribute("checkUrl"), endsWith("/checkExemptUserIds"));
    }

    @Test
    void theNoticeOfAFutureDateIsOnlyShownWhenThePolicyIsActive() throws Exception {
        ApiTokenPropertyConfiguration config = ApiTokenPropertyConfiguration.get();
        LocalDate future = LocalDate.now().plusDays(10);

        assertThat(securityPageText(), not(containsString(FUTURE_NOTICE)));

        config.setEnforceFrom(future.toString());
        assertThat("no maximum: nothing to announce", securityPageText(), not(containsString(FUTURE_NOTICE)));

        config.setMaxTokenLifetimeDays(30);
        assertThat(securityPageText(), containsString("From " + future + ", " + FUTURE_NOTICE + " 30 days, unless the account is exempt. Existing tokens are not touched."));

        config.setMaxTokenLifetimeDays(1);
        assertThat("singular", securityPageText(), containsString("From " + future + ", " + FUTURE_NOTICE + " 1 day, unless the account is exempt."));
        config.setMaxTokenLifetimeDays(30);

        config.setEnforceFrom(LocalDate.now().toString());
        assertThat("today: enforcing", securityPageText(), not(containsString(FUTURE_NOTICE)));

        config.setEnforceFrom(LocalDate.now().minusDays(1).toString());
        assertThat("past: enforcing", securityPageText(), not(containsString(FUTURE_NOTICE)));

        config.setEnforceFrom(null);
        assertThat("no date: enforcing", securityPageText(), not(containsString(FUTURE_NOTICE)));

        config.setEnforceFrom("not-a-date-on-the-page");
        assertThat("unparsable: enforcing", securityPageText(), not(containsString(FUTURE_NOTICE)));

        config.setEnforceFrom(future.toString());
        config.setMaxTokenLifetimeDays(0);
        assertThat(securityPageText(), not(containsString(FUTURE_NOTICE)));
    }

    @Test
    void theLegacyTokenWarningIsOnlyShownWhenThePolicyIsActiveAndALegacyOptionIsOn() throws Exception {
        ApiTokenPropertyConfiguration config = ApiTokenPropertyConfiguration.get();
        assertThat(securityPageText(), not(containsString(LEGACY_NOTICE)));

        config.setTokenGenerationOnCreationEnabled(true);
        assertThat("no maximum", securityPageText(), not(containsString(LEGACY_NOTICE)));

        config.setMaxTokenLifetimeDays(30);
        assertThat(securityPageText(), containsString(LEGACY_NOTICE));

        config.setTokenGenerationOnCreationEnabled(false);
        assertThat("both options off", securityPageText(), not(containsString(LEGACY_NOTICE)));

        config.setCreationOfLegacyTokenEnabled(true);
        assertThat(securityPageText(), containsString(LEGACY_NOTICE));

        config.setTokenGenerationOnCreationEnabled(true);
        assertThat(securityPageText(), containsString(LEGACY_NOTICE));

        config.setMaxTokenLifetimeDays(0);
        assertThat(securityPageText(), not(containsString(LEGACY_NOTICE)));
    }

    private HtmlForm securityForm() throws Exception {
        return j.createWebClient().goTo("configureSecurity").getFormByName("config");
    }

    private String securityPageText() throws Exception {
        HtmlPage page = j.createWebClient().goTo("configureSecurity");
        return page.asNormalizedText();
    }

    private void configure(JSONObject json) throws Exception {
        j.executeOnServer(() -> {
            ApiTokenPropertyConfiguration.get().configure(Stapler.getCurrentRequest2(), json);
            return null;
        });
    }

    private void setUpUsers() {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.ADMINISTER).everywhere().to("admin")
                .grant(Jenkins.READ).everywhere().to("reader"));
    }

    private String checkExemptUserIds(JenkinsRule.WebClient wc, HttpMethod method, String value) throws Exception {
        return check(wc, method, "checkExemptUserIds", "value=" + URLEncoder.encode(value, StandardCharsets.UTF_8));
    }

    private String check(JenkinsRule.WebClient wc, HttpMethod method, String check, String query) throws Exception {
        URL url = new URL(j.getURL(), "descriptorByName/" + ApiTokenPropertyConfiguration.class.getName() + "/" + check + "?" + query);
        Page page = wc.getPage(wc.addCrumb(new WebRequest(url, method)));
        return page.getWebResponse().getContentAsString();
    }

    /**
     * Counts the writes of the configuration.
     */
    @TestExtension
    public static class SaveCounter extends SaveableListener {
        static final AtomicInteger COUNT = new AtomicInteger();

        @Override
        public void onChange(Saveable o, XmlFile file) {
            if (o instanceof ApiTokenPropertyConfiguration) {
                COUNT.incrementAndGet();
            }
        }
    }
}
