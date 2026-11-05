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
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import java.util.regex.Pattern;
import jenkins.model.Jenkins;
import jenkins.security.ApiTokenProperty;
import jenkins.security.Messages;
import net.sf.json.JSONNull;
import net.sf.json.JSONObject;
import org.htmlunit.FailingHttpStatusCodeException;
import org.htmlunit.HttpMethod;
import org.htmlunit.Page;
import org.htmlunit.WebRequest;
import org.htmlunit.html.DomElement;
import org.htmlunit.html.HtmlCheckBoxInput;
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
    private static final String STRICT_FUTURE_NOTICE = "every new token, including those that scripts and plugins create, must expire within";
    private static final String LEGACY_NOTICE = "Legacy API tokens never expire, and the maximum lifetime does not apply to them";
    private static final String STRICT_LABEL = "Strict mode: no exemptions, and also limit the API tokens that scripts and plugins create";

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
        assertFalse(config.isStrict());
        assertFalse(new ApiTokenLifetimePolicy().isActive());
        assertFalse(new ApiTokenLifetimePolicy().isStrict(LocalDate.now()));
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
        assertFalse(config.isStrict());
        ApiTokenLifetimePolicy policy = new ApiTokenLifetimePolicy();
        assertFalse(policy.isActive());
        assertFalse(policy.isEnforcing(LocalDate.now()));
        assertFalse(policy.isStrict(LocalDate.now()));
        assertFalse(policy.isExempt(User.getById("alice", true), LocalDate.now()));
    }

    /**
     * The file has {@code <strict>true</strict>}, with a maximum and an exempt user: {@code Descriptor#load} fills the field
     * without calling the setter, and the policy sees it, and ignores the exemption.
     */
    @Test
    @LocalData
    void strictModeIsLoadedFromTheConfigurationFile() {
        ApiTokenPropertyConfiguration config = ApiTokenPropertyConfiguration.get();
        assertTrue(config.isStrict());
        assertEquals(30, config.getMaxTokenLifetimeDays());
        assertEquals("alice", config.getExemptUserIds());
        ApiTokenLifetimePolicy policy = new ApiTokenLifetimePolicy();
        assertTrue(policy.isStrict(LocalDate.now()));
        assertFalse(policy.isExempt(User.getById("alice", true), LocalDate.now()), "nobody is exempt in strict mode");
        assertTrue(policy.appliesTo(User.getById("alice", true), LocalDate.now()));
    }

    @Test
    void strictModeIsSavedAndLoaded() throws Exception {
        ApiTokenPropertyConfiguration config = ApiTokenPropertyConfiguration.get();
        File file = new File(j.jenkins.getRootDir(), ApiTokenPropertyConfiguration.class.getName() + ".xml");
        config.setMaxTokenLifetimeDays(30);

        config.setStrict(true);
        assertTrue(config.isStrict());
        assertThat(Files.readString(file.toPath(), StandardCharsets.UTF_8), containsString("<strict>true</strict>"));
        // what is on disk is what loads
        config.setStrict(false);
        assertFalse(config.isStrict());
        assertThat(Files.readString(file.toPath(), StandardCharsets.UTF_8), containsString("<strict>false</strict>"));
        config.setStrict(true);
        config.load();
        assertTrue(config.isStrict());
        assertTrue(new ApiTokenLifetimePolicy().isStrict(LocalDate.now()));
    }

    /**
     * The page shows the box as stored, so saving the page without touching it leaves the value as it is, whichever it is.
     */
    @Test
    void submitWithoutChangesKeepsTheStrictMode() throws Exception {
        ApiTokenPropertyConfiguration config = ApiTokenPropertyConfiguration.get();
        config.setMaxTokenLifetimeDays(30);
        config.setStrict(true);

        j.submit(securityForm());

        assertTrue(config.isStrict());
        assertEquals(30, config.getMaxTokenLifetimeDays());

        config.setStrict(false);
        j.submit(securityForm());
        assertFalse(config.isStrict());
        assertEquals(30, config.getMaxTokenLifetimeDays());
    }

    @Test
    void theStrictModeBoxIsRenderedWithItsLabelAndShowsTheStoredValue() throws Exception {
        ApiTokenPropertyConfiguration config = ApiTokenPropertyConfiguration.get();

        HtmlPage page = j.createWebClient().goTo("configureSecurity");
        HtmlCheckBoxInput box = strictBox(page.getFormByName("config"));
        assertFalse(box.isChecked(), "off by default");
        assertThat(page.asNormalizedText(), containsString(STRICT_LABEL));
        DomElement label = (DomElement) box.getParentNode().querySelector("label");
        assertEquals(STRICT_LABEL, label.asNormalizedText());
        assertThat("the box has its help", page.getWebResponse().getContentAsString(), containsString("/descriptor/" + ApiTokenPropertyConfiguration.class.getName() + "/help/strict"));

        config.setStrict(true);
        assertTrue(strictBox(securityForm()).isChecked(), "shows what is stored");
    }

    @Test
    void submitCheckedStrictBoxSetsStrictAndPersistsIt() throws Exception {
        ApiTokenPropertyConfiguration config = ApiTokenPropertyConfiguration.get();
        config.setMaxTokenLifetimeDays(30);
        File file = new File(j.jenkins.getRootDir(), ApiTokenPropertyConfiguration.class.getName() + ".xml");

        HtmlForm form = securityForm();
        strictBox(form).setChecked(true);
        SaveCounter.COUNT.set(0);
        j.submit(form);

        assertTrue(config.isStrict());
        assertEquals(1, SaveCounter.COUNT.get(), "one write for the whole form");
        assertThat(Files.readString(file.toPath(), StandardCharsets.UTF_8), containsString("<strict>true</strict>"));
        config.load();
        assertTrue(config.isStrict(), "what is on disk is what loads");
        assertTrue(new ApiTokenLifetimePolicy().isStrict(LocalDate.now()));
        assertEquals(30, config.getMaxTokenLifetimeDays());
    }

    @Test
    void submitUncheckedStrictBoxSetsStrictToFalse() throws Exception {
        ApiTokenPropertyConfiguration config = ApiTokenPropertyConfiguration.get();
        config.setMaxTokenLifetimeDays(30);
        config.setStrict(true);
        File file = new File(j.jenkins.getRootDir(), ApiTokenPropertyConfiguration.class.getName() + ".xml");

        HtmlForm form = securityForm();
        assertTrue(strictBox(form).isChecked());
        strictBox(form).setChecked(false);
        j.submit(form);

        assertFalse(config.isStrict(), "an unchecked box is posted as false, it is not ignored");
        assertThat(Files.readString(file.toPath(), StandardCharsets.UTF_8), containsString("<strict>false</strict>"));
        config.load();
        assertFalse(config.isStrict());
        assertFalse(new ApiTokenLifetimePolicy().isStrict(LocalDate.now()));
    }

    @Test
    void submitStrictBoxWithoutAMaximumIsAcceptedAndInert() throws Exception {
        ApiTokenPropertyConfiguration config = ApiTokenPropertyConfiguration.get();
        HtmlForm form = securityForm();
        strictBox(form).setChecked(true);
        form.getInputByName("_.enforceFrom").setValue(LocalDate.now().plusDays(3).toString());

        j.submit(form);

        assertTrue(config.isStrict());
        assertEquals(0, config.getMaxTokenLifetimeDays());
        assertFalse(new ApiTokenLifetimePolicy().isActive());
        assertFalse(new ApiTokenLifetimePolicy().isStrict(LocalDate.now()), "no maximum: nothing to be strict about");
    }

    @Test
    void togglingTheStrictBoxLeavesTheLegacyFlagsAndTheOtherFieldsAlone() throws Exception {
        ApiTokenPropertyConfiguration config = ApiTokenPropertyConfiguration.get();
        String date = LocalDate.now().plusDays(10).toString();
        config.setTokenGenerationOnCreationEnabled(true);
        config.setCreationOfLegacyTokenEnabled(true);
        config.setUsageStatisticsEnabled(false);
        config.setMaxTokenLifetimeDays(30);
        config.setExemptUserIds("alice");
        config.setEnforceFrom(date);

        HtmlForm form = securityForm();
        strictBox(form).setChecked(true);
        j.submit(form);

        assertTrue(config.isStrict());
        assertTrue(config.isTokenGenerationOnCreationEnabled());
        assertTrue(config.isCreationOfLegacyTokenEnabled());
        assertFalse(config.isUsageStatisticsEnabled());
        assertEquals(30, config.getMaxTokenLifetimeDays());
        assertEquals("alice", config.getExemptUserIds());
        assertEquals(date, config.getEnforceFrom());

        HtmlForm again = securityForm();
        strictBox(again).setChecked(false);
        j.submit(again);

        assertFalse(config.isStrict());
        assertTrue(config.isTokenGenerationOnCreationEnabled());
        assertTrue(config.isCreationOfLegacyTokenEnabled());
        assertFalse(config.isUsageStatisticsEnabled());
        assertEquals(30, config.getMaxTokenLifetimeDays());
        assertEquals("alice", config.getExemptUserIds());
        assertEquals(date, config.getEnforceFrom());
    }

    /** What the box sends when an invalid submission is refused: the strict mode is not half applied either. */
    @Test
    void anInvalidSubmissionDoesNotApplyTheStrictBox() throws Exception {
        ApiTokenPropertyConfiguration config = ApiTokenPropertyConfiguration.get();
        config.setMaxTokenLifetimeDays(10);

        HtmlForm form = securityForm();
        strictBox(form).setChecked(true);
        form.getInputByName("_.enforceFrom").setValue("2030-13-01");
        assertThrows(FailingHttpStatusCodeException.class, () -> j.submit(form));

        assertFalse(config.isStrict());
    }

    @Test
    void configureBindsTheStrictKeyAndLeavesItWhenAbsent() throws Exception {
        ApiTokenPropertyConfiguration config = ApiTokenPropertyConfiguration.get();

        configure(new JSONObject().element("strict", true));
        assertTrue(config.isStrict());
        configure(new JSONObject().element("usageStatisticsEnabled", false));
        assertTrue(config.isStrict(), "absent: unchanged");
        configure(new JSONObject().element("strict", false));
        assertFalse(config.isStrict());
        configure(new JSONObject());
        assertFalse(config.isStrict(), "absent: unchanged");
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

        assertEquals(ok, config.doCheckMaxTokenLifetimeDays("", false, "").kind);
        assertEquals(ok, config.doCheckMaxTokenLifetimeDays(null, false, null).kind);
        assertEquals(ok, config.doCheckMaxTokenLifetimeDays("  ", false, "  ").kind);
        assertEquals(ok, config.doCheckMaxTokenLifetimeDays("0", false, "").kind);
        assertEquals(error, config.doCheckMaxTokenLifetimeDays("-1", false, "").kind);
        assertEquals(error, config.doCheckMaxTokenLifetimeDays("abc", false, "").kind);
        assertEquals(error, config.doCheckMaxTokenLifetimeDays("1.5", false, "").kind);
        assertEquals(error, config.doCheckMaxTokenLifetimeDays("99999999999", false, "").kind);
        assertEquals(warning, config.doCheckMaxTokenLifetimeDays("1", false, "").kind);
        assertEquals(warning, config.doCheckMaxTokenLifetimeDays("3", false, "").kind);
        assertEquals(warning, config.doCheckMaxTokenLifetimeDays("6", false, "").kind);
        assertEquals(ok, config.doCheckMaxTokenLifetimeDays("7", false, "").kind, "a token is flagged when it expires in fewer than 7 days");
        assertEquals(ok, config.doCheckMaxTokenLifetimeDays("30", false, "").kind);
        assertEquals(ok, config.doCheckMaxTokenLifetimeDays(" 365000 ", false, "").kind, "no upper bound");

        assertThat(config.doCheckMaxTokenLifetimeDays("3", false, "").renderHtml(), containsString("less than 7 days"));
        assertThat(config.doCheckMaxTokenLifetimeDays("abc", false, "").renderHtml(), not(containsString("abc")));

        // A date has no effect without a maximum
        assertEquals(warning, config.doCheckMaxTokenLifetimeDays("0", false, "2099-01-01").kind);
        assertEquals(warning, config.doCheckMaxTokenLifetimeDays("", false, "2099-01-01").kind);
        assertEquals(ok, config.doCheckMaxTokenLifetimeDays("30", false, "2099-01-01").kind);
        assertEquals(warning, config.doCheckMaxTokenLifetimeDays("3", false, "2099-01-01").kind);
        assertEquals(error, config.doCheckMaxTokenLifetimeDays("-1", false, "2099-01-01").kind);
    }

    /**
     * Strict mode has no effect without a maximum, which the field says: one warning, and when the enforcement date is set as
     * well, the same warning names both, as they are both ignored.
     */
    @Test
    void doCheckMaxTokenLifetimeDaysWarnsThatStrictModeHasNoEffectWithoutAMaximum() {
        ApiTokenPropertyConfiguration config = ApiTokenPropertyConfiguration.get();
        FormValidation.Kind ok = FormValidation.Kind.OK;
        FormValidation.Kind warning = FormValidation.Kind.WARNING;
        FormValidation.Kind error = FormValidation.Kind.ERROR;
        String strictOnly = "Strict mode has no effect while the maximum lifetime is 0.";
        String both = "Strict mode and the enforcement date have no effect while the maximum lifetime is 0.";
        String dateOnly = "The enforcement date has no effect while the maximum lifetime is 0.";

        for (String noMaximum : new String[] {"0", "", "  ", null, " 0 "}) {
            FormValidation strict = config.doCheckMaxTokenLifetimeDays(noMaximum, true, "");
            assertEquals(warning, strict.kind, String.valueOf(noMaximum));
            assertThat(strict.renderHtml(), containsString(strictOnly));
            assertEquals(warning, config.doCheckMaxTokenLifetimeDays(noMaximum, true, null).kind);
            assertEquals(warning, config.doCheckMaxTokenLifetimeDays(noMaximum, true, "   ").kind, "a blank date is no date");

            FormValidation withDate = config.doCheckMaxTokenLifetimeDays(noMaximum, true, "2099-01-01");
            assertEquals(warning, withDate.kind);
            assertThat("one warning that says both", withDate.renderHtml(), containsString(both));
            assertThat(withDate.renderHtml(), not(containsString(dateOnly)));

            // the date alone keeps its own warning, and with neither there is nothing to say
            FormValidation onlyDate = config.doCheckMaxTokenLifetimeDays(noMaximum, false, "2099-01-01");
            assertEquals(warning, onlyDate.kind);
            assertThat(onlyDate.renderHtml(), containsString(dateOnly));
            assertEquals(ok, config.doCheckMaxTokenLifetimeDays(noMaximum, false, "").kind);
        }

        // with a maximum the strict mode is in effect, so there is nothing to warn about
        assertEquals(ok, config.doCheckMaxTokenLifetimeDays("30", true, "").kind);
        assertEquals(ok, config.doCheckMaxTokenLifetimeDays("7", true, "2099-01-01").kind);
        assertEquals(ok, config.doCheckMaxTokenLifetimeDays(" 365000 ", true, "").kind);
        assertEquals(warning, config.doCheckMaxTokenLifetimeDays("3", true, "").kind, "the warning about a short maximum is unchanged");
        assertThat(config.doCheckMaxTokenLifetimeDays("3", true, "").renderHtml(), containsString("less than 7 days"));

        // an invalid number is an error, whatever else is set
        for (String invalid : new String[] {"-1", "abc", "1.5", "99999999999"}) {
            assertEquals(error, config.doCheckMaxTokenLifetimeDays(invalid, true, "").kind, invalid);
            assertEquals(error, config.doCheckMaxTokenLifetimeDays(invalid, true, "2099-01-01").kind, invalid);
            assertThat(config.doCheckMaxTokenLifetimeDays(invalid, true, "2099-01-01").renderHtml(), not(containsString(invalid)));
        }
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
        assertEquals(nothingToSay, check(reader, HttpMethod.POST, "checkMaxTokenLifetimeDays", "value=0&strict=true&enforceFrom="));
        assertEquals(nothingToSay, check(reader, HttpMethod.POST, "checkEnforceFrom", "value=garbage"));
        assertNull(User.getById("ghost", false));

        // while an administrator does get the answers
        JenkinsRule.WebClient admin = j.createWebClient().withBasicCredentials("admin");
        assertThat(check(admin, HttpMethod.POST, "checkMaxTokenLifetimeDays", "value=-1&enforceFrom="), containsString("class=\"error\""));
        assertThat(check(admin, HttpMethod.POST, "checkMaxTokenLifetimeDays", "value=0&enforceFrom=2099-01-01"), containsString("class=\"warning\""));
        assertThat(check(admin, HttpMethod.POST, "checkEnforceFrom", "value=garbage"), containsString("class=\"error\""));
    }

    /**
     * What the form sends: the checkbox as the text {@code true} or {@code false} in the {@code strict} parameter, which
     * the method takes as a boolean, and nothing at all when it is a call that leaves it out.
     */
    @Test
    void doCheckMaxTokenLifetimeDaysReadsTheStrictParameterOfTheRequest() throws Exception {
        setUpUsers();
        JenkinsRule.WebClient admin = j.createWebClient().withBasicCredentials("admin");

        String strict = check(admin, HttpMethod.POST, "checkMaxTokenLifetimeDays", "value=0&strict=true&enforceFrom=");
        assertThat(strict, containsString("class=\"warning\""));
        assertThat(strict, containsString("Strict mode has no effect while the maximum lifetime is 0."));
        String both = check(admin, HttpMethod.POST, "checkMaxTokenLifetimeDays", "value=&strict=true&enforceFrom=2099-01-01");
        assertThat(both, containsString("Strict mode and the enforcement date have no effect while the maximum lifetime is 0."));

        String notStrict = check(admin, HttpMethod.POST, "checkMaxTokenLifetimeDays", "value=0&strict=false&enforceFrom=");
        assertEquals(FormValidation.ok().renderHtml(), notStrict);
        assertEquals(FormValidation.ok().renderHtml(), check(admin, HttpMethod.POST, "checkMaxTokenLifetimeDays", "value=0&enforceFrom="), "absent is false");
        assertEquals(FormValidation.ok().renderHtml(), check(admin, HttpMethod.POST, "checkMaxTokenLifetimeDays", "value=30&strict=true&enforceFrom="));
        assertThat(check(admin, HttpMethod.POST, "checkMaxTokenLifetimeDays", "value=-1&strict=true&enforceFrom="), containsString("class=\"error\""));
    }

    /**
     * The number field is checked again when the box changes: this is what the page does when someone ticks the box while the
     * number is 0. It runs the script of the page, which sends the {@code strict} and {@code enforceFrom} fields along.
     */
    @Test
    void tickingTheStrictBoxChecksTheMaximumAgain() throws Exception {
        setUpUsers();
        JenkinsRule.WebClient wc = j.createWebClient().withBasicCredentials("admin");
        HtmlPage page = wc.goTo("configureSecurity");
        HtmlForm form = page.getFormByName("config");
        DomElement number = form.getInputByName("_.maxTokenLifetimeDays");
        DomElement item = number.getFirstByXPath("ancestor::div[contains(@class, 'jenkins-form-item')]");
        DomElement area = (DomElement) item.querySelector(".validation-error-area");
        assertNotNull(area);
        wc.waitForBackgroundJavaScript(5000);
        assertThat("nothing to say while the box is not ticked", area.asNormalizedText(), not(containsString("Strict mode has no effect")));

        strictBox(form).click();
        wc.waitForBackgroundJavaScript(10_000);

        assertTrue(strictBox(form).isChecked());
        assertThat(area.asNormalizedText(), containsString("Strict mode has no effect while the maximum lifetime is 0."));

        // and again when the box is cleared
        strictBox(form).click();
        wc.waitForBackgroundJavaScript(10_000);
        assertThat(area.asNormalizedText(), not(containsString("Strict mode has no effect")));
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
        // derived from the @QueryParameter names of the check method, in the order of its parameters: the number is checked
        // again when the strict mode box or the date changes
        assertThat(form.getInputByName("_.maxTokenLifetimeDays").getAttribute("checkDependsOn"), equalTo("strict enforceFrom"));
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

    /**
     * With strict mode switched on nobody is exempt, as the checkbox says: the notice below it must not say that an
     * exempt account is spared.
     */
    @Test
    void theNoticeOfAFutureDateSaysThatNobodyIsExemptWhenStrictModeIsSwitchedOn() throws Exception {
        ApiTokenPropertyConfiguration config = ApiTokenPropertyConfiguration.get();
        LocalDate future = LocalDate.now().plusDays(10);
        config.setMaxTokenLifetimeDays(30);
        config.setEnforceFrom(future.toString());
        config.setStrict(true);

        String strict = securityPageText();
        assertThat(strict, containsString("From " + future + ", " + STRICT_FUTURE_NOTICE + " 30 days, and nobody is exempt: strict mode starts on that day. Existing tokens are not touched."));
        assertThat(strict, not(containsString("unless the account is exempt")));
        assertThat(strict, not(containsString(FUTURE_NOTICE)));

        config.setMaxTokenLifetimeDays(1);
        assertThat("singular", securityPageText(), containsString("From " + future + ", " + STRICT_FUTURE_NOTICE + " 1 day, and nobody is exempt:"));

        config.setMaxTokenLifetimeDays(Integer.MAX_VALUE);
        String huge = securityPageText();
        assertThat("no grouping separators", huge, containsString("must expire within 2147483647 days, and nobody is exempt:"));
        assertThat(huge, not(containsString("unless the account is exempt")));

        // the existing wording is that of the case where an exempt account can exist
        config.setMaxTokenLifetimeDays(30);
        config.setStrict(false);
        String notStrict = securityPageText();
        assertThat(notStrict, containsString("From " + future + ", " + FUTURE_NOTICE + " 30 days, unless the account is exempt. Existing tokens are not touched."));
        assertThat(notStrict, not(containsString("nobody is exempt: strict mode starts on that day")));

        // strict mode without a future date, or without a maximum, announces nothing
        config.setStrict(true);
        config.setEnforceFrom(LocalDate.now().toString());
        assertThat("today: in effect", securityPageText(), not(containsString("strict mode starts on that day")));
        config.setEnforceFrom(future.toString());
        config.setMaxTokenLifetimeDays(0);
        assertThat("no maximum", securityPageText(), not(containsString("strict mode starts on that day")));
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

    // Help

    /** The help file as the page serves it, the white space reduced to single spaces. */
    private String help(String field) throws Exception {
        return j.createWebClient()
                .goTo("descriptorByName/" + ApiTokenPropertyConfiguration.class.getName() + "/help/" + field, "text/html")
                .getWebResponse().getContentAsString()
                .replaceAll("\\s+", " ");
    }

    @Test
    void theHelpOfTheStrictModeIsServedAndSaysWhatItDoesAndWhatToPlanFor() throws Exception {
        String help = help("strict");

        // what it is: the first sentence does not promise more than what the lists below cover
        assertThat(help, containsString("Applies the maximum lifetime without exemptions, and also to the tokens that scripts and plugins create, not only to those created through the HTTP endpoints. It is off by default."));
        assertThat(help, not(containsString("every way of creating")));
        // when it has an effect
        assertThat(help, containsString("only has an effect while the maximum lifetime is greater than 0"));
        assertThat(help, containsString("it is only announced: nothing changes, and the exemptions still apply until that date"));
        assertThat(help, containsString("A stored date that cannot be read counts as already started."));
        // what it adds
        assertThat(help, containsString("enforced for scripts and plugins that create tokens through the Java API"));
        assertThat(help, containsString("<code>init.groovy.d</code>"));
        assertThat(help, containsString("gets an exception instead of a token"));
        assertThat(help, containsString("maximum is also enforced when a legacy API token is regenerated"));
        assertThat(help, containsString("Exemptions stop applying: nobody is exempt."));
        assertThat(help, containsString("A secret that a user has registered already cannot be registered again by a script either"));
        // what it does not cover
        assertThat(help, containsString("The legacy tokens that Jenkins creates automatically for new users while the legacy token options above are on."));
        assertThat(help, containsString("migrate when a user is loaded"));
        assertThat(help, containsString("calls the legacy regeneration of the token store directly"));
        assertThat(help, containsString("best-effort against administrators: they can still edit the configuration files of the users"));
        assertThat(help, containsString("The tokens that exist already. They keep their expiration; the compliance panel lists those that exceed the maximum."));
        // what to plan for
        assertThat(help, containsString("fails from the second start"));
        assertThat(help, containsString("fails from the first start"));
        assertThat(help, containsString("Automated tests or tools that create tokens without an expiry fail."));
        assertThat(help, not(containsString("JenkinsRule")));
        // the initial administrator
        assertThat(help, containsString("<code>-Djenkins.install.SetupWizard.adminInitialApiToken=true</code>"));
        assertThat(help, containsString("Jenkins will not start until strict mode is switched off or that system property is not set."));
        assertThat(help, containsString("The start fails the same way every time until then."));
        assertThat(help, containsString("To switch strict mode off, set <code>&lt;strict&gt;false&lt;/strict&gt;</code> in the file"
                + " <code>jenkins.security.apitoken.ApiTokenPropertyConfiguration.xml</code> under <code>$JENKINS_HOME</code>"
                + " or in the configuration as code file, or remove the system property, then restart."));
        assertThat(help, containsString("a configuration as code file that sets a security realm skips this step"));
        assertThat(help, containsString("Jenkins only logs a WARNING and creates no token. The start continues."));
        assertThat(help, containsString("Do not combine <code>adminInitialApiToken=true</code> with a pre-seeded configuration that turns strict mode on."));
        assertThat(help, containsString("Create the initial token by script after the first start instead, with an expiration date that respects the maximum."));
        // Prettier puts a space between a code element and the punctuation that follows it, which shows
        assertFalse(Pattern.compile("</code>\\s*[,.;:)]").matcher(help).find(), "no punctuation directly after a code element");
        // plain language for administrators: nothing internal
        assertThat(help, not(containsString("C4")));
        assertThat(help, not(containsString("5.4")));
        assertThat(help, not(containsString("ApiTokenStore")));
        assertThat(help, not(containsString("ApiTokenPolicyViolationException")));
    }

    @Test
    void theOtherHelpFilesSayWhatStrictModeChanges() throws Exception {
        String maximum = help("maxTokenLifetimeDays");
        assertThat(maximum, containsString("through the Java API, unless <em>Strict mode</em> is in effect: then the maximum covers them too, and nobody is exempt."));
        assertThat(maximum, containsString("are not limited, even in strict mode."));
        assertThat(maximum, containsString("are not restricted, unless strict mode is in effect."));
        assertThat(maximum, containsString("except while strict mode is switched on and has not started yet"));
        assertThat(maximum, containsString("In strict mode nobody is exempt, so the rule applies to them too."));
        assertThat(maximum, containsString("does not check the secrets either, unless strict mode is in effect."));

        String exempt = help("exemptUserIds");
        assertThat(exempt, containsString("Exemptions stop applying when <em>Strict mode</em> is in effect: nobody is exempt then, from the day the maximum lifetime is enforced."));
        assertThat(exempt, containsString("applies again if strict mode is switched off."));
        assertThat(exempt, containsString("While strict mode is switched on and has not started yet"));

        String date = help("enforceFrom");
        assertThat(date, containsString("<em>Strict mode</em> is switched on, it starts on this date too. Until then it is only announced, and the exemptions still apply."));
    }

    private HtmlForm securityForm() throws Exception {
        return j.createWebClient().goTo("configureSecurity").getFormByName("config");
    }

    private static HtmlCheckBoxInput strictBox(HtmlForm form) {
        return form.getInputByName("_.strict");
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
