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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.User;
import hudson.util.FormValidation;
import hudson.util.Secret;
import java.net.URL;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.List;
import jenkins.security.ApiTokenProperty;
import net.sf.json.JSONObject;
import org.htmlunit.HttpMethod;
import org.htmlunit.Page;
import org.htmlunit.WebRequest;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.html.HtmlSelect;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
@Issue("https://github.com/jenkinsci/jenkins/issues/27612")
class ApiTokenLifetimeTest {
    private static final String FIXED_TOKEN = "110123456789abcdef0123456789abcdef";
    private JenkinsRule j;
    private ApiTokenPropertyConfiguration configuration;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
        configuration = ApiTokenPropertyConfiguration.get();
    }

    @Test
    void unrestrictedByDefault() {
        assertEquals(0, configuration.getMaxTokenLifetimeDays());
        ApiTokenStore store = new ApiTokenStore();
        assertNull(store.findMatchingToken(store.generateNewToken("no expiration").plainValue).getExpirationDate());
        LocalDate expiration = LocalDate.now().plusYears(10);
        assertEquals(expiration, store.findMatchingToken(store.generateNewToken("long lifetime", expiration).plainValue).getExpirationDate());
        store.addFixedNewToken("imported", FIXED_TOKEN, null);
    }

    @Test
    void enforceLifetimeOnGeneratedAndImportedTokens() {
        configuration.setMaxTokenLifetimeDays(30);
        LocalDate maximum = LocalDate.now().plusDays(30);
        ApiTokenStore store = new ApiTokenStore();
        assertThrows(IllegalArgumentException.class, () -> store.generateNewToken("no expiration"));
        assertThrows(IllegalArgumentException.class, () -> store.generateNewToken("too long", maximum.plusDays(1)));
        assertThrows(IllegalArgumentException.class, () -> store.addFixedNewToken("no expiration", FIXED_TOKEN, null));
        assertThrows(IllegalArgumentException.class, () -> store.addFixedNewToken("too long", FIXED_TOKEN, maximum.plusDays(1)));
        assertTrue(store.getTokenListSortedByName().isEmpty());
        assertEquals(maximum, store.findMatchingToken(store.generateNewToken("boundary", maximum).plainValue).getExpirationDate());
        store.generateNewToken("shorter", maximum.minusDays(1));
        store.addFixedNewToken("imported boundary", FIXED_TOKEN, maximum);
        assertEquals(3, store.getTokenListSortedByName().size());
    }

    @Test
    void existingTokensSurvivePolicyChangesAndReload() throws Exception {
        User user = User.getById("alice", true);
        ApiTokenProperty property = user.getProperty(ApiTokenProperty.class);
        TokenUuidAndPlainValue neverExpires = property.generateNewToken("existing");
        LocalDate expiration = LocalDate.now().plusDays(90);
        TokenUuidAndPlainValue longLived = property.generateNewToken("existing long lifetime", expiration);
        configuration.setMaxTokenLifetimeDays(7);
        user.load();
        property = user.getProperty(ApiTokenProperty.class);
        assertTrue(property.matchesPassword(neverExpires.plainValue));
        assertTrue(property.matchesPassword(longLived.plainValue));
        assertEquals(expiration, property.getTokenStore().findMatchingToken(longLived.plainValue).getExpirationDate());
        configuration.setMaxTokenLifetimeDays(0);
        assertEquals("Never", property.generateNewToken("unrestricted again").expirationDate);
    }

    @Test
    void legacyCreationIsDisabledWithoutRevokingExistingTokens() throws Exception {
        configuration.setTokenGenerationOnCreationEnabled(true);
        configuration.setCreationOfLegacyTokenEnabled(true);
        User existing = User.getById("existing", true);
        ApiTokenProperty property = existing.getProperty(ApiTokenProperty.class);
        String legacyToken = property.getApiToken();
        configuration.setMaxTokenLifetimeDays(30);
        assertFalse(configuration.isTokenGenerationOnCreationEnabled());
        assertFalse(configuration.isCreationOfLegacyTokenEnabled());
        assertFalse(User.getById("new", true).getProperty(ApiTokenProperty.class).hasLegacyToken());
        assertThrows(IllegalArgumentException.class, property::changeApiToken);
        assertThrows(IllegalArgumentException.class,
                () -> property.getTokenStore().regenerateTokenFromLegacy(Secret.fromString("replacement")));
        assertTrue(property.matchesPassword(legacyToken));
        // Loading a pre-token-store user must still migrate its existing legacy token.
        property.getTokenStore().revokeAllTokens();
        existing.save();
        existing.load();
        assertTrue(existing.getProperty(ApiTokenProperty.class).matchesPassword(legacyToken));
    }

    @Test
    void configurationValidationAndPersistence() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> configuration.setMaxTokenLifetimeDays(-1));
        assertEquals(FormValidation.Kind.ERROR, configuration.doCheckMaxTokenLifetimeDays("-1").kind);
        assertEquals(FormValidation.Kind.ERROR, configuration.doCheckMaxTokenLifetimeDays("abc").kind);
        assertEquals(FormValidation.Kind.OK, configuration.doCheckMaxTokenLifetimeDays("0").kind);
        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            HtmlPage page = wc.goTo("configureSecurity");
            HtmlForm form = page.getFormByName("config");
            form.getInputByName("_.maxTokenLifetimeDays").setValue("14");
            j.submit(form);
        }
        assertEquals(14, configuration.getMaxTokenLifetimeDays());
        assertTrue(Files.readString(j.jenkins.getRootDir().toPath().resolve(ApiTokenPropertyConfiguration.class.getName() + ".xml")).contains("<maxTokenLifetimeDays>14</maxTokenLifetimeDays>"));
        configuration.load();
        assertEquals(14, configuration.getMaxTokenLifetimeDays());
    }

    @Test
    void tokenFormOffersOnlyAllowedExpirations() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        User user = User.getById("alice", true);
        try (JenkinsRule.WebClient wc = j.createWebClient().login("alice")) {
            for (int maximum : List.of(1, 7, 30, 100, 0)) {
                configuration.setMaxTokenLifetimeDays(maximum);
                HtmlPage page = wc.goTo(user.getUrl() + "/security/");
                HtmlSelect select = page.querySelector("#api-token-add-template select[name=expirationDuration]");
                assertNotNull(select);
                assertEquals(maximum == 0, select.getOptions().stream().anyMatch(o -> o.getValueAttribute().equals("never")));
                assertTrue(select.getOptions().stream()
                        .map(o -> o.getValueAttribute())
                        .filter(v -> !v.equals("never") && !v.equals("custom"))
                        .allMatch(v -> maximum == 0 || Integer.parseInt(v) <= maximum));
                assertEquals(Integer.toString(maximum == 0 ? 30 : Math.min(30, maximum)), select.getSelectedOptions().getFirst().getValueAttribute());
            }
        }
    }

    @Test
    void httpEndpointsEnforcePolicy() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setCrumbIssuer(null);
        User user = User.getById("alice", true);
        configuration.setMaxTokenLifetimeDays(7);
        try (JenkinsRule.WebClient wc = j.createWebClient().login("alice")) {
            for (String endpoint : List.of("generateNewToken", "addFixedToken")) {
                for (String duration : List.of("never", "8", "custom", "")) {
                    JSONObject response = post(wc, user, endpoint, duration, LocalDate.now().plusDays(8).toString());
                    assertEquals("error", response.getString("status"));
                }
                assertEquals("ok", post(wc, user, endpoint, "7", "").getString("status"));
                assertEquals("ok", post(wc, user, endpoint, "custom", LocalDate.now().plusDays(7).toString()).getString("status"));
            }
        }
        assertEquals(4, user.getProperty(ApiTokenProperty.class).getTokenList().size());
    }

    private JSONObject post(JenkinsRule.WebClient wc, User user, String endpoint, String duration, String date) throws Exception {
        String path = user.getUrl() + "/descriptorByName/" + ApiTokenProperty.class.getName() + "/" + endpoint
                + "?newTokenName=test&newTokenPlainValue=" + FIXED_TOKEN
                + "&expirationDuration=" + duration + "&tokenExpiration=" + date;
        Page response = wc.getPage(new WebRequest(new URL(j.getURL(), path), HttpMethod.POST));
        assertEquals(200, response.getWebResponse().getStatusCode());
        return JSONObject.fromObject(response.getWebResponse().getContentAsString());
    }
}
