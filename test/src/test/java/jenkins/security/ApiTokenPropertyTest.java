package jenkins.security;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.oneOf;
import static org.hamcrest.xml.HasXPath.hasXPath;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Util;
import hudson.XmlFile;
import hudson.model.AdministrativeMonitor;
import hudson.model.Cause;
import hudson.model.FreeStyleProject;
import hudson.model.Saveable;
import hudson.model.User;
import hudson.model.listeners.SaveableListener;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.security.HudsonPrivateSecurityRealm;
import jakarta.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import jenkins.model.IdStrategy;
import jenkins.model.Jenkins;
import jenkins.security.apitoken.ApiTokenPropertyConfiguration;
import jenkins.security.apitoken.ApiTokenStats;
import jenkins.security.apitoken.ApiTokenStore;
import jenkins.security.apitoken.LegacyApiTokenAdministrativeMonitor;
import jenkins.security.apitoken.TokenUuidAndPlainValue;
import net.sf.json.JSONObject;
import org.htmlunit.FailingHttpStatusCodeException;
import org.htmlunit.HttpMethod;
import org.htmlunit.Page;
import org.htmlunit.WebRequest;
import org.htmlunit.html.HtmlElement;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.html.HtmlSelect;
import org.htmlunit.util.NameValuePair;
import org.htmlunit.xml.XmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.hudson.test.recipes.LocalData;

/**
 * @author Kohsuke Kawaguchi
 */
@WithJenkins
class ApiTokenPropertyTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    /**
     * Tests the UI interaction and authentication.
     */
    @Test
    void basics() throws Exception {
        ApiTokenPropertyConfiguration tokenConfig = ApiTokenPropertyConfiguration.get();
        tokenConfig.setTokenGenerationOnCreationEnabled(true);

        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        User u = User.getById("foo", true);
        j.createWebClient().withBasicApiToken(u);
        final ApiTokenProperty t = u.getProperty(ApiTokenProperty.class);
        final String token = t.getApiToken();

        // Make sure that user is able to get the token via the interface
        try (ACLContext acl = ACL.as(u)) {
            assertEquals(token, t.getApiToken(), "User is unable to get its own token");
        }

        // test the authentication via Token
        WebClient wc = createClientForUser("foo");
        assertEquals(u, wc.executeOnServer(User::current));

        // Make sure the UI shows the token to the user
        HtmlPage config = wc.goTo(u.getUrl() + "/security/");
        HtmlForm form = config.getFormByName("config");
        assertEquals(token, form.getInputByName("_.apiToken").getValue());

        // round-trip shouldn't change the API token
        j.submit(form);
        assertSame(t, u.getProperty(ApiTokenProperty.class));
    }

    @Test
    void security49Upgrade() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        User u = User.getOrCreateByIdOrFullName("foo");
        String historicalInitialValue = Util.getDigestOf(Jenkins.get().getSecretKey() + ":" + u.getId());

        // we won't accept historically used initial value as it may be compromised
        ApiTokenProperty t = new ApiTokenProperty(historicalInitialValue);
        u.addProperty(t);
        String apiToken1 = t.getApiToken();
        assertNotEquals(apiToken1, Util.getDigestOf(historicalInitialValue));

        // the replacement for the compromised value must be consistent and cannot be random
        ApiTokenProperty t2 = new ApiTokenProperty(historicalInitialValue);
        u.addProperty(t2);
        assertEquals(apiToken1, t2.getApiToken());

        // any other value is OK. those are changed values
        t = new ApiTokenProperty(historicalInitialValue + "somethingElse");
        u.addProperty(t);
        assertEquals(t.getApiToken(), Util.getDigestOf(historicalInitialValue + "somethingElse"));
    }

    @Issue("SECURITY-200")
    @Test
    void adminsShouldBeUnableToSeeTokensByDefault() throws Exception {
        ApiTokenPropertyConfiguration tokenConfig = ApiTokenPropertyConfiguration.get();
        tokenConfig.setTokenGenerationOnCreationEnabled(true);

        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        User u = User.getOrCreateByIdOrFullName("foo");
        final ApiTokenProperty t = u.getProperty(ApiTokenProperty.class);
        t.generateNewToken("test");
        final String token = t.getApiToken();

        // Make sure the UI does not show the token to another user
        WebClient wc = createClientForUser("bar");
        HtmlPage config = wc.goTo(u.getUrl() + "/security/");
        HtmlForm form = config.getFormByName("config");
        assertEquals(Messages.ApiTokenProperty_ChangeToken_TokenIsHidden(), form.getInputByName("_.apiToken").getValue());
    }

    @Issue("SECURITY-200")
    @Test
    void adminsShouldBeUnableToChangeTokensByDefault() throws Exception {
        ApiTokenPropertyConfiguration tokenConfig = ApiTokenPropertyConfiguration.get();
        tokenConfig.setTokenGenerationOnCreationEnabled(true);

        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        User foo = User.getOrCreateByIdOrFullName("foo");
        User bar = User.getOrCreateByIdOrFullName("bar");
        final ApiTokenProperty t = foo.getProperty(ApiTokenProperty.class);
        final ApiTokenProperty.DescriptorImpl descriptor = (ApiTokenProperty.DescriptorImpl) t.getDescriptor();

        // Make sure that Admin can reset a token of another user
        WebClient wc = createClientForUser("bar")
                .withThrowExceptionOnFailingStatusCode(false);
        HtmlPage requirePOST = wc.goTo(foo.getUrl() + "/" + descriptor.getDescriptorUrl() + "/changeToken");
        assertEquals(HttpURLConnection.HTTP_BAD_METHOD,
                requirePOST.getWebResponse().getStatusCode(),
                "method should not be allowed");

        wc.setThrowExceptionOnFailingStatusCode(true);
        WebRequest request = new WebRequest(new URI(j.getURL().toString() + foo.getUrl() + "/" + descriptor.getDescriptorUrl() + "/changeToken").toURL(), HttpMethod.POST);
        HtmlPage res = wc.getPage(request);

        // TODO This nicer alternative requires https://github.com/jenkinsci/jenkins/pull/2268 or similar to work
//        HtmlPage res = requirePOST.getPage().getForms().get(0).getElementsByAttribute("input", "type", "submit").get(0).click();
        assertEquals(Messages.ApiTokenProperty_ChangeToken_SuccessHidden(), "<div>" + res.getBody().asNormalizedText() + "</div>", "Update token response is incorrect");
    }

    @Test
    void postWithUsernameAndTokenInBasicAuthHeader() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject("bar");
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        User.getById("foo", true);

        WebClient wc = createClientForUser("foo");
        WebRequest wr = new WebRequest(new URL(j.getURL(), "job/bar/build"), HttpMethod.POST);

        assertEquals(HttpURLConnection.HTTP_CREATED, wc.getPage(wr).getWebResponse().getStatusCode());

        j.waitUntilNoActivity();

        Cause.UserIdCause triggeredBy = p.getBuildByNumber(1).getCause(Cause.UserIdCause.class);
        assertEquals("foo", triggeredBy.getUserId());
    }

    @NonNull
    private WebClient createClientForUser(final String id) {
        User u = User.getById(id, true);

        WebClient wc = j.createWebClient();
        wc.withBasicApiToken(u);
        return wc;
    }

    @Test
    @Issue("JENKINS-32776")
    void generateNewTokenWithoutName() throws Exception {
        j.jenkins.setCrumbIssuer(null);
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());

        // user is still able to connect with legacy token
        User admin = User.getById("admin", true);

        WebClient wc = j.createWebClient();
        wc.withBasicCredentials("admin", "admin");

        GenerateNewTokenResponse token1 = generateNewToken(wc, "admin", "");
        assertNotEquals("", token1.tokenName.trim());

        GenerateNewTokenResponse token2 = generateNewToken(wc, "admin", "New Token");
        assertEquals("New Token", token2.tokenName);
    }

    @Test
    @LocalData
    @Issue("JENKINS-32776")
    void migrationFromLegacyToken() throws Exception {
        j.jenkins.setCrumbIssuer(null);

        // user is still able to connect with legacy token
        User admin = User.getById("admin", false);
        assertNotNull(admin, "Admin user not configured correctly in local data");
        ApiTokenProperty apiTokenProperty = admin.getProperty(ApiTokenProperty.class);

        WebClient wc = j.createWebClient();
        wc.withBasicCredentials("admin", "admin");
        checkUserIsConnected(wc);

        // 7be8e81ad5a350fa3f3e2acfae4adb14
        String localLegacyToken = apiTokenProperty.getApiTokenInsecure();
        wc = j.createWebClient();
        wc.withBasicCredentials("admin", localLegacyToken);
        checkUserIsConnected(wc);

        // can still renew it after (using API)
        assertEquals(1, apiTokenProperty.getTokenList().size());
        apiTokenProperty.changeApiToken();
        assertEquals(1, apiTokenProperty.getTokenList().size());
        String newLegacyToken = apiTokenProperty.getApiTokenInsecure();

        // use the new legacy api token
        wc = j.createWebClient();
        wc.withBasicCredentials("admin", newLegacyToken);
        checkUserIsConnected(wc);

        // but previous one is not more usable
        wc = j.createWebClient();
        wc.withBasicCredentials("admin", localLegacyToken);
        checkUserIsNotConnected(wc);

        // ===== new system =====

        // revoke the legacy
        ApiTokenStore.HashedToken legacyToken = apiTokenProperty.getTokenStore().getLegacyToken();
        assertNotNull(legacyToken);
        String legacyUuid = legacyToken.getUuid();

        wc = j.createWebClient();
        wc.withBasicCredentials("admin", newLegacyToken);
        revokeToken(wc, "admin", legacyUuid);

        assertEquals(0, apiTokenProperty.getTokenList().size());

        // check it does not work any more
        wc = j.createWebClient();
        wc.withBasicCredentials("admin", newLegacyToken);
        checkUserIsNotConnected(wc);

        wc = j.createWebClient();
        wc.withBasicCredentials("admin", localLegacyToken);
        checkUserIsNotConnected(wc);

        // ensure the user can still connect using its username / password
        wc = j.createWebClient();
        wc.withBasicCredentials("admin", "admin");
        checkUserIsConnected(wc);

        // generate new token with the new system
        wc = j.createWebClient();
        wc.login("admin", "admin");
        GenerateNewTokenResponse newToken = generateNewToken(wc, "admin", "New Token");

        // use the new one
        wc = j.createWebClient();
        wc.withBasicCredentials("admin", newToken.tokenValue);
        checkUserIsConnected(wc);
    }

    private void checkUserIsConnected(WebClient wc) throws Exception {
        XmlPage xmlPage = wc.goToXml("whoAmI/api/xml");
        assertThat(xmlPage, hasXPath("//name", is("admin")));
        assertThat(xmlPage, hasXPath("//anonymous", is("false")));
        assertThat(xmlPage, hasXPath("//authenticated", is("true")));
        assertThat(xmlPage, hasXPath("//authority", is("authenticated")));
    }

    private void checkUserIsNotConnected(WebClient wc) {
        FailingHttpStatusCodeException e = assertThrows(FailingHttpStatusCodeException.class, () -> wc.goToXml("whoAmI/api/xml"));
        assertEquals(401, e.getStatusCode());
    }

    @Test
    @Issue("JENKINS-32776")
    void legacyTokenChange() throws Exception {
        j.jenkins.setCrumbIssuer(null);
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());

        ApiTokenPropertyConfiguration config = ApiTokenPropertyConfiguration.get();

        config.setTokenGenerationOnCreationEnabled(true);

        User user = User.getById("user", true);
        WebClient wc = j.createWebClient();
        wc.withBasicCredentials("user", "user");
        ApiTokenProperty apiTokenProperty = user.getProperty(ApiTokenProperty.class);

        { // with one legacy token, we can change it using web UI or direct internal call
            String currentLegacyToken = apiTokenProperty.getApiToken();
            assertEquals(1, apiTokenProperty.getTokenList().size());

            config.setCreationOfLegacyTokenEnabled(true);
            {
                // change using web UI
                changeLegacyToken(wc, "user", true);
                String newLegacyToken = apiTokenProperty.getApiToken();
                assertNotEquals(newLegacyToken, currentLegacyToken);

                // change using internal call
                apiTokenProperty.changeApiToken();
                String newLegacyToken2 = apiTokenProperty.getApiToken();
                assertNotEquals(newLegacyToken2, newLegacyToken);
                assertNotEquals(newLegacyToken2, currentLegacyToken);

                currentLegacyToken = newLegacyToken2;
            }

            config.setCreationOfLegacyTokenEnabled(false);
            {
                // change using web UI
                changeLegacyToken(wc, "user", true);
                String newLegacyToken = apiTokenProperty.getApiToken();
                assertNotEquals(newLegacyToken, currentLegacyToken);

                // change using internal call
                apiTokenProperty.changeApiToken();
                String newLegacyToken2 = apiTokenProperty.getApiToken();
                assertNotEquals(newLegacyToken2, newLegacyToken);
                assertNotEquals(newLegacyToken2, currentLegacyToken);
            }
        }
        { // but without any legacy token, the direct internal call remains but web UI depends on config
            revokeAllToken(wc, user);

            checkCombinationWithConfigAndMethodForLegacyTokenCreation(config, wc, user);
        }
        { // only the legacy token have impact on that capability
            generateNewToken(wc, "user", "New token");

            checkCombinationWithConfigAndMethodForLegacyTokenCreation(config, wc, user);
        }
    }

    private void checkCombinationWithConfigAndMethodForLegacyTokenCreation(
            ApiTokenPropertyConfiguration config, WebClient wc, User user
    ) throws Exception {
        ApiTokenProperty apiTokenProperty = user.getProperty(ApiTokenProperty.class);

        config.setCreationOfLegacyTokenEnabled(true);
        {
            { // change using web UI
                changeLegacyToken(wc, "user", true);
                String newLegacyToken = apiTokenProperty.getApiToken();
                assertNotEquals(newLegacyToken, Messages.ApiTokenProperty_ChangeToken_CapabilityNotAllowed());
            }
            revokeLegacyToken(wc, user);

            // always possible
            changeTokenByDirectCall(apiTokenProperty);
            revokeLegacyToken(wc, user);
        }

        revokeAllToken(wc, user);

        config.setCreationOfLegacyTokenEnabled(false);
        {
            { // change not possible using web UI
                changeLegacyToken(wc, "user", false);
                String newLegacyToken = apiTokenProperty.getApiToken();
                assertEquals(newLegacyToken, Messages.ApiTokenProperty_NoLegacyToken());
            }
            revokeLegacyToken(wc, user);

            // always possible
            changeTokenByDirectCall(apiTokenProperty);
            revokeLegacyToken(wc, user);
        }
    }

    private void changeTokenByDirectCall(ApiTokenProperty apiTokenProperty) throws Exception {
        apiTokenProperty.changeApiToken();
        String newLegacyToken = apiTokenProperty.getApiToken();
        assertNotEquals(newLegacyToken, Messages.ApiTokenProperty_ChangeToken_CapabilityNotAllowed());
    }

    private void revokeAllToken(WebClient wc, User user) throws Exception {
        revokeAllTokenUsingFilter(wc, user, it -> true);
    }

    private void revokeLegacyToken(WebClient wc, User user) throws Exception {
        revokeAllTokenUsingFilter(wc, user, ApiTokenStore.HashedToken::isLegacy);
    }

    private void revokeAllTokenUsingFilter(WebClient wc, User user, Predicate<ApiTokenStore.HashedToken> filter) throws Exception {
        ApiTokenProperty apiTokenProperty = user.getProperty(ApiTokenProperty.class);
        List<String> uuidList = apiTokenProperty.getTokenStore().getTokenListSortedByName().stream()
                .filter(filter)
                .map(ApiTokenStore.HashedToken::getUuid)
                .toList();
        for (String uuid : uuidList) {
            revokeToken(wc, user.getId(), uuid);
        }
    }

    private void revokeToken(WebClient wc, String login, String tokenUuid) throws Exception {
        WebRequest request = new WebRequest(
                new URL(j.getURL(), "user/" + login + "/descriptorByName/" + ApiTokenProperty.class.getName() + "/revoke/?tokenUuid=" + tokenUuid),
                HttpMethod.POST
        );
        Page p = wc.getPage(request);
        assertEquals(200, p.getWebResponse().getStatusCode());
    }

    private void changeLegacyToken(WebClient wc, String login, boolean success) throws Exception {
        WebRequest request = new WebRequest(
                new URL(j.getURL(), "user/" + login + "/descriptorByName/" + ApiTokenProperty.class.getName() + "/changeToken/"),
                HttpMethod.POST
        );
        Page p = wc.getPage(request);
        assertEquals(200, p.getWebResponse().getStatusCode());
        if (success) {
            assertThat(p.getWebResponse().getContentAsString(), not(containsString(Messages.ApiTokenProperty_ChangeToken_CapabilityNotAllowed())));
        } else {
            assertThat(p.getWebResponse().getContentAsString(), containsString(Messages.ApiTokenProperty_ChangeToken_CapabilityNotAllowed()));
        }
    }

    public static class GenerateNewTokenResponse {
        public String tokenUuid;
        public String tokenName;
        public String tokenValue;
    }

    private GenerateNewTokenResponse generateNewToken(WebClient wc, String login, String tokenName) throws Exception {
        WebRequest request = new WebRequest(
                new URL(j.getURL(), "user/" + login + "/descriptorByName/" + ApiTokenProperty.class.getName() + "/generateNewToken/?newTokenName=" + tokenName),
                HttpMethod.POST
        );
        Page p = wc.getPage(request);
        assertEquals(200, p.getWebResponse().getStatusCode());

        String response = p.getWebResponse().getContentAsString();
        JSONObject responseJson = JSONObject.fromObject(response);
        Object result = responseJson.getJSONObject("data").toBean(GenerateNewTokenResponse.class);
        return (GenerateNewTokenResponse) result;
    }

    @Test
    @Issue("JENKINS-57484")
    void script_addFixedNewToken_Regular() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());

        User user = User.getById("user", true);
        ApiTokenProperty apiTokenProperty = user.getProperty(ApiTokenProperty.class);

        Collection<ApiTokenStore.HashedToken> beforeTokenList = apiTokenProperty.getTokenStore().getTokenListSortedByName();

        String tokenPlainTextValue = "110123456789abcdef0123456789abcdef";
        apiTokenProperty.addFixedNewToken("fixed-token", tokenPlainTextValue);

        Collection<ApiTokenStore.HashedToken> afterTokenList = apiTokenProperty.getTokenStore().getTokenListSortedByName();
        // ensure the token is created
        assertEquals(beforeTokenList.size() + 1, afterTokenList.size());
        // ensure the token is working

        checkTokenIsWorking(user.getId(), tokenPlainTextValue);
    }

    @Test
    @Issue("JENKINS-57484")
    void script_addFixedNewToken_Invalid() {
        User user = User.getById("user", true);
        ApiTokenProperty apiTokenProperty = user.getProperty(ApiTokenProperty.class);

        Collection<ApiTokenStore.HashedToken> beforeTokenList = apiTokenProperty.getTokenStore().getTokenListSortedByName();

        checkInvalidTokenValue(apiTokenProperty, "invalid-token: too-long", "110123456789abcdef0123456789abcdefg");
        checkInvalidTokenValue(apiTokenProperty, "invalid-token: too-short", "110123456789abcdef0123456789abcde");
        checkInvalidTokenValue(apiTokenProperty, "invalid-token: non-hex", "110123456789abcdef0123456789abcdeg");
        checkInvalidTokenValue(apiTokenProperty, "invalid-token: invalid-version", "120123456789abcdef0123456789abcdef");

        Collection<ApiTokenStore.HashedToken> afterTokenList = apiTokenProperty.getTokenStore().getTokenListSortedByName();
        // ensure there is no new tokens
        assertEquals(beforeTokenList.size(), afterTokenList.size());
    }

    @Test
    @Issue("JENKINS-75492")
    void script_generateNewExpiringToken() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());

        LocalDate tomorrow = LocalDate.now().plusDays(1);

        User user = User.getById("user", true);
        ApiTokenProperty apiTokenProperty = user.getProperty(ApiTokenProperty.class);

        Collection<ApiTokenStore.HashedToken> beforeTokenList = apiTokenProperty.getTokenStore().getTokenListSortedByName();

        TokenUuidAndPlainValue token1 = apiTokenProperty.generateNewToken("token1", tomorrow);

        Collection<ApiTokenStore.HashedToken> afterTokenList = apiTokenProperty.getTokenStore().getTokenListSortedByName();
        assertEquals(beforeTokenList.size() + 1, afterTokenList.size());

        checkTokenIsWorking(user.getId(), token1.plainValue);

        TokenUuidAndPlainValue token2 = apiTokenProperty.generateNewToken("token2", tomorrow);
        checkTokenIsWorking(user.getId(), token2.plainValue);
        TokenUuidAndPlainValue token3 = apiTokenProperty.generateNewToken("token3", tomorrow);
        checkTokenIsWorking(user.getId(), token3.plainValue);

        Collection<ApiTokenStore.HashedToken> afterTokenList2 = apiTokenProperty.getTokenStore().getTokenListSortedByName();
        assertEquals(beforeTokenList.size() + 3, afterTokenList2.size());
    }

    @Test
    @Issue("JENKINS-75492")
    void script_expiredTokenIsInvalid() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());

        LocalDate expired = LocalDate.now().minusDays(1);

        User user = User.getById("user", true);
        ApiTokenProperty apiTokenProperty = user.getProperty(ApiTokenProperty.class);

        Collection<ApiTokenStore.HashedToken> beforeTokenList = apiTokenProperty.getTokenStore().getTokenListSortedByName();

        TokenUuidAndPlainValue token1 = apiTokenProperty.generateNewToken("token1", expired);

        Collection<ApiTokenStore.HashedToken> afterTokenList = apiTokenProperty.getTokenStore().getTokenListSortedByName();
        assertEquals(beforeTokenList.size() + 1, afterTokenList.size());

        checkTokenIsNotWorking(user.getId(), token1.plainValue);

        TokenUuidAndPlainValue token2 = apiTokenProperty.generateNewToken("token2", expired);
        checkTokenIsNotWorking(user.getId(), token2.plainValue);
        TokenUuidAndPlainValue token3 = apiTokenProperty.generateNewToken("token3", expired);
        checkTokenIsNotWorking(user.getId(), token3.plainValue);

        Collection<ApiTokenStore.HashedToken> afterTokenList2 = apiTokenProperty.getTokenStore().getTokenListSortedByName();
        assertEquals(beforeTokenList.size() + 3, afterTokenList2.size());
    }

    @Test
    @Issue("JENKINS-57484")
    void script_generateNewToken() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());

        User user = User.getById("user", true);
        ApiTokenProperty apiTokenProperty = user.getProperty(ApiTokenProperty.class);

        Collection<ApiTokenStore.HashedToken> beforeTokenList = apiTokenProperty.getTokenStore().getTokenListSortedByName();

        TokenUuidAndPlainValue token1 = apiTokenProperty.generateNewToken("token1");

        Collection<ApiTokenStore.HashedToken> afterTokenList = apiTokenProperty.getTokenStore().getTokenListSortedByName();
        assertEquals(beforeTokenList.size() + 1, afterTokenList.size());

        checkTokenIsWorking(user.getId(), token1.plainValue);

        TokenUuidAndPlainValue token2 = apiTokenProperty.generateNewToken("token2");
        checkTokenIsWorking(user.getId(), token2.plainValue);
        TokenUuidAndPlainValue token3 = apiTokenProperty.generateNewToken("token3");
        checkTokenIsWorking(user.getId(), token3.plainValue);

        Collection<ApiTokenStore.HashedToken> afterTokenList2 = apiTokenProperty.getTokenStore().getTokenListSortedByName();
        assertEquals(beforeTokenList.size() + 3, afterTokenList2.size());
    }

    @Test
    @Issue("JENKINS-57484")
    void script_revokeAllTokens() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());

        ApiTokenPropertyConfiguration config = ApiTokenPropertyConfiguration.get();
        config.setTokenGenerationOnCreationEnabled(true);

        User user = User.getById("user", true);
        ApiTokenProperty apiTokenProperty = user.getProperty(ApiTokenProperty.class);

        apiTokenProperty.revokeAllTokens();
        Collection<ApiTokenStore.HashedToken> tokenList = apiTokenProperty.getTokenStore().getTokenListSortedByName();
        // legacy token removed
        assertThat(tokenList, empty());

        apiTokenProperty.generateNewToken("token1");
        apiTokenProperty.revokeAllTokens();
        tokenList = apiTokenProperty.getTokenStore().getTokenListSortedByName();
        assertThat(tokenList, empty());

        LocalDate tomorrow = LocalDate.now().plusDays(1);

        apiTokenProperty.generateNewToken("token2", tomorrow);
        apiTokenProperty.revokeAllTokens();
        tokenList = apiTokenProperty.getTokenStore().getTokenListSortedByName();
        assertThat(tokenList, empty());

        String tokenPlainTextValue = "110123456789abcdef0123456789abcdef";
        apiTokenProperty.addFixedNewToken("fixed-token", tokenPlainTextValue);
        checkTokenIsWorking(user.getId(), tokenPlainTextValue);
        apiTokenProperty.revokeAllTokens();
        checkTokenIsNotWorking(user.getId(), tokenPlainTextValue);

        tokenList = apiTokenProperty.getTokenStore().getTokenListSortedByName();
        assertThat(tokenList, empty());
    }

    @Test
    @Issue("JENKINS-57484")
    void script_revokeAllTokensExceptOne() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());

        ApiTokenPropertyConfiguration config = ApiTokenPropertyConfiguration.get();
        config.setTokenGenerationOnCreationEnabled(true);

        User user = User.getById("user", true);
        ApiTokenProperty apiTokenProperty = user.getProperty(ApiTokenProperty.class);

        apiTokenProperty.generateNewToken("token0");
        TokenUuidAndPlainValue token1 = apiTokenProperty.generateNewToken("token1");
        TokenUuidAndPlainValue token2 = apiTokenProperty.generateNewToken("token2");
        apiTokenProperty.generateNewToken("token3");

        checkTokenIsWorking(user.getId(), token1.plainValue);
        checkTokenIsWorking(user.getId(), token2.plainValue);
        apiTokenProperty.revokeAllTokensExceptOne(token1.tokenUuid);
        checkTokenIsWorking(user.getId(), token1.plainValue);
        checkTokenIsNotWorking(user.getId(), token2.plainValue);

        Collection<ApiTokenStore.HashedToken> tokenList = apiTokenProperty.getTokenStore().getTokenListSortedByName();
        assertThat(tokenList, hasSize(1));
        assertEquals(token1.tokenUuid, tokenList.iterator().next().getUuid());

        String tokenPlainTextValue = "110123456789abcdef0123456789abcdef";
        apiTokenProperty.addFixedNewToken("fixed-token", tokenPlainTextValue);
        TokenUuidAndPlainValue token4 = apiTokenProperty.generateNewToken("token4");
        apiTokenProperty.revokeAllTokensExceptOne(token4.tokenUuid);

        tokenList = apiTokenProperty.getTokenStore().getTokenListSortedByName();
        assertThat(tokenList, hasSize(1));
        assertEquals(token4.tokenUuid, tokenList.iterator().next().getUuid());

        checkTokenIsNotWorking(user.getId(), token1.plainValue);
        checkTokenIsNotWorking(user.getId(), tokenPlainTextValue);
        checkTokenIsWorking(user.getId(), token4.plainValue);
    }

    @Test
    @Issue("JENKINS-57484")
    void script_revokeToken() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());

        ApiTokenPropertyConfiguration config = ApiTokenPropertyConfiguration.get();
        config.setTokenGenerationOnCreationEnabled(true);

        User user = User.getById("user", true);
        ApiTokenProperty apiTokenProperty = user.getProperty(ApiTokenProperty.class);

        apiTokenProperty.revokeAllTokens();

        TokenUuidAndPlainValue token1 = apiTokenProperty.generateNewToken("token1");
        TokenUuidAndPlainValue token2 = apiTokenProperty.generateNewToken("token2");
        TokenUuidAndPlainValue token3 = apiTokenProperty.generateNewToken("token3");

        checkTokenIsWorking(user.getId(), token1.plainValue);
        checkTokenIsWorking(user.getId(), token2.plainValue);
        checkTokenIsWorking(user.getId(), token3.plainValue);

        apiTokenProperty.revokeToken(token1.tokenUuid);

        checkTokenIsNotWorking(user.getId(), token1.plainValue);
        checkTokenIsWorking(user.getId(), token2.plainValue);
        checkTokenIsWorking(user.getId(), token3.plainValue);

        apiTokenProperty.revokeToken(token3.tokenUuid);

        checkTokenIsNotWorking(user.getId(), token1.plainValue);
        checkTokenIsWorking(user.getId(), token2.plainValue);
        checkTokenIsNotWorking(user.getId(), token3.plainValue);

        // no effect
        apiTokenProperty.revokeToken("invalid-uuid");

        checkTokenIsNotWorking(user.getId(), token1.plainValue);
        checkTokenIsWorking(user.getId(), token2.plainValue);
        checkTokenIsNotWorking(user.getId(), token3.plainValue);
    }

    private void checkTokenIsWorking(String login, String token) throws Exception {
        WebClient wc = j.createWebClient()
                .withBasicCredentials(login, token);
        XmlPage xmlPage = wc.goToXml("whoAmI/api/xml");
        assertThat(xmlPage, allOf(
                hasXPath("//name", is(login)),
                hasXPath("//anonymous", is("false")),
                hasXPath("//authenticated", is("true")),
                hasXPath("//authority", is("authenticated"))
        ));
    }

    private void checkTokenIsNotWorking(String login, String token) throws Exception {
        WebClient wc = j.createWebClient()
                .withBasicCredentials(login, token)
                .withThrowExceptionOnFailingStatusCode(false);
        // error page returned
        HtmlPage page = wc.goTo("whoAmI/api/xml");
        assertThat(page.getWebResponse().getStatusCode(), is(HttpServletResponse.SC_UNAUTHORIZED));
    }

    private void checkInvalidTokenValue(ApiTokenProperty apiTokenProperty, String tokenName, String tokenValue) {
        assertThrows(
                IllegalArgumentException.class,
                () -> apiTokenProperty.addFixedNewToken(tokenName, tokenValue),
                "The invalid token " + tokenName + " with value " + tokenValue + " was accepted but it should have been rejected.");
    }

    private static final String GENERATE_NEW_TOKEN = "generateNewToken";
    private static final String ADD_FIXED_TOKEN = "addFixedToken";
    private static final String FIXED_TOKEN = "110123456789abcdef0123456789abcdef";

    /**
     * What a token creation can change for a user: the tokens held in memory and the content of {@code config.xml}
     * ({@code null} when the user was never saved).
     */
    private record StoredState(List<String> tokenUuids, String configXml) {}

    private static StoredState storedState(User user) throws IOException {
        ApiTokenProperty property = user.getProperty(ApiTokenProperty.class);
        List<String> tokenUuids = property == null
                ? List.of()
                : property.getTokenStore().getTokenListSortedByName().stream().map(ApiTokenStore.HashedToken::getUuid).toList();
        File configFile = new File(user.getRootDir(), "config.xml");
        String configXml = configFile.exists() ? Files.readString(configFile.toPath(), StandardCharsets.UTF_8) : null;
        return new StoredState(tokenUuids, configXml);
    }

    /**
     * Creates the user and returns a client that calls the token creation endpoints the way the UI does: logged in
     * with the password of the dummy security realm (the user ID), without crumb. It does not authenticate with an
     * API token, so the tokens of the user are not involved.
     */
    private WebClient createClientForTokenEndpoints(String login) throws Exception {
        j.jenkins.setCrumbIssuer(null);
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        User.getById(login, true);
        WebClient wc = j.createWebClient().withBasicCredentials(login, login);
        // the first request of a user may save it (last granted authorities): do it now, not during the assertions
        wc.goToXml("whoAmI/api/xml");
        return wc;
    }

    /**
     * Posts to a token creation endpoint and returns the JSON answer, after checking the HTTP status is 200:
     * the endpoints report rejections with {@code status:error}, never with an HTTP error.
     *
     * @param parameters name and value pairs, a {@code null} value is not sent at all
     */
    private JSONObject postToTokenEndpoint(WebClient wc, String login, String endpoint, String... parameters) throws Exception {
        assertEquals(0, parameters.length % 2, "parameters are name and value pairs");
        List<NameValuePair> requestParameters = new ArrayList<>();
        for (int i = 0; i < parameters.length; i += 2) {
            if (parameters[i + 1] != null) {
                requestParameters.add(new NameValuePair(parameters[i], parameters[i + 1]));
            }
        }
        WebRequest request = new WebRequest(
                new URL(j.getURL(), "user/" + login + "/descriptorByName/" + ApiTokenProperty.class.getName() + "/" + endpoint),
                HttpMethod.POST
        );
        request.setRequestParameters(requestParameters);
        Page page = wc.getPage(request);
        assertEquals(200, page.getWebResponse().getStatusCode());
        return JSONObject.fromObject(page.getWebResponse().getContentAsString());
    }

    /**
     * Checks that a token creation request is rejected the way clients expect, HTTP 200 with {@code status:error} and
     * the given message, and that nothing is stored for the user (no token, user not saved).
     */
    private void assertRejected(WebClient wc, User user, String endpoint, String expectedMessage, String... parameters) throws Exception {
        StoredState before = storedState(user);
        JSONObject response = postToTokenEndpoint(wc, user.getId(), endpoint, parameters);
        assertEquals("error", response.getString("status"), response.toString());
        assertEquals(expectedMessage, response.getString("message"));
        assertEquals(before, storedState(user), "a rejected request must not change anything for the user");
    }

    private static JSONObject assertAccepted(JSONObject response) {
        assertEquals("ok", response.getString("status"), response.toString());
        return response.getJSONObject("data");
    }

    private static ApiTokenStore.HashedToken findToken(User user, String tokenUuid) {
        return user.getProperty(ApiTokenProperty.class).getTokenStore().getTokenListSortedByName().stream()
                .filter(token -> token.getUuid().equals(tokenUuid))
                .findFirst()
                .orElseThrow();
    }

    @Test
    @Issue("JENKINS-27612")
    void generateNewTokenWithCustomExpirationButNoDateIsRejected() throws Exception {
        WebClient wc = createClientForTokenEndpoints("user");
        User user = User.getById("user", false);
        String expected = Messages.ApiTokenProperty_expirationDateMissing();

        assertRejected(wc, user, GENERATE_NEW_TOKEN, expected, "newTokenName", "token", "expirationDuration", "custom");
        assertRejected(wc, user, GENERATE_NEW_TOKEN, expected, "newTokenName", "token", "expirationDuration", "custom", "tokenExpiration", "");
        assertRejected(wc, user, GENERATE_NEW_TOKEN, expected, "newTokenName", "token", "expirationDuration", "custom", "tokenExpiration", "   ");
    }

    @Test
    @Issue("JENKINS-27612")
    void generateNewTokenWithNonNumericDurationIsRejected() throws Exception {
        WebClient wc = createClientForTokenEndpoints("user");
        User user = User.getById("user", false);

        for (String duration : List.of("soon", "1.5", "99999999999")) {
            assertRejected(wc, user, GENERATE_NEW_TOKEN, Messages.ApiTokenProperty_expirationDurationInvalid(),
                    "newTokenName", "token", "expirationDuration", duration);
        }
    }

    @Test
    @Issue("JENKINS-27612")
    void generateNewTokenWithMalformedCustomDateIsRejected() throws Exception {
        WebClient wc = createClientForTokenEndpoints("user");
        User user = User.getById("user", false);

        for (String date : List.of("31/12/2030", "2030-13-01", "tomorrow")) {
            assertRejected(wc, user, GENERATE_NEW_TOKEN, Messages.ApiTokenProperty_expirationDateInvalid(),
                    "newTokenName", "token", "expirationDuration", "custom", "tokenExpiration", date);
        }
    }

    @Test
    @Issue("JENKINS-27612")
    void generateNewTokenWithNegativeNumberOfDaysIsRejected() throws Exception {
        WebClient wc = createClientForTokenEndpoints("user");
        User user = User.getById("user", false);

        for (String days : List.of("-1", "-365")) {
            assertRejected(wc, user, GENERATE_NEW_TOKEN, Messages.ApiTokenProperty_expirationInPast(),
                    "newTokenName", "token", "expirationDuration", days);
        }
    }

    @Test
    @Issue("JENKINS-27612")
    void generateNewTokenWithPastCustomDateIsRejected() throws Exception {
        WebClient wc = createClientForTokenEndpoints("user");
        User user = User.getById("user", false);

        assertRejected(wc, user, GENERATE_NEW_TOKEN, Messages.ApiTokenProperty_expirationInPast(),
                "newTokenName", "token", "expirationDuration", "custom", "tokenExpiration", LocalDate.now().minusDays(1).toString());
    }

    @Test
    @Issue("JENKINS-27612")
    void addFixedTokenWithPastCustomDateIsRejected() throws Exception {
        WebClient wc = createClientForTokenEndpoints("user");
        User user = User.getById("user", false);

        assertRejected(wc, user, ADD_FIXED_TOKEN, Messages.ApiTokenProperty_expirationInPast(),
                "newTokenName", "fixed", "newTokenPlainValue", FIXED_TOKEN,
                "expirationDuration", "custom", "tokenExpiration", LocalDate.now().minusDays(1).toString());
        checkTokenIsNotWorking("user", FIXED_TOKEN);
    }

    @Test
    @Issue("JENKINS-27612")
    void addFixedTokenWithNegativeNumberOfDaysIsRejected() throws Exception {
        WebClient wc = createClientForTokenEndpoints("user");
        User user = User.getById("user", false);

        for (String days : List.of("-1", "-365")) {
            assertRejected(wc, user, ADD_FIXED_TOKEN, Messages.ApiTokenProperty_expirationInPast(),
                    "newTokenName", "fixed", "newTokenPlainValue", FIXED_TOKEN, "expirationDuration", days);
        }
        checkTokenIsNotWorking("user", FIXED_TOKEN);
    }

    /**
     * {@code doAddFixedToken} shares the parsing of the expiration with {@code doGenerateNewToken}.
     */
    @Test
    @Issue("JENKINS-27612")
    void addFixedTokenWithInvalidExpirationIsRejected() throws Exception {
        WebClient wc = createClientForTokenEndpoints("user");
        User user = User.getById("user", false);

        assertRejected(wc, user, ADD_FIXED_TOKEN, Messages.ApiTokenProperty_expirationDurationInvalid(),
                "newTokenName", "fixed", "newTokenPlainValue", FIXED_TOKEN, "expirationDuration", "soon");
        assertRejected(wc, user, ADD_FIXED_TOKEN, Messages.ApiTokenProperty_expirationDateMissing(),
                "newTokenName", "fixed", "newTokenPlainValue", FIXED_TOKEN, "expirationDuration", "custom");
        assertRejected(wc, user, ADD_FIXED_TOKEN, Messages.ApiTokenProperty_expirationDateInvalid(),
                "newTokenName", "fixed", "newTokenPlainValue", FIXED_TOKEN, "expirationDuration", "custom", "tokenExpiration", "31/12/2030");
        checkTokenIsNotWorking("user", FIXED_TOKEN);
    }

    /**
     * The message of the token store contains the submitted value: the endpoint must answer with a static one.
     */
    @Test
    @Issue("JENKINS-27612")
    void addFixedTokenWithMalformedTokenValueIsRejectedWithoutEchoingIt() throws Exception {
        WebClient wc = createClientForTokenEndpoints("user");
        User user = User.getById("user", false);

        List<String> malformedValues = List.of(
                "110123456789abcdef0123456789abcde", // too short
                "110123456789abcdef0123456789abcdefg", // too long
                "120123456789abcdef0123456789abcdef", // unknown version
                "110123456789abcdef0123456789abcdeg"); // not hexadecimal
        for (String value : malformedValues) {
            // the exact message is checked, so nothing of the submitted value can be echoed
            assertRejected(wc, user, ADD_FIXED_TOKEN, Messages.ApiTokenProperty_tokenValueInvalid(),
                    "newTokenName", "fixed", "newTokenPlainValue", value);
        }
    }

    @Test
    @Issue("JENKINS-27612")
    void addFixedTokenWithMissingOrBlankTokenValueIsRejected() throws Exception {
        WebClient wc = createClientForTokenEndpoints("user");
        User user = User.getById("user", false);

        for (String value : new String[]{null, "", "   "}) {
            assertRejected(wc, user, ADD_FIXED_TOKEN, Messages.ApiTokenProperty_tokenValueMissing(),
                    "newTokenName", "fixed", "newTokenPlainValue", value);
        }
    }

    @Test
    @Issue("JENKINS-27612")
    void generateNewTokenReturnsExpirationDateInLocalizedAndIsoFormat() throws Exception {
        WebClient wc = createClientForTokenEndpoints("user");
        User user = User.getById("user", false);

        // an empty duration and an explicit never both mean no expiry
        for (String duration : new String[]{null, "", "never"}) {
            JSONObject data = assertAccepted(postToTokenEndpoint(wc, "user", GENERATE_NEW_TOKEN,
                    "newTokenName", "no-expiry", "expirationDuration", duration));
            assertEquals("never", data.getString("expirationDate"));
            assertEquals("never", data.getString("expirationDateIso"));
        }

        LocalDate inTenDays = LocalDate.now().plusDays(10);
        JSONObject data = assertAccepted(postToTokenEndpoint(wc, "user", GENERATE_NEW_TOKEN,
                "newTokenName", "custom-date", "expirationDuration", "custom", "tokenExpiration", inTenDays.toString()));
        assertEquals(inTenDays.toString(), data.getString("expirationDateIso"));
        assertEquals(inTenDays.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(Locale.US)), data.getString("expirationDate"));
        // what the answer says is what is stored, and the other fields are still there
        assertEquals(inTenDays, findToken(user, data.getString("tokenUuid")).getExpirationDate());
        assertEquals("custom-date", data.getString("tokenName"));
        assertFalse(data.getString("tokenValue").isEmpty());
        assertFalse(data.getBoolean("aboutToExpire"));
        checkTokenIsWorking("user", data.getString("tokenValue"));

        // 0 days is today, which is not in the past: the token is valid through its expiration date
        LocalDate before = LocalDate.now();
        data = assertAccepted(postToTokenEndpoint(wc, "user", GENERATE_NEW_TOKEN,
                "newTokenName", "today", "expirationDuration", "0"));
        LocalDate after = LocalDate.now();
        assertThat(data.getString("expirationDateIso"), is(oneOf(before.toString(), after.toString())));
        assertTrue(data.getBoolean("aboutToExpire"));

        // relative duration
        before = LocalDate.now();
        data = assertAccepted(postToTokenEndpoint(wc, "user", GENERATE_NEW_TOKEN,
                "newTokenName", "thirty-days", "expirationDuration", "30"));
        after = LocalDate.now();
        assertThat(data.getString("expirationDateIso"), is(oneOf(before.plusDays(30).toString(), after.plusDays(30).toString())));
        assertNotEquals("never", data.getString("expirationDate"));
    }

    /**
     * The duration is trimmed, as before: {@code " 30 "} is the same request as {@code "30"}, on both endpoints.
     */
    @Test
    @Issue("JENKINS-27612")
    void paddedDurationIsAcceptedLikeTheTrimmedOne() throws Exception {
        WebClient wc = createClientForTokenEndpoints("user");

        LocalDate before = LocalDate.now();
        JSONObject trimmed = assertAccepted(postToTokenEndpoint(wc, "user", GENERATE_NEW_TOKEN,
                "newTokenName", "trimmed", "expirationDuration", "30"));
        JSONObject padded = assertAccepted(postToTokenEndpoint(wc, "user", GENERATE_NEW_TOKEN,
                "newTokenName", "padded", "expirationDuration", " 30 "));
        JSONObject paddedFixed = assertAccepted(postToTokenEndpoint(wc, "user", ADD_FIXED_TOKEN,
                "newTokenName", "padded-fixed", "newTokenPlainValue", FIXED_TOKEN, "expirationDuration", " 30 "));
        LocalDate after = LocalDate.now();

        // the server computes today itself: accept the day before the requests and the day after them, in case midnight passes
        for (JSONObject data : List.of(trimmed, padded, paddedFixed)) {
            assertThat(data.getString("expirationDateIso"), is(oneOf(before.plusDays(30).toString(), after.plusDays(30).toString())));
        }
        assertNotEquals("never", padded.getString("expirationDate"));
        checkTokenIsWorking("user", FIXED_TOKEN);
    }

    /**
     * The exact boundary of the past-date check: a custom date equal to today is accepted, the token is valid through
     * its expiration date, and yesterday is rejected (see {@code generateNewTokenWithPastCustomDateIsRejected}).
     */
    @Test
    @Issue("JENKINS-27612")
    void customDateEqualToTodayIsAccepted() throws Exception {
        WebClient wc = createClientForTokenEndpoints("user");
        User user = User.getById("user", false);

        // only wrong if midnight passes between this line and the server computing its own today
        LocalDate today = LocalDate.now();
        JSONObject data = assertAccepted(postToTokenEndpoint(wc, "user", GENERATE_NEW_TOKEN,
                "newTokenName", "custom-today", "expirationDuration", "custom", "tokenExpiration", today.toString()));
        assertEquals(today.toString(), data.getString("expirationDateIso"));
        assertEquals(today, findToken(user, data.getString("tokenUuid")).getExpirationDate());
        checkTokenIsWorking("user", data.getString("tokenValue"));

        data = assertAccepted(postToTokenEndpoint(wc, "user", ADD_FIXED_TOKEN,
                "newTokenName", "fixed-custom-today", "newTokenPlainValue", FIXED_TOKEN,
                "expirationDuration", "custom", "tokenExpiration", today.toString()));
        assertEquals(today.toString(), data.getString("expirationDateIso"));
        assertEquals(today, findToken(user, data.getString("tokenUuid")).getExpirationDate());
        checkTokenIsWorking("user", FIXED_TOKEN);
    }

    @Test
    @Issue("JENKINS-27612")
    void addFixedTokenReturnsExpirationDateInLocalizedAndIsoFormatAndTheTokenAuthenticates() throws Exception {
        WebClient wc = createClientForTokenEndpoints("user");
        User user = User.getById("user", false);

        JSONObject data = assertAccepted(postToTokenEndpoint(wc, "user", ADD_FIXED_TOKEN,
                "newTokenName", "fixed-no-expiry", "newTokenPlainValue", FIXED_TOKEN, "expirationDuration", "never"));
        assertEquals("fixed-no-expiry", data.getString("tokenName"));
        assertEquals("never", data.getString("expirationDate"));
        assertEquals("never", data.getString("expirationDateIso"));
        assertNull(findToken(user, data.getString("tokenUuid")).getExpirationDate());
        checkTokenIsWorking("user", FIXED_TOKEN);

        String secondToken = "11fedcba9876543210fedcba9876543210";
        LocalDate inTenDays = LocalDate.now().plusDays(10);
        data = assertAccepted(postToTokenEndpoint(wc, "user", ADD_FIXED_TOKEN,
                "newTokenName", "fixed-custom-date", "newTokenPlainValue", secondToken,
                "expirationDuration", "custom", "tokenExpiration", inTenDays.toString()));
        assertEquals(inTenDays.toString(), data.getString("expirationDateIso"));
        assertEquals(inTenDays.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(Locale.US)), data.getString("expirationDate"));
        assertEquals(inTenDays, findToken(user, data.getString("tokenUuid")).getExpirationDate());
        checkTokenIsWorking("user", secondToken);

        // no duration at all, as the clients that send only the name and the value do
        String thirdToken = "11" + "ab".repeat(16);
        data = assertAccepted(postToTokenEndpoint(wc, "user", ADD_FIXED_TOKEN, "newTokenPlainValue", thirdToken));
        assertEquals("never", data.getString("expirationDate"));
        assertEquals("never", data.getString("expirationDateIso"));
        assertFalse(data.getString("tokenName").isBlank());
        checkTokenIsWorking("user", thirdToken);

        // relative duration
        String fourthToken = "11" + "cd".repeat(16);
        LocalDate before = LocalDate.now();
        data = assertAccepted(postToTokenEndpoint(wc, "user", ADD_FIXED_TOKEN,
                "newTokenName", "fixed-thirty-days", "newTokenPlainValue", fourthToken, "expirationDuration", "30"));
        LocalDate after = LocalDate.now();
        assertThat(data.getString("expirationDateIso"), is(oneOf(before.plusDays(30).toString(), after.plusDays(30).toString())));
        checkTokenIsWorking("user", fourthToken);
    }

    /**
     * A user without an {@link ApiTokenProperty} is practically unreachable, the property is allocated when the user
     * is loaded, so it is removed by reflection. A rejected request must neither create the property nor save the user;
     * an accepted {@code addFixedToken} creates the property with its token store ready (without it, storing the token
     * is a {@code NullPointerException}) and saves the user.
     */
    @Test
    @Issue("JENKINS-27612")
    void tokenCreationForUserWithoutApiTokenPropertyAddsItOnlyWhenAccepted() throws Exception {
        WebClient wc = createClientForTokenEndpoints("user");
        User user = User.getById("user", false);
        Field properties = User.class.getDeclaredField("properties");
        properties.setAccessible(true);
        ((List<?>) properties.get(user)).removeIf(ApiTokenProperty.class::isInstance);
        assertNull(user.getProperty(ApiTokenProperty.class));

        assertRejected(wc, user, GENERATE_NEW_TOKEN, Messages.ApiTokenProperty_expirationInPast(),
                "newTokenName", "token", "expirationDuration", "-1");
        assertRejected(wc, user, ADD_FIXED_TOKEN, Messages.ApiTokenProperty_expirationInPast(),
                "newTokenName", "fixed", "newTokenPlainValue", FIXED_TOKEN, "expirationDuration", "-1");
        assertRejected(wc, user, ADD_FIXED_TOKEN, Messages.ApiTokenProperty_tokenValueInvalid(),
                "newTokenName", "fixed", "newTokenPlainValue", "110123456789abcdef0123456789abcde");
        assertRejected(wc, user, ADD_FIXED_TOKEN, Messages.ApiTokenProperty_tokenValueMissing(),
                "newTokenName", "fixed");
        assertNull(user.getProperty(ApiTokenProperty.class), "rejected requests must not create the property");

        assertAccepted(postToTokenEndpoint(wc, "user", ADD_FIXED_TOKEN, "newTokenName", "fixed", "newTokenPlainValue", FIXED_TOKEN));
        assertNotNull(user.getProperty(ApiTokenProperty.class));
        assertThat(user.getProperty(ApiTokenProperty.class).getTokenStore().getTokenListSortedByName(), hasSize(1));
        checkTokenIsWorking("user", FIXED_TOKEN);

        // the user was saved with the property and its token
        user.load();
        assertThat(user.getProperty(ApiTokenProperty.class).getTokenStore().getTokenListSortedByName(), hasSize(1));
        checkTokenIsWorking("user", FIXED_TOKEN);
    }

    /**
     * The legacy token is created again from the {@code apiToken} secret when the user is loaded. Revoking all the
     * tokens must also drop that secret, or the legacy token is back, and works, after the next restart.
     */
    @Test
    @Issue("JENKINS-27612")
    void revokeAllTokensDoesNotBringTheLegacyTokenBackWhenTheUserIsLoaded() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        ApiTokenPropertyConfiguration.get().setTokenGenerationOnCreationEnabled(true);

        User user = User.getById("user", true);
        ApiTokenProperty apiTokenProperty = user.getProperty(ApiTokenProperty.class);
        assertTrue(apiTokenProperty.hasLegacyToken());
        String legacyToken = apiTokenProperty.getApiTokenInsecure();
        checkTokenIsWorking(user.getId(), legacyToken);

        apiTokenProperty.revokeAllTokens();
        // the in-memory state, which the legacy token monitor reads, before any reload
        assertFalse(apiTokenProperty.hasLegacyToken());
        checkTokenIsNotWorking(user.getId(), legacyToken);

        // what happens at the next start: the user is loaded from its config.xml
        user.load();
        ApiTokenProperty reloaded = user.getProperty(ApiTokenProperty.class);
        assertFalse(reloaded.hasLegacyToken());
        assertThat(reloaded.getTokenStore().getTokenListSortedByName(), empty());
        checkTokenIsNotWorking(user.getId(), legacyToken);
    }

    @Test
    @Issue("JENKINS-27612")
    void revokeAllTokensExceptOneDoesNotBringTheLegacyTokenBackWhenTheUserIsLoaded() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        ApiTokenPropertyConfiguration.get().setTokenGenerationOnCreationEnabled(true);

        User user = User.getById("user", true);
        ApiTokenProperty apiTokenProperty = user.getProperty(ApiTokenProperty.class);
        String legacyToken = apiTokenProperty.getApiTokenInsecure();
        TokenUuidAndPlainValue keptToken = apiTokenProperty.generateNewToken("kept");

        apiTokenProperty.revokeAllTokensExceptOne(keptToken.tokenUuid);
        // the in-memory state, which the legacy token monitor reads, before any reload
        assertFalse(apiTokenProperty.hasLegacyToken());
        checkTokenIsNotWorking(user.getId(), legacyToken);
        checkTokenIsWorking(user.getId(), keptToken.plainValue);

        user.load();
        ApiTokenProperty reloaded = user.getProperty(ApiTokenProperty.class);
        assertFalse(reloaded.hasLegacyToken());
        Collection<ApiTokenStore.HashedToken> tokenList = reloaded.getTokenStore().getTokenListSortedByName();
        assertThat(tokenList, hasSize(1));
        assertFalse(tokenList.iterator().next().isLegacy());
        checkTokenIsNotWorking(user.getId(), legacyToken);
        checkTokenIsWorking(user.getId(), keptToken.plainValue);
    }

    /**
     * When the legacy token is the one kept, its secret must stay: only the other tokens are revoked.
     */
    @Test
    @Issue("JENKINS-27612")
    void revokeAllTokensExceptOneKeepsTheLegacyTokenWhenItIsTheKeptOne() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        ApiTokenPropertyConfiguration.get().setTokenGenerationOnCreationEnabled(true);

        User user = User.getById("user", true);
        ApiTokenProperty apiTokenProperty = user.getProperty(ApiTokenProperty.class);
        String legacyToken = apiTokenProperty.getApiTokenInsecure();
        String legacyUuid = apiTokenProperty.getTokenStore().getLegacyToken().getUuid();
        TokenUuidAndPlainValue otherToken = apiTokenProperty.generateNewToken("other");

        apiTokenProperty.revokeAllTokensExceptOne(legacyUuid);
        assertTrue(apiTokenProperty.hasLegacyToken());
        checkTokenIsWorking(user.getId(), legacyToken);
        checkTokenIsNotWorking(user.getId(), otherToken.plainValue);

        user.load();
        ApiTokenProperty reloaded = user.getProperty(ApiTokenProperty.class);
        assertTrue(reloaded.hasLegacyToken());
        Collection<ApiTokenStore.HashedToken> tokenList = reloaded.getTokenStore().getTokenListSortedByName();
        assertThat(tokenList, hasSize(1));
        assertTrue(tokenList.iterator().next().isLegacy());
        checkTokenIsWorking(user.getId(), legacyToken);
        checkTokenIsNotWorking(user.getId(), otherToken.plainValue);
    }

    // ---- JENKINS-27612: the maximum lifetime of API tokens, as applied by the HTTP endpoints ----

    private static final DateTimeFormatter SHORT_DATE_US = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(Locale.US);

    /** Every accepted {@code addFixedToken} request registers a different secret. */
    private int fixedTokenCounter;

    /** The secret sent by the last {@code addFixedToken} request that {@link #creationParameters} built. */
    private String lastFixedToken;

    /**
     * The parameters of a token creation request for either endpoint: the name, for {@code addFixedToken} a new secret
     * (kept in {@link #lastFixedToken}) and the given expiration parameters, name and value pairs as in
     * {@link #postToTokenEndpoint}.
     */
    private String[] creationParameters(String endpoint, String... expirationParameters) {
        List<String> parameters = new ArrayList<>(List.of("newTokenName", "token"));
        if (ADD_FIXED_TOKEN.equals(endpoint)) {
            lastFixedToken = "11" + String.format("%032x", ++fixedTokenCounter);
            parameters.addAll(List.of("newTokenPlainValue", lastFixedToken));
        }
        Collections.addAll(parameters, expirationParameters);
        return parameters.toArray(String[]::new);
    }

    private JSONObject createToken(WebClient wc, String owner, String endpoint, String... expirationParameters) throws Exception {
        return postToTokenEndpoint(wc, owner, endpoint, creationParameters(endpoint, expirationParameters));
    }

    private void assertCreationRejected(WebClient wc, User owner, String endpoint, String expectedMessage, String... expirationParameters) throws Exception {
        assertRejected(wc, owner, endpoint, expectedMessage, creationParameters(endpoint, expirationParameters));
    }

    /** The secret of the token that an accepted request of the last {@link #creationParameters} created. */
    private String secretOf(String endpoint, JSONObject data) {
        return ADD_FIXED_TOKEN.equals(endpoint) ? lastFixedToken : data.getString("tokenValue");
    }

    /**
     * Checks that the request was accepted and that the token expires on one of the given dates (more than one when
     * midnight can pass between the test computing a date and the server doing it): what is stored, and what the answer says
     * in the localized and in the ISO format, are the same date.
     */
    private static JSONObject assertAcceptedExpiring(User owner, JSONObject response, LocalDate... possibleDates) {
        JSONObject data = assertAccepted(response);
        LocalDate stored = findToken(owner, data.getString("tokenUuid")).getExpirationDate();
        assertNotNull(stored, "the token must expire");
        assertThat(stored, is(oneOf(possibleDates)));
        assertEquals(stored.toString(), data.getString("expirationDateIso"));
        assertEquals(stored.format(SHORT_DATE_US), data.getString("expirationDate"));
        return data;
    }

    private static void assertAcceptedNeverExpiring(User owner, JSONObject response) {
        JSONObject data = assertAccepted(response);
        assertEquals("never", data.getString("expirationDate"));
        assertEquals("never", data.getString("expirationDateIso"));
        assertNull(findToken(owner, data.getString("tokenUuid")).getExpirationDate());
    }

    /**
     * With the maximum lifetime off, which is the default, nothing changes: an empty duration and {@code never} give a token
     * without expiry, and a far date is accepted.
     */
    @ParameterizedTest
    @ValueSource(strings = {GENERATE_NEW_TOKEN, ADD_FIXED_TOKEN})
    @Issue("JENKINS-27612")
    void withoutMaximumTheEndpointsAcceptAnyExpiration(String endpoint) throws Exception {
        WebClient wc = createClientForTokenEndpoints("user");
        User user = User.getById("user", false);
        assertEquals(0, ApiTokenPropertyConfiguration.get().getMaxTokenLifetimeDays());

        for (String duration : new String[]{null, "", "never"}) {
            assertAcceptedNeverExpiring(user, createToken(wc, "user", endpoint, "expirationDuration", duration));
        }
        LocalDate before = LocalDate.now();
        JSONObject response = createToken(wc, "user", endpoint, "expirationDuration", "3650");
        LocalDate after = LocalDate.now();
        assertAcceptedExpiring(user, response, before.plusDays(3650), after.plusDays(3650));
        LocalDate farAway = LocalDate.now().plusDays(5000);
        assertAcceptedExpiring(user, createToken(wc, "user", endpoint, "expirationDuration", "custom", "tokenExpiration", farAway.toString()), farAway);
    }

    /**
     * Under an enforced maximum a request that sends no duration, like the clients that send only the name of the token, keeps
     * working: the token expires on the last allowed day, and the answer says so.
     */
    @ParameterizedTest
    @ValueSource(strings = {GENERATE_NEW_TOKEN, ADD_FIXED_TOKEN})
    @Issue("JENKINS-27612")
    void enforcedMaximumGivesAnEmptyDurationTheLastAllowedDay(String endpoint) throws Exception {
        WebClient wc = createClientForTokenEndpoints("user");
        User user = User.getById("user", false);
        ApiTokenPropertyConfiguration.get().setMaxTokenLifetimeDays(30);

        for (String duration : new String[]{null, "", "   "}) {
            LocalDate before = LocalDate.now();
            JSONObject response = createToken(wc, "user", endpoint, "expirationDuration", duration);
            LocalDate after = LocalDate.now();
            JSONObject data = assertAcceptedExpiring(user, response, before.plusDays(30), after.plusDays(30));
            assertFalse(data.getString("tokenName").isBlank());
            checkTokenIsWorking("user", secretOf(endpoint, data));
        }
        // the name is optional too
        LocalDate before = LocalDate.now();
        JSONObject response = postToTokenEndpoint(wc, "user", endpoint, ADD_FIXED_TOKEN.equals(endpoint)
                ? new String[]{"newTokenPlainValue", "11" + "ef".repeat(16)}
                : new String[]{});
        LocalDate after = LocalDate.now();
        assertAcceptedExpiring(user, response, before.plusDays(30), after.plusDays(30));
    }

    /**
     * A number of days up to the maximum is accepted, today plus the maximum included: a token is valid through its
     * expiration date. One more day is rejected, whatever the size of the number.
     */
    @ParameterizedTest
    @ValueSource(strings = {GENERATE_NEW_TOKEN, ADD_FIXED_TOKEN})
    @Issue("JENKINS-27612")
    void enforcedMaximumAcceptsDaysUpToTheMaximumAndRejectsMore(String endpoint) throws Exception {
        WebClient wc = createClientForTokenEndpoints("user");
        User user = User.getById("user", false);
        ApiTokenPropertyConfiguration.get().setMaxTokenLifetimeDays(30);

        for (int days : new int[]{0, 1, 29, 30}) {
            LocalDate before = LocalDate.now();
            JSONObject response = createToken(wc, "user", endpoint, "expirationDuration", Integer.toString(days));
            LocalDate after = LocalDate.now();
            assertAcceptedExpiring(user, response, before.plusDays(days), after.plusDays(days));
        }
        // padding is trimmed, as without a maximum
        LocalDate before = LocalDate.now();
        JSONObject response = createToken(wc, "user", endpoint, "expirationDuration", " 30 ");
        LocalDate after = LocalDate.now();
        assertAcceptedExpiring(user, response, before.plusDays(30), after.plusDays(30));

        String expected = Messages.ApiTokenProperty_expirationOverMaximum(30);
        for (String days : List.of("31", "365", "2147483647")) {
            assertCreationRejected(wc, user, endpoint, expected, "expirationDuration", days);
        }
        assertCreationRejected(wc, user, endpoint, expected, "expirationDuration", " 31 ");
    }

    /**
     * Same boundary for a custom date: today plus the maximum is accepted, the next day is rejected.
     */
    @ParameterizedTest
    @ValueSource(strings = {GENERATE_NEW_TOKEN, ADD_FIXED_TOKEN})
    @Issue("JENKINS-27612")
    void enforcedMaximumAcceptsACustomDateUpToTheMaximumAndRejectsLater(String endpoint) throws Exception {
        WebClient wc = createClientForTokenEndpoints("user");
        User user = User.getById("user", false);
        ApiTokenPropertyConfiguration.get().setMaxTokenLifetimeDays(30);

        // only wrong if midnight passes between this line and the server computing its own today
        LocalDate today = LocalDate.now();
        for (LocalDate date : List.of(today, today.plusDays(1), today.plusDays(29), today.plusDays(30))) {
            assertAcceptedExpiring(user, createToken(wc, "user", endpoint, "expirationDuration", "custom", "tokenExpiration", date.toString()), date);
        }

        String expected = Messages.ApiTokenProperty_expirationOverMaximum(30);
        for (LocalDate date : List.of(today.plusDays(31), today.plusDays(365), today.plusYears(100))) {
            assertCreationRejected(wc, user, endpoint, expected, "expirationDuration", "custom", "tokenExpiration", date.toString());
        }
    }

    /**
     * The message says which maximum is configured, and the boundary follows it.
     */
    @ParameterizedTest
    @ValueSource(strings = {GENERATE_NEW_TOKEN, ADD_FIXED_TOKEN})
    @Issue("JENKINS-27612")
    void overMaximumMessageStatesTheConfiguredMaximum(String endpoint) throws Exception {
        WebClient wc = createClientForTokenEndpoints("user");
        User user = User.getById("user", false);

        for (int max : new int[]{1, 45, 365}) {
            ApiTokenPropertyConfiguration.get().setMaxTokenLifetimeDays(max);
            String expected = Messages.ApiTokenProperty_expirationOverMaximum(max);
            assertThat(expected, containsString(max == 1 ? "1 day " : max + " days"));

            LocalDate before = LocalDate.now();
            JSONObject response = createToken(wc, "user", endpoint, "expirationDuration", Integer.toString(max));
            LocalDate after = LocalDate.now();
            assertAcceptedExpiring(user, response, before.plusDays(max), after.plusDays(max));
            assertCreationRejected(wc, user, endpoint, expected, "expirationDuration", Integer.toString(max + 1));

            before = LocalDate.now();
            response = createToken(wc, "user", endpoint);
            after = LocalDate.now();
            assertAcceptedExpiring(user, response, before.plusDays(max), after.plusDays(max));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {GENERATE_NEW_TOKEN, ADD_FIXED_TOKEN})
    @Issue("JENKINS-27612")
    void enforcedMaximumRejectsNever(String endpoint) throws Exception {
        WebClient wc = createClientForTokenEndpoints("user");
        User user = User.getById("user", false);
        ApiTokenPropertyConfiguration.get().setMaxTokenLifetimeDays(30);

        assertCreationRejected(wc, user, endpoint, Messages.ApiTokenProperty_expirationNeverNotAllowed(), "expirationDuration", "never");
        // trimmed like the other durations, so the same request
        assertCreationRejected(wc, user, endpoint, Messages.ApiTokenProperty_expirationNeverNotAllowed(), "expirationDuration", " never ");
    }

    /**
     * What is invalid stays invalid, with its own message, under an enforced maximum: the expiration is parsed and checked
     * for the past before the maximum is applied.
     */
    @ParameterizedTest
    @ValueSource(strings = {GENERATE_NEW_TOKEN, ADD_FIXED_TOKEN})
    @Issue("JENKINS-27612")
    void enforcedMaximumKeepsRejectingPastDatesAndInvalidExpirations(String endpoint) throws Exception {
        WebClient wc = createClientForTokenEndpoints("user");
        User user = User.getById("user", false);
        ApiTokenPropertyConfiguration.get().setMaxTokenLifetimeDays(30);

        for (String days : List.of("-1", "-365")) {
            assertCreationRejected(wc, user, endpoint, Messages.ApiTokenProperty_expirationInPast(), "expirationDuration", days);
        }
        assertCreationRejected(wc, user, endpoint, Messages.ApiTokenProperty_expirationInPast(),
                "expirationDuration", "custom", "tokenExpiration", LocalDate.now().minusDays(1).toString());
        assertCreationRejected(wc, user, endpoint, Messages.ApiTokenProperty_expirationDurationInvalid(), "expirationDuration", "soon");
        assertCreationRejected(wc, user, endpoint, Messages.ApiTokenProperty_expirationDateMissing(), "expirationDuration", "custom");
        assertCreationRejected(wc, user, endpoint, Messages.ApiTokenProperty_expirationDateInvalid(),
                "expirationDuration", "custom", "tokenExpiration", "31/12/2030");
    }

    /**
     * An exempt owner keeps today's behaviour: no expiry for {@code never} and for an empty duration, any duration. The past
     * is still rejected.
     */
    @ParameterizedTest
    @ValueSource(strings = {GENERATE_NEW_TOKEN, ADD_FIXED_TOKEN})
    @Issue("JENKINS-27612")
    void exemptOwnerIsNotLimitedByTheMaximum(String endpoint) throws Exception {
        WebClient wc = createClientForTokenEndpoints("user");
        User user = User.getById("user", false);
        ApiTokenPropertyConfiguration config = ApiTokenPropertyConfiguration.get();
        config.setMaxTokenLifetimeDays(30);
        config.setExemptUserIds("someone\n  user \n");

        for (String duration : new String[]{null, "", "never", " never "}) {
            assertAcceptedNeverExpiring(user, createToken(wc, "user", endpoint, "expirationDuration", duration));
        }
        LocalDate before = LocalDate.now();
        JSONObject response = createToken(wc, "user", endpoint, "expirationDuration", "365");
        LocalDate after = LocalDate.now();
        assertAcceptedExpiring(user, response, before.plusDays(365), after.plusDays(365));
        LocalDate farAway = LocalDate.now().plusDays(400);
        assertAcceptedExpiring(user, createToken(wc, "user", endpoint, "expirationDuration", "custom", "tokenExpiration", farAway.toString()), farAway);

        assertCreationRejected(wc, user, endpoint, Messages.ApiTokenProperty_expirationInPast(), "expirationDuration", "-1");

        // the exemption is the user's, not a switch: another user is limited
        config.setExemptUserIds("someone");
        assertCreationRejected(wc, user, endpoint, Messages.ApiTokenProperty_expirationNeverNotAllowed(), "expirationDuration", "never");
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

    /** Creates the account, which creates the user, and returns a client logged in with its password. */
    private WebClient createClientForAccount(HudsonPrivateSecurityRealm realm, String login) throws Exception {
        realm.createAccount(login, login);
        WebClient wc = j.createWebClient().withBasicCredentials(login, login);
        // the first request of a user may save it: do it now, not during the assertions
        wc.goToXml("whoAmI/api/xml");
        return wc;
    }

    /**
     * The exemptions are matched with the {@link IdStrategy} of the security realm: with a case sensitive one, the owner whose
     * ID differs by the case is not exempt.
     */
    @ParameterizedTest
    @ValueSource(strings = {GENERATE_NEW_TOKEN, ADD_FIXED_TOKEN})
    @Issue("JENKINS-27612")
    void exemptionIsCaseSensitiveWithACaseSensitiveIdStrategy(String endpoint) throws Exception {
        j.jenkins.setCrumbIssuer(null);
        HudsonPrivateSecurityRealm realm = new IdStrategySpecifyingSecurityRealm(new IdStrategy.CaseSensitive());
        j.jenkins.setSecurityRealm(realm);
        WebClient lowerCaseClient = createClientForAccount(realm, "alice");
        WebClient upperCaseClient = createClientForAccount(realm, "Alice");
        User lowerCase = User.getById("alice", false);
        User upperCase = User.getById("Alice", false);
        assertNotEquals(lowerCase, upperCase);
        ApiTokenPropertyConfiguration config = ApiTokenPropertyConfiguration.get();
        config.setMaxTokenLifetimeDays(30);
        config.setExemptUserIds("alice");

        assertAcceptedNeverExpiring(lowerCase, createToken(lowerCaseClient, "alice", endpoint, "expirationDuration", "never"));
        assertCreationRejected(upperCaseClient, upperCase, endpoint, Messages.ApiTokenProperty_expirationNeverNotAllowed(), "expirationDuration", "never");
        LocalDate before = LocalDate.now();
        JSONObject response = createToken(upperCaseClient, "Alice", endpoint);
        LocalDate after = LocalDate.now();
        assertAcceptedExpiring(upperCase, response, before.plusDays(30), after.plusDays(30));
    }

    @ParameterizedTest
    @ValueSource(strings = {GENERATE_NEW_TOKEN, ADD_FIXED_TOKEN})
    @Issue("JENKINS-27612")
    void exemptionIgnoresTheCaseWithACaseInsensitiveIdStrategy(String endpoint) throws Exception {
        j.jenkins.setCrumbIssuer(null);
        HudsonPrivateSecurityRealm realm = new IdStrategySpecifyingSecurityRealm(new IdStrategy.CaseInsensitive());
        j.jenkins.setSecurityRealm(realm);
        WebClient upperCaseClient = createClientForAccount(realm, "ALICE");
        WebClient bobClient = createClientForAccount(realm, "bob");
        User upperCase = User.getById("ALICE", false);
        User bob = User.getById("bob", false);
        ApiTokenPropertyConfiguration config = ApiTokenPropertyConfiguration.get();
        config.setMaxTokenLifetimeDays(30);
        config.setExemptUserIds("alice");

        assertAcceptedNeverExpiring(upperCase, createToken(upperCaseClient, "ALICE", endpoint, "expirationDuration", "never"));
        assertCreationRejected(bobClient, bob, endpoint, Messages.ApiTokenProperty_expirationNeverNotAllowed(), "expirationDuration", "never");
    }

    /**
     * Before {@code enforceFrom} the policy only announces the maximum: nothing is rejected and nothing is filled in.
     */
    @ParameterizedTest
    @ValueSource(strings = {GENERATE_NEW_TOKEN, ADD_FIXED_TOKEN})
    @Issue("JENKINS-27612")
    void maximumIsNotAppliedBeforeTheEnforcementDate(String endpoint) throws Exception {
        WebClient wc = createClientForTokenEndpoints("user");
        User user = User.getById("user", false);
        ApiTokenPropertyConfiguration config = ApiTokenPropertyConfiguration.get();
        config.setMaxTokenLifetimeDays(30);
        // far enough from today for midnight not to matter
        config.setEnforceFrom(LocalDate.now().plusDays(10).toString());

        for (String duration : new String[]{null, "", "never"}) {
            assertAcceptedNeverExpiring(user, createToken(wc, "user", endpoint, "expirationDuration", duration));
        }
        LocalDate before = LocalDate.now();
        JSONObject response = createToken(wc, "user", endpoint, "expirationDuration", "365");
        LocalDate after = LocalDate.now();
        assertAcceptedExpiring(user, response, before.plusDays(365), after.plusDays(365));
        LocalDate farAway = LocalDate.now().plusDays(400);
        assertAcceptedExpiring(user, createToken(wc, "user", endpoint, "expirationDuration", "custom", "tokenExpiration", farAway.toString()), farAway);

        assertCreationRejected(wc, user, endpoint, Messages.ApiTokenProperty_expirationInPast(), "expirationDuration", "-1");
    }

    /**
     * The enforcement date is included, a past date enforces, and so does a blank one.
     */
    @ParameterizedTest
    @ValueSource(strings = {GENERATE_NEW_TOKEN, ADD_FIXED_TOKEN})
    @Issue("JENKINS-27612")
    void maximumIsAppliedFromTheEnforcementDateOnAndWithoutOne(String endpoint) throws Exception {
        WebClient wc = createClientForTokenEndpoints("user");
        User user = User.getById("user", false);
        ApiTokenPropertyConfiguration config = ApiTokenPropertyConfiguration.get();
        config.setMaxTokenLifetimeDays(30);

        // if midnight passes the date is in the past for the server, which enforces as well
        LocalDate today = LocalDate.now();
        for (String enforceFrom : new String[]{today.toString(), today.minusDays(1).toString(), "2000-01-01", "", "  ", null}) {
            config.setEnforceFrom(enforceFrom);
            assertCreationRejected(wc, user, endpoint, Messages.ApiTokenProperty_expirationNeverNotAllowed(), "expirationDuration", "never");
            assertCreationRejected(wc, user, endpoint, Messages.ApiTokenProperty_expirationOverMaximum(30), "expirationDuration", "31");
            LocalDate before = LocalDate.now();
            JSONObject response = createToken(wc, "user", endpoint);
            LocalDate after = LocalDate.now();
            assertAcceptedExpiring(user, response, before.plusDays(30), after.plusDays(30));
        }
    }

    /**
     * A stored enforcement date that does not parse can only come from a configuration file: the policy fails closed and enforces.
     */
    @Test
    @LocalData
    @Issue("JENKINS-27612")
    void unparsableEnforcementDateFailsClosed() throws Exception {
        assertEquals("2026-13-01", ApiTokenPropertyConfiguration.get().getEnforceFrom());
        WebClient wc = createClientForTokenEndpoints("user");
        User user = User.getById("user", false);

        for (String endpoint : List.of(GENERATE_NEW_TOKEN, ADD_FIXED_TOKEN)) {
            assertCreationRejected(wc, user, endpoint, Messages.ApiTokenProperty_expirationNeverNotAllowed(), "expirationDuration", "never");
            assertCreationRejected(wc, user, endpoint, Messages.ApiTokenProperty_expirationOverMaximum(30), "expirationDuration", "31");
            LocalDate before = LocalDate.now();
            JSONObject response = createToken(wc, "user", endpoint);
            LocalDate after = LocalDate.now();
            assertAcceptedExpiring(user, response, before.plusDays(30), after.plusDays(30));
        }
    }

    /**
     * The token of the owner is judged, not the user who sends the request: an administrator who creates a token for another user
     * is limited if the owner is, and is not if the owner is exempt, whatever the administrator's own status.
     */
    @ParameterizedTest
    @ValueSource(strings = {GENERATE_NEW_TOKEN, ADD_FIXED_TOKEN})
    @Issue("JENKINS-27612")
    void theOwnerOfTheTokenIsJudgedNotTheUserWhoSendsTheRequest(String endpoint) throws Exception {
        WebClient adminClient = createClientForTokenEndpoints("admin");
        User admin = User.getById("admin", false);
        User alice = User.getById("alice", true);
        User service = User.getById("service", true);
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.ADMINISTER).everywhere().to("admin")
                .grant(Jenkins.READ).everywhere().toAuthenticated());
        ApiTokenPropertyConfiguration config = ApiTokenPropertyConfiguration.get();
        config.setMaxTokenLifetimeDays(30);

        boolean previous = setAdminCanGenerateNewTokens(true);
        try {
            // the administrator is limited, the owner is exempt
            config.setExemptUserIds("service");
            assertAcceptedNeverExpiring(service, createToken(adminClient, "service", endpoint, "expirationDuration", "never"));
            assertAcceptedNeverExpiring(service, createToken(adminClient, "service", endpoint));
            assertCreationRejected(adminClient, alice, endpoint, Messages.ApiTokenProperty_expirationNeverNotAllowed(), "expirationDuration", "never");

            // the administrator is exempt, the owner is limited
            config.setExemptUserIds("admin");
            assertCreationRejected(adminClient, alice, endpoint, Messages.ApiTokenProperty_expirationNeverNotAllowed(), "expirationDuration", "never");
            assertCreationRejected(adminClient, alice, endpoint, Messages.ApiTokenProperty_expirationOverMaximum(30), "expirationDuration", "31");
            assertCreationRejected(adminClient, service, endpoint, Messages.ApiTokenProperty_expirationNeverNotAllowed(), "expirationDuration", "never");
            LocalDate before = LocalDate.now();
            JSONObject response = createToken(adminClient, "alice", endpoint);
            LocalDate after = LocalDate.now();
            assertAcceptedExpiring(alice, response, before.plusDays(30), after.plusDays(30));
            // and the exempt administrator is not limited for the administrator's own tokens
            assertAcceptedNeverExpiring(admin, createToken(adminClient, "admin", endpoint, "expirationDuration", "never"));
        } finally {
            setAdminCanGenerateNewTokens(previous);
        }
    }

    /**
     * The system property is read once, when the class is initialized: the test sets the field.
     *
     * @return the previous value
     */
    private static boolean setAdminCanGenerateNewTokens(boolean value) throws ReflectiveOperationException {
        Field field = ApiTokenProperty.class.getDeclaredField("ADMIN_CAN_GENERATE_NEW_TOKENS");
        field.setAccessible(true);
        boolean previous = field.getBoolean(null);
        field.setBoolean(null, value);
        return previous;
    }

    /**
     * The policy is applied before the property of a user who has none is built and added, which saves the user: a rejected
     * request leaves that user as it was.
     */
    @ParameterizedTest
    @ValueSource(strings = {GENERATE_NEW_TOKEN, ADD_FIXED_TOKEN})
    @Issue("JENKINS-27612")
    void rejectionByTheMaximumDoesNotCreateThePropertyOfAUserWithoutOne(String endpoint) throws Exception {
        WebClient wc = createClientForTokenEndpoints("user");
        User user = User.getById("user", false);
        Field properties = User.class.getDeclaredField("properties");
        properties.setAccessible(true);
        ((List<?>) properties.get(user)).removeIf(ApiTokenProperty.class::isInstance);
        assertNull(user.getProperty(ApiTokenProperty.class));
        ApiTokenPropertyConfiguration.get().setMaxTokenLifetimeDays(30);

        assertCreationRejected(wc, user, endpoint, Messages.ApiTokenProperty_expirationNeverNotAllowed(), "expirationDuration", "never");
        assertCreationRejected(wc, user, endpoint, Messages.ApiTokenProperty_expirationOverMaximum(30), "expirationDuration", "31");
        assertNull(user.getProperty(ApiTokenProperty.class), "rejected requests must not create the property");

        LocalDate before = LocalDate.now();
        JSONObject response = createToken(wc, "user", endpoint);
        LocalDate after = LocalDate.now();
        assertAcceptedExpiring(user, response, before.plusDays(30), after.plusDays(30));
    }

    /**
     * For {@code addFixedToken}: the value must be there before anything else, and the maximum is checked before the format
     * of the value, which is the last check, the one that needs the store.
     */
    @Test
    @Issue("JENKINS-27612")
    void addFixedTokenChecksTheMaximumAfterTheValueIsPresentAndBeforeItsFormat() throws Exception {
        WebClient wc = createClientForTokenEndpoints("user");
        User user = User.getById("user", false);
        ApiTokenPropertyConfiguration.get().setMaxTokenLifetimeDays(30);
        String malformed = "110123456789abcdef0123456789abcde";

        assertRejected(wc, user, ADD_FIXED_TOKEN, Messages.ApiTokenProperty_tokenValueMissing(),
                "newTokenName", "fixed", "expirationDuration", "never");
        assertRejected(wc, user, ADD_FIXED_TOKEN, Messages.ApiTokenProperty_expirationOverMaximum(30),
                "newTokenName", "fixed", "newTokenPlainValue", malformed, "expirationDuration", "31");
        assertRejected(wc, user, ADD_FIXED_TOKEN, Messages.ApiTokenProperty_expirationNeverNotAllowed(),
                "newTokenName", "fixed", "newTokenPlainValue", malformed, "expirationDuration", "never");
        assertRejected(wc, user, ADD_FIXED_TOKEN, Messages.ApiTokenProperty_tokenValueInvalid(),
                "newTokenName", "fixed", "newTokenPlainValue", malformed, "expirationDuration", "10");
        assertRejected(wc, user, ADD_FIXED_TOKEN, Messages.ApiTokenProperty_tokenValueInvalid(),
                "newTokenName", "fixed", "newTokenPlainValue", malformed);
    }

    /**
     * The maximum is forward only: the tokens that exist when it is enabled, of any kind, keep authenticating and stay as they are.
     */
    @Test
    @Issue("JENKINS-27612")
    void tokensCreatedBeforeTheMaximumKeepWorkingAfterItIsEnabled() throws Exception {
        ApiTokenPropertyConfiguration config = ApiTokenPropertyConfiguration.get();
        config.setTokenGenerationOnCreationEnabled(true);
        WebClient wc = createClientForTokenEndpoints("user");
        User user = User.getById("user", false);
        String legacyToken = user.getProperty(ApiTokenProperty.class).getApiTokenInsecure();
        assertTrue(user.getProperty(ApiTokenProperty.class).hasLegacyToken());

        JSONObject generated = assertAccepted(createToken(wc, "user", GENERATE_NEW_TOKEN, "expirationDuration", "never"));
        String generatedToken = generated.getString("tokenValue");
        LocalDate farAway = LocalDate.now().plusDays(365);
        assertAccepted(createToken(wc, "user", ADD_FIXED_TOKEN, "expirationDuration", "custom", "tokenExpiration", farAway.toString()));
        String fixedToken = lastFixedToken;
        checkTokenIsWorking("user", legacyToken);
        checkTokenIsWorking("user", generatedToken);
        checkTokenIsWorking("user", fixedToken);
        StoredState before = storedState(user);

        config.setMaxTokenLifetimeDays(30);

        checkTokenIsWorking("user", legacyToken);
        checkTokenIsWorking("user", generatedToken);
        checkTokenIsWorking("user", fixedToken);
        assertNull(findToken(user, generated.getString("tokenUuid")).getExpirationDate());
        assertEquals(before.tokenUuids(), storedState(user).tokenUuids());
        // new tokens are limited
        assertCreationRejected(wc, user, GENERATE_NEW_TOKEN, Messages.ApiTokenProperty_expirationNeverNotAllowed(), "expirationDuration", "never");
    }

    /**
     * Posts to the deprecated {@code changeToken} endpoint and returns the HTML answer, after checking the HTTP status is 200.
     */
    private String changeToken(WebClient wc, String owner) throws Exception {
        WebRequest request = new WebRequest(
                new URL(j.getURL(), "user/" + owner + "/descriptorByName/" + ApiTokenProperty.class.getName() + "/changeToken/"),
                HttpMethod.POST
        );
        Page page = wc.getPage(request);
        assertEquals(200, page.getWebResponse().getStatusCode());
        return page.getWebResponse().getContentAsString();
    }

    /** The legacy token is regenerated: the answer does not refuse, the old value stops working and the new one works. */
    private void assertLegacyTokenIsRegenerated(WebClient wc, User owner) throws Exception {
        ApiTokenProperty property = owner.getProperty(ApiTokenProperty.class);
        String before = property.getApiTokenInsecure();
        checkTokenIsWorking(owner.getId(), before);

        String response = changeToken(wc, owner.getId());
        assertThat(response, not(containsString(Messages.ApiTokenProperty_ChangeToken_MaximumLifetimeEnforced())));
        assertThat(response, not(containsString(Messages.ApiTokenProperty_ChangeToken_CapabilityNotAllowed())));
        String after = property.getApiTokenInsecure();
        assertNotEquals(before, after);
        checkTokenIsNotWorking(owner.getId(), before);
        checkTokenIsWorking(owner.getId(), after);
    }

    /** The legacy token is not regenerated: the answer says why, and nothing changes, in memory or in the saved user. */
    private void assertLegacyTokenIsKept(WebClient wc, User owner) throws Exception {
        ApiTokenProperty property = owner.getProperty(ApiTokenProperty.class);
        String before = property.getApiTokenInsecure();
        StoredState stored = storedState(owner);

        String response = changeToken(wc, owner.getId());
        assertThat(response, containsString(Messages.ApiTokenProperty_ChangeToken_MaximumLifetimeEnforced()));
        assertEquals(before, property.getApiTokenInsecure());
        assertEquals(stored, storedState(owner), "a rejected request must not change anything for the user");
        checkTokenIsWorking(owner.getId(), before);
    }

    /**
     * The legacy token never expires: regenerating it would give a token that the maximum does not cover, so the deprecated
     * endpoint is rejected for an owner who is not exempt once the maximum is enforced. The Java method is not enforced.
     */
    @Test
    @Issue("JENKINS-27612")
    void changeTokenIsRejectedForANonExemptOwnerWhenTheMaximumIsEnforced() throws Exception {
        ApiTokenPropertyConfiguration config = ApiTokenPropertyConfiguration.get();
        config.setTokenGenerationOnCreationEnabled(true);
        WebClient wc = createClientForTokenEndpoints("user");
        User user = User.getById("user", false);
        ApiTokenProperty property = user.getProperty(ApiTokenProperty.class);
        assertTrue(property.hasLegacyToken());
        String legacyToken = property.getApiTokenInsecure();

        config.setMaxTokenLifetimeDays(30);
        assertLegacyTokenIsKept(wc, user);
        // the same with a date that is today and one that is in the past, and with a blank one
        for (String enforceFrom : new String[]{LocalDate.now().toString(), "2000-01-01", ""}) {
            config.setEnforceFrom(enforceFrom);
            assertLegacyTokenIsKept(wc, user);
        }
        config.setExemptUserIds("someone else");
        assertLegacyTokenIsKept(wc, user);

        // only the HTTP endpoint is enforced
        property.changeApiToken();
        assertNotEquals(legacyToken, property.getApiTokenInsecure());
    }

    @Test
    @LocalData
    @Issue("JENKINS-27612")
    void changeTokenIsRejectedWhenTheEnforcementDateDoesNotParse() throws Exception {
        ApiTokenPropertyConfiguration.get().setTokenGenerationOnCreationEnabled(true);
        WebClient wc = createClientForTokenEndpoints("user");
        assertEquals("2026-13-01", ApiTokenPropertyConfiguration.get().getEnforceFrom());

        assertLegacyTokenIsKept(wc, User.getById("user", false));
    }

    /**
     * Without a maximum, before the enforcement date and for an exempt owner, the endpoint works as it did.
     */
    @Test
    @Issue("JENKINS-27612")
    void changeTokenWorksWhenTheMaximumDoesNotApply() throws Exception {
        ApiTokenPropertyConfiguration config = ApiTokenPropertyConfiguration.get();
        config.setTokenGenerationOnCreationEnabled(true);
        WebClient wc = createClientForTokenEndpoints("user");
        User user = User.getById("user", false);
        assertTrue(user.getProperty(ApiTokenProperty.class).hasLegacyToken());

        // no maximum
        assertLegacyTokenIsRegenerated(wc, user);

        // a maximum that is not enforced yet
        config.setMaxTokenLifetimeDays(30);
        config.setEnforceFrom(LocalDate.now().plusDays(10).toString());
        assertLegacyTokenIsRegenerated(wc, user);

        // an exempt owner
        config.setEnforceFrom(null);
        config.setExemptUserIds("user");
        assertLegacyTokenIsRegenerated(wc, user);
    }

    /**
     * The owner of the legacy token is judged, not the administrator who regenerates it.
     */
    @Test
    @Issue("JENKINS-27612")
    void changeTokenJudgesTheOwnerNotTheUserWhoSendsTheRequest() throws Exception {
        ApiTokenPropertyConfiguration config = ApiTokenPropertyConfiguration.get();
        config.setTokenGenerationOnCreationEnabled(true);
        WebClient adminClient = createClientForTokenEndpoints("admin");
        User admin = User.getById("admin", false);
        User alice = User.getById("alice", true);
        User service = User.getById("service", true);
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.ADMINISTER).everywhere().to("admin")
                .grant(Jenkins.READ).everywhere().toAuthenticated());
        config.setMaxTokenLifetimeDays(30);

        // the administrator is limited, the owner is exempt
        config.setExemptUserIds("service");
        assertLegacyTokenIsRegenerated(adminClient, service);
        assertLegacyTokenIsKept(adminClient, alice);

        // the administrator is exempt, the owner is limited
        config.setExemptUserIds("admin");
        assertLegacyTokenIsKept(adminClient, alice);
        assertLegacyTokenIsKept(adminClient, service);
        assertLegacyTokenIsRegenerated(adminClient, admin);
    }

    // ---- JENKINS-27612: the add-token dialog and the legacy token section of the page of a user ----

    /** What the dialog offers when the maximum does not apply to the owner, as {@code value=label,...}. */
    private static final String FULL_EXPIRATION_OPTIONS = "7=7 Days,30=30 Days,60=60 Days,90=90 Days,custom=Custom,never=No Expiration";

    private static final String NEVER_WARNING = "It is not recommended to create tokens that never expire.";

    private static HtmlPage tokenPage(WebClient wc, User owner) throws Exception {
        return wc.goTo(owner.getUrl() + "/security/");
    }

    /** The dialog is in the page as a hidden template, which the script clones when the user adds a token. */
    private static HtmlElement dialogTemplate(HtmlPage page) {
        return page.getHtmlElementById("api-token-add-template");
    }

    /** The options of the expiration select, as {@code value=label,...}. */
    private static String expirationOptions(HtmlPage page) {
        HtmlSelect select = dialogTemplate(page).getOneHtmlElementByAttribute("select", "name", "expirationDuration");
        return String.join(",", select.getOptions().stream()
                .map(option -> option.getValueAttribute() + "=" + option.getTextContent().strip().replaceAll("\\s+", " "))
                .toList());
    }

    /** The values of the options that the dialog selects when it opens. */
    private static List<String> selectedExpirations(HtmlPage page) {
        HtmlSelect select = dialogTemplate(page).getOneHtmlElementByAttribute("select", "name", "expirationDuration");
        return select.getOptions().stream()
                .filter(option -> option.hasAttribute("selected"))
                .map(option -> option.getValueAttribute())
                .toList();
    }

    private static void assertExpirationOptions(HtmlPage page, String expectedSelected, String expectedOptions) {
        assertEquals(expectedOptions, expirationOptions(page));
        assertEquals(List.of(expectedSelected), selectedExpirations(page));
    }

    /** The warning about tokens that never expire, {@code null} if the dialog has none because it does not offer them. */
    private static String neverWarning(HtmlPage page) {
        HtmlElement warning = dialogTemplate(page).querySelector(".token-warning");
        return warning == null ? null : warning.getTextContent().strip();
    }

    /** The text of the notice about the enforcement date, {@code null} if the dialog has none. */
    private static String enforceFromNotice(HtmlPage page) {
        HtmlElement notice = dialogTemplate(page).querySelector(".token-enforce-from-notice");
        return notice == null ? null : notice.getTextContent().strip().replaceAll("\\s+", " ");
    }

    private static String enforceFromNoticeText(String date, String days) {
        return "From " + date + ", tokens must expire within " + days + ", unless your account is exempt. Existing tokens are not touched.";
    }

    private static boolean hasChangeTokenButton(HtmlPage page) {
        return page.querySelector("button[data-validate-button-method='changeToken']") != null;
    }

    /**
     * An owner who is subject to an enforced maximum is offered the usual choices that the maximum allows, and the maximum itself
     * when it is not one of them. The default is the largest of them that is not longer than 30 days. There is no token that never
     * expires, so no option for it and no warning about it.
     */
    @ParameterizedTest
    @CsvSource(delimiter = ';', value = {
        "90;30;7=7 Days,30=30 Days,60=60 Days,90=90 Days",
        "100;30;7=7 Days,30=30 Days,60=60 Days,90=90 Days,100=100 Days (maximum)",
        "45;30;7=7 Days,30=30 Days,45=45 Days (maximum)",
        "30;30;7=7 Days,30=30 Days",
        "29;29;7=7 Days,29=29 Days (maximum)",
        "10;10;7=7 Days,10=10 Days (maximum)",
        "7;7;7=7 Days",
        "3;3;3=3 Days (maximum)",
        "1;1;1=1 Day (maximum)",
        // the numbers have no grouping
        "1000;30;7=7 Days,30=30 Days,60=60 Days,90=90 Days,1000=1000 Days (maximum)",
        "2147483647;30;7=7 Days,30=30 Days,60=60 Days,90=90 Days,2147483647=2147483647 Days (maximum)"
    })
    @Issue("JENKINS-27612")
    void dialogOffersOnlyWhatTheEnforcedMaximumAllows(int maximum, String expectedDefault, String expectedChoices) throws Exception {
        WebClient wc = createClientForTokenEndpoints("user");
        User user = User.getById("user", false);
        ApiTokenPropertyConfiguration.get().setMaxTokenLifetimeDays(maximum);

        HtmlPage page = tokenPage(wc, user);
        assertExpirationOptions(page, expectedDefault, expectedChoices + ",custom=Custom");
        assertNull(neverWarning(page));
        assertThat(page.asXml(), not(containsString(NEVER_WARNING)));
        assertNull(enforceFromNotice(page));
    }

    /** The maximum is enforced from the date, that day included, and when there is no date, a blank one or one that is not a date. */
    @Test
    @Issue("JENKINS-27612")
    void dialogIsLimitedFromTheEnforcementDateOnAndWithoutOne() throws Exception {
        WebClient wc = createClientForTokenEndpoints("user");
        User user = User.getById("user", false);
        ApiTokenPropertyConfiguration config = ApiTokenPropertyConfiguration.get();
        config.setMaxTokenLifetimeDays(10);

        // if midnight passes the date is in the past for the server, which enforces as well
        LocalDate today = LocalDate.now();
        for (String enforceFrom : new String[]{today.toString(), today.minusDays(1).toString(), "2000-01-01", "", "  ", null, "2026-13-01", "soon"}) {
            config.setEnforceFrom(enforceFrom);
            HtmlPage page = tokenPage(wc, user);
            assertExpirationOptions(page, "10", "7=7 Days,10=10 Days (maximum),custom=Custom");
            assertNull(enforceFromNotice(page), "no notice once the maximum is enforced: " + enforceFrom);
        }
    }

    /** With no maximum, which is the default, the dialog is what it always was. */
    @Test
    @Issue("JENKINS-27612")
    void dialogIsUnchangedWithoutMaximum() throws Exception {
        WebClient wc = createClientForTokenEndpoints("user");
        User user = User.getById("user", false);
        ApiTokenPropertyConfiguration config = ApiTokenPropertyConfiguration.get();
        assertEquals(0, config.getMaxTokenLifetimeDays());

        HtmlPage page = tokenPage(wc, user);
        assertExpirationOptions(page, "30", FULL_EXPIRATION_OPTIONS);
        assertEquals(NEVER_WARNING, neverWarning(page));
        assertNull(enforceFromNotice(page));

        // the date means nothing without a maximum: no restriction and nothing to announce
        config.setEnforceFrom(LocalDate.now().plusDays(10).toString());
        page = tokenPage(wc, user);
        assertExpirationOptions(page, "30", FULL_EXPIRATION_OPTIONS);
        assertNull(enforceFromNotice(page));
    }

    /**
     * Before the enforcement date the dialog offers everything, as without a maximum, and announces the maximum, to the owners
     * who are subject to it and to the ones who are exempt: it states a rule of the instance.
     */
    @Test
    @Issue("JENKINS-27612")
    void dialogIsUnchangedBeforeTheEnforcementDateAndAnnouncesTheMaximum() throws Exception {
        WebClient wc = createClientForTokenEndpoints("user");
        User user = User.getById("user", false);
        ApiTokenPropertyConfiguration config = ApiTokenPropertyConfiguration.get();
        config.setMaxTokenLifetimeDays(30);
        // far enough from today for midnight not to matter
        LocalDate enforceFrom = LocalDate.now().plusDays(10);
        config.setEnforceFrom(enforceFrom.toString());

        HtmlPage page = tokenPage(wc, user);
        assertExpirationOptions(page, "30", FULL_EXPIRATION_OPTIONS);
        assertEquals(NEVER_WARNING, neverWarning(page));
        assertEquals(enforceFromNoticeText(enforceFrom.toString(), "30 days"), enforceFromNotice(page));

        config.setExemptUserIds("user");
        page = tokenPage(wc, user);
        assertExpirationOptions(page, "30", FULL_EXPIRATION_OPTIONS);
        assertEquals(enforceFromNoticeText(enforceFrom.toString(), "30 days"), enforceFromNotice(page));

        // the notice gives the number of days of the maximum, in the singular when it is one
        config.setExemptUserIds("");
        config.setMaxTokenLifetimeDays(1);
        assertEquals(enforceFromNoticeText(enforceFrom.toString(), "1 day"), enforceFromNotice(tokenPage(wc, user)));
        config.setMaxTokenLifetimeDays(365);
        assertEquals(enforceFromNoticeText(enforceFrom.toString(), "365 days"), enforceFromNotice(tokenPage(wc, user)));

        // the number has no grouping, however large: "2147483647 days" and not "2,147,483,647 days"
        config.setMaxTokenLifetimeDays(1000);
        assertEquals(enforceFromNoticeText(enforceFrom.toString(), "1000 days"), enforceFromNotice(tokenPage(wc, user)));
        config.setMaxTokenLifetimeDays(Integer.MAX_VALUE);
        assertEquals(enforceFromNoticeText(enforceFrom.toString(), "2147483647 days"), enforceFromNotice(tokenPage(wc, user)));
    }

    /** An owner who is exempt is not limited by an enforced maximum: the dialog is the full one, and has nothing to announce. */
    @Test
    @Issue("JENKINS-27612")
    void dialogIsUnchangedForAnExemptOwner() throws Exception {
        WebClient wc = createClientForTokenEndpoints("user");
        User user = User.getById("user", false);
        ApiTokenPropertyConfiguration config = ApiTokenPropertyConfiguration.get();
        config.setMaxTokenLifetimeDays(10);
        config.setExemptUserIds("someone\n  user \n");

        HtmlPage page = tokenPage(wc, user);
        assertExpirationOptions(page, "30", FULL_EXPIRATION_OPTIONS);
        assertEquals(NEVER_WARNING, neverWarning(page));
        assertNull(enforceFromNotice(page));
    }

    /**
     * The owner of the page is judged, not the user who looks at it: an administrator who is subject to the maximum sees the
     * full dialog on the page of an exempt user, and one who is exempt sees the limited dialog on the page of a user who is not.
     */
    @Test
    @Issue("JENKINS-27612")
    void dialogIsThatOfTheOwnerOfThePageNotThatOfTheViewer() throws Exception {
        WebClient adminClient = createClientForTokenEndpoints("admin");
        WebClient aliceClient = createClientForTokenEndpoints("alice");
        WebClient serviceClient = createClientForTokenEndpoints("service");
        User admin = User.getById("admin", false);
        User alice = User.getById("alice", false);
        User service = User.getById("service", false);
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.ADMINISTER).everywhere().to("admin")
                .grant(Jenkins.READ).everywhere().toAuthenticated());
        ApiTokenPropertyConfiguration config = ApiTokenPropertyConfiguration.get();
        config.setMaxTokenLifetimeDays(30);
        String limited = "7=7 Days,30=30 Days,custom=Custom";

        // the administrator is subject to the maximum, the owner of the second page is exempt
        config.setExemptUserIds("service");
        assertExpirationOptions(tokenPage(adminClient, alice), "30", limited);
        assertExpirationOptions(tokenPage(adminClient, service), "30", FULL_EXPIRATION_OPTIONS);
        assertExpirationOptions(tokenPage(adminClient, admin), "30", limited);
        assertExpirationOptions(tokenPage(aliceClient, alice), "30", limited);
        assertExpirationOptions(tokenPage(serviceClient, service), "30", FULL_EXPIRATION_OPTIONS);

        // the administrator is exempt, the others are not
        config.setExemptUserIds("admin");
        assertExpirationOptions(tokenPage(adminClient, alice), "30", limited);
        assertExpirationOptions(tokenPage(adminClient, service), "30", limited);
        assertExpirationOptions(tokenPage(adminClient, admin), "30", FULL_EXPIRATION_OPTIONS);
        assertExpirationOptions(tokenPage(aliceClient, alice), "30", limited);
        assertExpirationOptions(tokenPage(serviceClient, service), "30", limited);
    }

    /** The heading and the labels of the choices of the dialog come from the properties of the page. */
    @Test
    @Issue("JENKINS-27612")
    void dialogHasTheLabelsOfThePropertiesFile() throws Exception {
        WebClient wc = createClientForTokenEndpoints("user");
        User user = User.getById("user", false);

        HtmlPage page = tokenPage(wc, user);
        List<String> labels = dialogTemplate(page).querySelectorAll(".jenkins-form-label").stream()
                .map(label -> label.getTextContent().strip())
                .toList();
        assertTrue(labels.contains("Expiration"), labels.toString());
        assertEquals(FULL_EXPIRATION_OPTIONS, expirationOptions(page));
    }

    /** The warning about tokens that never expire is in the dialog, hidden, only while the dialog offers such tokens. */
    @Test
    @Issue("JENKINS-27612")
    void neverWarningIsRenderedOnlyWhenTheMaximumDoesNotApply() throws Exception {
        WebClient wc = createClientForTokenEndpoints("user");
        User user = User.getById("user", false);
        ApiTokenPropertyConfiguration config = ApiTokenPropertyConfiguration.get();

        // no maximum
        HtmlElement warning = dialogTemplate(tokenPage(wc, user)).querySelector(".token-warning");
        assertNotNull(warning);
        assertThat(warning.getAttribute("class"), allOf(containsString("jenkins-hidden"), containsString("warning")));

        // the maximum is announced but not enforced
        config.setMaxTokenLifetimeDays(10);
        config.setEnforceFrom(LocalDate.now().plusDays(10).toString());
        assertNotNull(dialogTemplate(tokenPage(wc, user)).querySelector(".token-warning"));

        // the owner is exempt
        config.setEnforceFrom(null);
        config.setExemptUserIds("user");
        assertNotNull(dialogTemplate(tokenPage(wc, user)).querySelector(".token-warning"));

        // the maximum applies: nothing to warn about, and no element
        config.setExemptUserIds("");
        HtmlPage page = tokenPage(wc, user);
        assertNull(dialogTemplate(page).querySelector(".token-warning"));
        assertThat(expirationOptions(page), not(containsString("never")));
    }

    // ---- JENKINS-27612: the dates of the date picker of the custom choice, which the server computes ----

    /** The dates that the page gives to the date picker, as {@code min,max,preset}. */
    private static String datePicker(HtmlPage page) {
        HtmlElement input = dialogTemplate(page).getOneHtmlElementByAttribute("input", "name", "tokenExpiration");
        return input.getAttribute("data-min-date") + "," + input.getAttribute("data-max-date") + "," + input.getAttribute("data-preset-date");
    }

    private static String datePicker(LocalDate min, LocalDate max, LocalDate preset) {
        return min + "," + max + "," + preset;
    }

    /** What the picker offers when the maximum does not apply: from today to a year from today, starting at 30 days. */
    private static String unlimitedDatePicker(LocalDate today) {
        return datePicker(today, today.plusDays(365), today.plusDays(30));
    }

    /**
     * Opens the page of the owner and checks the dates of its date picker. They are those of the day on which the server
     * rendered the page: if midnight passes between the clocks of the test and of the server, either day is accepted.
     */
    private static void assertDatePicker(WebClient wc, User owner, Function<LocalDate, String> expected) throws Exception {
        LocalDate before = LocalDate.now();
        String actual = datePicker(tokenPage(wc, owner));
        LocalDate after = LocalDate.now();
        assertThat(actual, is(oneOf(expected.apply(before), expected.apply(after))));
    }

    /**
     * When the maximum does not apply to the owner the picker is what it always was: from today to a year from today, and
     * 30 days from today when the custom choice is selected. That is the case without a maximum, before the enforcement date
     * and for an exempt owner.
     */
    @Test
    @Issue("JENKINS-27612")
    void datePickerIsLimitedToAYearWhenTheMaximumDoesNotApply() throws Exception {
        WebClient wc = createClientForTokenEndpoints("user");
        User user = User.getById("user", false);
        ApiTokenPropertyConfiguration config = ApiTokenPropertyConfiguration.get();

        // no maximum
        assertDatePicker(wc, user, ApiTokenPropertyTest::unlimitedDatePicker);

        // a maximum that is announced and not enforced: neither the 10 days nor its date are in the picker
        config.setMaxTokenLifetimeDays(10);
        config.setEnforceFrom(LocalDate.now().plusDays(10).toString());
        assertDatePicker(wc, user, ApiTokenPropertyTest::unlimitedDatePicker);

        // an exempt owner
        config.setEnforceFrom(null);
        config.setExemptUserIds("user");
        assertDatePicker(wc, user, ApiTokenPropertyTest::unlimitedDatePicker);

        // the maximum applies again
        config.setExemptUserIds("");
        assertDatePicker(wc, user, today -> datePicker(today, today.plusDays(10), today.plusDays(10)));
    }

    /**
     * When the maximum applies the picker ends on the last day that a token may expire, and starts at 30 days from today
     * or at that last day if it is sooner.
     */
    @ParameterizedTest
    @CsvSource(delimiter = ';', value = {
        "10;10",
        "45;30",
        "90;30",
        "30;30",
        "31;30",
        "29;29",
        "7;7",
        "1;1",
        "365;30",
        "366;30",
        "1000;30"
    })
    @Issue("JENKINS-27612")
    void datePickerIsLimitedToTheEnforcedMaximum(int maximum, int presetDays) throws Exception {
        WebClient wc = createClientForTokenEndpoints("user");
        User user = User.getById("user", false);
        ApiTokenPropertyConfiguration config = ApiTokenPropertyConfiguration.get();
        config.setMaxTokenLifetimeDays(maximum);

        assertDatePicker(wc, user, today -> datePicker(today, today.plusDays(maximum), today.plusDays(presetDays)));

        // from the enforcement date on, that day included
        config.setEnforceFrom(LocalDate.now().toString());
        assertDatePicker(wc, user, today -> datePicker(today, today.plusDays(maximum), today.plusDays(presetDays)));
    }

    /**
     * A date input does not take a year above 9999, so a maximum that goes beyond that ends the picker on the last valid date,
     * and what the user sees is not an invalid date. The picker is not cut before that.
     */
    @Test
    @Issue("JENKINS-27612")
    void datePickerOfAVeryLargeMaximumEndsOnTheLastValidDate() throws Exception {
        WebClient wc = createClientForTokenEndpoints("user");
        User user = User.getById("user", false);
        ApiTokenPropertyConfiguration config = ApiTokenPropertyConfiguration.get();
        LocalDate lastDate = LocalDate.of(9999, 12, 31);
        int daysToLastDate = (int) ChronoUnit.DAYS.between(LocalDate.now(), lastDate);

        for (int maximum : new int[]{daysToLastDate - 1, daysToLastDate, daysToLastDate + 1, 3_000_000}) {
            config.setMaxTokenLifetimeDays(maximum);
            assertDatePicker(wc, user, today -> {
                LocalDate expiration = today.plusDays(maximum);
                return datePicker(today, expiration.isAfter(lastDate) ? lastDate : expiration, today.plusDays(30));
            });
        }

        // the largest maximum, with the date written out instead of computed
        config.setMaxTokenLifetimeDays(Integer.MAX_VALUE);
        assertDatePicker(wc, user, today -> datePicker(today, LocalDate.of(9999, 12, 31), today.plusDays(30)));
    }

    /**
     * The picker follows the owner of the page, not the user who looks at it, as the options do.
     */
    @Test
    @Issue("JENKINS-27612")
    void datePickerIsThatOfTheOwnerOfThePageNotThatOfTheViewer() throws Exception {
        WebClient adminClient = createClientForTokenEndpoints("admin");
        User alice = User.getById("alice", true);
        User service = User.getById("service", true);
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.ADMINISTER).everywhere().to("admin")
                .grant(Jenkins.READ).everywhere().toAuthenticated());
        ApiTokenPropertyConfiguration config = ApiTokenPropertyConfiguration.get();
        config.setMaxTokenLifetimeDays(20);
        config.setExemptUserIds("service");

        assertDatePicker(adminClient, alice, today -> datePicker(today, today.plusDays(20), today.plusDays(20)));
        assertDatePicker(adminClient, service, ApiTokenPropertyTest::unlimitedDatePicker);
    }

    /**
     * The legacy token of a user can be regenerated with the button of the legacy section as long as the maximum does not apply
     * to the owner: without a maximum, before the enforcement date and for an exempt owner. The server rejects the request
     * otherwise, so there is no button.
     */
    @Test
    @Issue("JENKINS-27612")
    void changeTokenButtonIsNotRenderedForAnOwnerWhoIsSubjectToTheEnforcedMaximum() throws Exception {
        ApiTokenPropertyConfiguration config = ApiTokenPropertyConfiguration.get();
        config.setTokenGenerationOnCreationEnabled(true);
        WebClient wc = createClientForTokenEndpoints("user");
        User user = User.getById("user", false);
        assertTrue(user.getProperty(ApiTokenProperty.class).hasLegacyToken());

        // no maximum
        HtmlPage page = tokenPage(wc, user);
        assertTrue(hasChangeTokenButton(page));
        assertEquals(user.getProperty(ApiTokenProperty.class).getApiToken(), page.getFormByName("config").getInputByName("_.apiToken").getValue());

        // a maximum that is not enforced yet
        config.setMaxTokenLifetimeDays(30);
        config.setEnforceFrom(LocalDate.now().plusDays(10).toString());
        assertTrue(hasChangeTokenButton(tokenPage(wc, user)));

        // an exempt owner
        config.setEnforceFrom(null);
        config.setExemptUserIds("user");
        assertTrue(hasChangeTokenButton(tokenPage(wc, user)));

        // the maximum is enforced: the legacy token is still shown, but it cannot be regenerated
        config.setExemptUserIds("someone else");
        for (String enforceFrom : new String[]{null, "", LocalDate.now().toString(), "2000-01-01", "2026-13-01"}) {
            config.setEnforceFrom(enforceFrom);
            page = tokenPage(wc, user);
            assertFalse(hasChangeTokenButton(page), "no button once the maximum is enforced: " + enforceFrom);
            assertEquals(user.getProperty(ApiTokenProperty.class).getApiToken(), page.getFormByName("config").getInputByName("_.apiToken").getValue());
        }

        // and without a maximum again
        config.setMaxTokenLifetimeDays(0);
        assertTrue(hasChangeTokenButton(tokenPage(wc, user)));
    }

    /**
     * When users can create a legacy token themselves and the owner has none, the legacy section says so, and an owner who is
     * subject to the enforced maximum has no button to create one: only the button is hidden, not the section.
     */
    @Test
    @Issue("JENKINS-27612")
    void legacySectionOfAnOwnerWithoutLegacyTokenHasNoButtonWhenTheMaximumApplies() throws Exception {
        ApiTokenPropertyConfiguration config = ApiTokenPropertyConfiguration.get();
        config.setCreationOfLegacyTokenEnabled(true);
        WebClient wc = createClientForTokenEndpoints("user");
        User user = User.getById("user", false);
        assertFalse(user.getProperty(ApiTokenProperty.class).hasLegacyToken());

        HtmlPage page = tokenPage(wc, user);
        assertTrue(hasChangeTokenButton(page));
        assertEquals(Messages.ApiTokenProperty_NoLegacyToken(), page.getFormByName("config").getInputByName("_.apiToken").getValue());

        config.setMaxTokenLifetimeDays(30);
        page = tokenPage(wc, user);
        assertFalse(hasChangeTokenButton(page));
        assertEquals(Messages.ApiTokenProperty_NoLegacyToken(), page.getFormByName("config").getInputByName("_.apiToken").getValue());

        config.setExemptUserIds("user");
        assertTrue(hasChangeTokenButton(tokenPage(wc, user)));
    }

    /** The button follows the owner of the page, not the user who looks at it, as the server does. */
    @Test
    @Issue("JENKINS-27612")
    void changeTokenButtonFollowsTheOwnerOfThePageNotTheViewer() throws Exception {
        ApiTokenPropertyConfiguration config = ApiTokenPropertyConfiguration.get();
        config.setTokenGenerationOnCreationEnabled(true);
        WebClient adminClient = createClientForTokenEndpoints("admin");
        User admin = User.getById("admin", false);
        User alice = User.getById("alice", true);
        User service = User.getById("service", true);
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.ADMINISTER).everywhere().to("admin")
                .grant(Jenkins.READ).everywhere().toAuthenticated());
        config.setMaxTokenLifetimeDays(30);

        // the administrator is subject to the maximum, the owner of the second page is exempt
        config.setExemptUserIds("service");
        assertFalse(hasChangeTokenButton(tokenPage(adminClient, alice)));
        assertTrue(hasChangeTokenButton(tokenPage(adminClient, service)));
        assertFalse(hasChangeTokenButton(tokenPage(adminClient, admin)));

        // the administrator is exempt, the others are not
        config.setExemptUserIds("admin");
        assertFalse(hasChangeTokenButton(tokenPage(adminClient, alice)));
        assertFalse(hasChangeTokenButton(tokenPage(adminClient, service)));
        assertTrue(hasChangeTokenButton(tokenPage(adminClient, admin)));
    }

    // ---- JENKINS-27612: a secret that the owner holds already is not registered again while the maximum applies ----
    //
    // The maximum lifetime bounds token entries, not secrets: a client that registers a fixed token (addFixedToken) could register
    // the same secret again before each expiry, and every registration would give it a new expiration. So while the maximum applies
    // to the owner, the endpoint rejects a secret that any entry of the owner's store holds, expired or not. The scenario ids S1
    // to S18 are those of the design of the feature; S12 is tested with the store, in ApiTokenStoreReuseTest, and S16 (a random
    // token is never checked) is a review requirement.

    private static int tokenCount(User user) {
        return user.getProperty(ApiTokenProperty.class).getTokenStore().getTokenListSortedByName().size();
    }

    /** Registers a secret through {@code addFixedToken}, with the given expiration parameters, name and value pairs. */
    private JSONObject registerSecret(WebClient wc, String owner, String name, String token, String... expirationParameters) throws Exception {
        List<String> parameters = new ArrayList<>(List.of("newTokenName", name, "newTokenPlainValue", token));
        Collections.addAll(parameters, expirationParameters);
        return postToTokenEndpoint(wc, owner, ADD_FIXED_TOKEN, parameters.toArray(String[]::new));
    }

    /**
     * Checks that registering the secret is rejected because the owner holds it already, with a valid duration so that nothing
     * else can be the reason, and that nothing is stored or saved for the user.
     */
    private void assertSecretAlreadyRegistered(WebClient wc, User owner, String name, String token) throws Exception {
        assertRejected(wc, owner, ADD_FIXED_TOKEN, Messages.ApiTokenProperty_SecretAlreadyRegistered(),
                "newTokenName", name, "newTokenPlainValue", token, "expirationDuration", "30");
    }

    /**
     * S1 reuse-active-rejected. Given the maximum is enforced (90 days) and a user who is not exempt registered a secret through
     * {@code addFixedToken}, when the user registers the same secret again, with another name and a valid duration, then the
     * answer is HTTP 200 with {@code status:error} and the message about the secret, the store still holds exactly one entry for
     * it, which was not renewed, and {@code config.xml} is unchanged.
     */
    @Test
    @Issue("JENKINS-27612")
    void s1_reuseOfAnActiveSecretIsRejected() throws Exception {
        WebClient wc = createClientForTokenEndpoints("user");
        User user = User.getById("user", false);
        ApiTokenPropertyConfiguration.get().setMaxTokenLifetimeDays(90);
        JSONObject first = assertAccepted(registerSecret(wc, "user", "first", FIXED_TOKEN, "expirationDuration", "30"));
        String uuid = first.getString("tokenUuid");
        LocalDate expiration = findToken(user, uuid).getExpirationDate();

        assertEquals("This secret is already registered for this user. Generate a new secret instead of registering the same one again.",
                Messages.ApiTokenProperty_SecretAlreadyRegistered());
        // assertRejected compares the uuids of the store and the bytes of config.xml before and after
        assertSecretAlreadyRegistered(wc, user, "second", FIXED_TOKEN);

        assertEquals(1, tokenCount(user));
        assertEquals(expiration, findToken(user, uuid).getExpirationDate(), "the entry was not renewed");
        checkTokenIsWorking("user", FIXED_TOKEN);
    }

    /**
     * S2 reuse-expired-rejected. Given the maximum is enforced and the user holds an expired entry with the secret, which the
     * Java API accepts (only the endpoint rejects a date in the past), when the user registers the secret again, then it is
     * rejected as in S1, and the secret does not come back to life.
     */
    @Test
    @Issue("JENKINS-27612")
    void s2_reuseOfAnExpiredSecretIsRejected() throws Exception {
        WebClient wc = createClientForTokenEndpoints("user");
        User user = User.getById("user", false);
        ApiTokenPropertyConfiguration.get().setMaxTokenLifetimeDays(90);
        user.getProperty(ApiTokenProperty.class).addFixedNewToken("expired", FIXED_TOKEN, LocalDate.now().minusDays(1));
        checkTokenIsNotWorking("user", FIXED_TOKEN);

        assertSecretAlreadyRegistered(wc, user, "again", FIXED_TOKEN);

        assertEquals(1, tokenCount(user));
        checkTokenIsNotWorking("user", FIXED_TOKEN);
    }

    /**
     * S3 policy-off-accepted. Given there is no maximum, which is the default, and the user holds the secret, when the user
     * registers it again, then it is accepted: two entries, as it always was.
     */
    @Test
    @Issue("JENKINS-27612")
    void s3_reuseIsAcceptedWithoutMaximum() throws Exception {
        WebClient wc = createClientForTokenEndpoints("user");
        User user = User.getById("user", false);
        JSONObject first = assertAccepted(registerSecret(wc, "user", "first", FIXED_TOKEN, "expirationDuration", "30"));

        JSONObject second = assertAccepted(registerSecret(wc, "user", "second", FIXED_TOKEN, "expirationDuration", "30"));

        assertNotEquals(first.getString("tokenUuid"), second.getString("tokenUuid"));
        assertEquals(2, tokenCount(user));
        checkTokenIsWorking("user", FIXED_TOKEN);
    }

    /**
     * S4 warning-phase-accepted. Given a maximum that is not enforced yet, because the enforcement date is in the future, and the
     * user holds the secret, when the user registers it again, then it is accepted.
     */
    @Test
    @Issue("JENKINS-27612")
    void s4_reuseIsAcceptedInTheWarningPhase() throws Exception {
        WebClient wc = createClientForTokenEndpoints("user");
        User user = User.getById("user", false);
        ApiTokenPropertyConfiguration config = ApiTokenPropertyConfiguration.get();
        config.setMaxTokenLifetimeDays(30);
        // far enough from today for midnight not to matter
        config.setEnforceFrom(LocalDate.now().plusDays(10).toString());
        assertAccepted(registerSecret(wc, "user", "first", FIXED_TOKEN, "expirationDuration", "30"));

        assertAccepted(registerSecret(wc, "user", "second", FIXED_TOKEN, "expirationDuration", "30"));

        assertEquals(2, tokenCount(user));
    }

    /**
     * S5 exempt-owner-accepted. Given the maximum is enforced and the user is exempt and holds the secret, when the user
     * registers it again, then it is accepted. Another user, who is not exempt, is rejected.
     */
    @Test
    @Issue("JENKINS-27612")
    void s5_reuseIsAcceptedForAnExemptOwner() throws Exception {
        WebClient wc = createClientForTokenEndpoints("user");
        User user = User.getById("user", false);
        ApiTokenPropertyConfiguration config = ApiTokenPropertyConfiguration.get();
        config.setMaxTokenLifetimeDays(30);
        config.setExemptUserIds("user");
        assertAccepted(registerSecret(wc, "user", "first", FIXED_TOKEN, "expirationDuration", "never"));

        assertAccepted(registerSecret(wc, "user", "second", FIXED_TOKEN, "expirationDuration", "never"));

        assertEquals(2, tokenCount(user));

        // the exemption is the user's: when it goes away the secret is held, and registering it is rejected
        config.setExemptUserIds("someone");
        assertSecretAlreadyRegistered(wc, user, "third", FIXED_TOKEN);
        assertEquals(2, tokenCount(user));
    }

    /**
     * S6 other-user-accepted. Given the maximum is enforced and a user holds the secret, when another user registers the same
     * secret, then it is accepted, and the two credentials are independent: authentication finds the user by its name first, so
     * revoking the entry of one leaves the other working.
     */
    @Test
    @Issue("JENKINS-27612")
    void s6_theSameSecretIsAcceptedForAnotherUser() throws Exception {
        WebClient wc = createClientForTokenEndpoints("user");
        User user = User.getById("user", false);
        User other = User.getById("other", true);
        WebClient otherClient = j.createWebClient().withBasicCredentials("other", "other");
        otherClient.goToXml("whoAmI/api/xml");
        ApiTokenPropertyConfiguration.get().setMaxTokenLifetimeDays(90);
        JSONObject first = assertAccepted(registerSecret(wc, "user", "first", FIXED_TOKEN, "expirationDuration", "30"));

        assertAccepted(registerSecret(otherClient, "other", "first", FIXED_TOKEN, "expirationDuration", "30"));

        assertEquals(1, tokenCount(user));
        assertEquals(1, tokenCount(other));
        checkTokenIsWorking("user", FIXED_TOKEN);
        checkTokenIsWorking("other", FIXED_TOKEN);

        user.getProperty(ApiTokenProperty.class).revokeToken(first.getString("tokenUuid"));
        checkTokenIsNotWorking("user", FIXED_TOKEN);
        checkTokenIsWorking("other", FIXED_TOKEN);
    }

    /**
     * S7 case-insensitive. Given the maximum is enforced and the user holds the secret in lowercase, when the user submits it in
     * uppercase hexadecimal digits, then it is rejected: the endpoint lowercases the submitted digits before hashing them.
     */
    @Test
    @Issue("JENKINS-27612")
    void s7_theLetterCaseOfTheSecretDoesNotMatter() throws Exception {
        WebClient wc = createClientForTokenEndpoints("user");
        User user = User.getById("user", false);
        ApiTokenPropertyConfiguration.get().setMaxTokenLifetimeDays(90);
        assertAccepted(registerSecret(wc, "user", "first", FIXED_TOKEN, "expirationDuration", "30"));
        String secret = FIXED_TOKEN.substring(2);
        assertNotEquals(secret, secret.toUpperCase(Locale.ROOT));

        assertSecretAlreadyRegistered(wc, user, "upper", "11" + secret.toUpperCase(Locale.ROOT));
        assertSecretAlreadyRegistered(wc, user, "mixed", "11" + secret.substring(0, 16).toUpperCase(Locale.ROOT) + secret.substring(16));

        assertEquals(1, tokenCount(user));
    }

    /**
     * S8 legacy-as-fixed-rejected. Given the maximum is enforced and the user has a legacy token with the value W, when the user
     * registers {@code 11}+W, then it is rejected: the hash of a legacy token is the hash of its value, as the hash of a fixed
     * token is the hash of its 32 hexadecimal digits, so both are the same secret. The test first shows that they are: with no
     * maximum {@code 11}+W is accepted, and after that entry is revoked, {@code 11}+W still authenticates, through the legacy entry.
     */
    @Test
    @Issue("JENKINS-27612")
    void s8_theValueOfTheLegacyTokenRegisteredAsAFixedTokenIsRejected() throws Exception {
        WebClient wc = createClientForTokenEndpoints("user");
        User user = User.getById("user", false);
        ApiTokenProperty property = user.getProperty(ApiTokenProperty.class);
        property.changeApiToken();
        assertTrue(property.hasLegacyToken());
        String legacyValue = property.getApiTokenInsecure();
        String fixedToken = "11" + legacyValue;
        assertEquals(34, fixedToken.length());
        assertEquals(1, tokenCount(user));

        JSONObject registered = assertAccepted(registerSecret(wc, "user", "fixed", fixedToken));
        assertEquals(2, tokenCount(user));
        property.revokeToken(registered.getString("tokenUuid"));
        assertEquals(1, tokenCount(user));
        assertTrue(property.hasLegacyToken());
        checkTokenIsWorking("user", fixedToken);
        checkTokenIsWorking("user", legacyValue);

        ApiTokenPropertyConfiguration.get().setMaxTokenLifetimeDays(90);
        assertSecretAlreadyRegistered(wc, user, "fixed again", fixedToken);

        assertEquals(1, tokenCount(user));
    }

    /**
     * S9 revoke-then-readd-accepted. Given the maximum is enforced and the user revoked the only entry that holds the secret,
     * when the user registers the secret again, then it is accepted.
     * <p>
     * This documents a known limitation, the residual that the help of the maximum lifetime states: the store keeps no history
     * of revoked secrets, so a secret can be kept alive by revoking it and registering it again before each expiry. The test
     * captures that limit; it is not a behavior to rely on.
     */
    @Test
    @Issue("JENKINS-27612")
    void s9_revokeThenRegisterAgainIsAccepted() throws Exception {
        WebClient wc = createClientForTokenEndpoints("user");
        User user = User.getById("user", false);
        ApiTokenPropertyConfiguration.get().setMaxTokenLifetimeDays(90);
        JSONObject first = assertAccepted(registerSecret(wc, "user", "first", FIXED_TOKEN, "expirationDuration", "30"));
        assertSecretAlreadyRegistered(wc, user, "again", FIXED_TOKEN);

        user.getProperty(ApiTokenProperty.class).revokeToken(first.getString("tokenUuid"));
        assertEquals(0, tokenCount(user));
        checkTokenIsNotWorking("user", FIXED_TOKEN);
        JSONObject second = assertAccepted(registerSecret(wc, "user", "again", FIXED_TOKEN, "expirationDuration", "30"));

        assertNotEquals(first.getString("tokenUuid"), second.getString("tokenUuid"));
        assertEquals(1, tokenCount(user));
        checkTokenIsWorking("user", FIXED_TOKEN);
    }

    /** The content of every file in the directory of the user, by path relative to it. */
    private static Map<String, String> filesOf(User user) throws IOException {
        Path root = user.getRootDir().toPath();
        List<Path> files;
        try (Stream<Path> walk = Files.walk(root)) {
            files = walk.filter(Files::isRegularFile).toList();
        }
        Map<String, String> content = new TreeMap<>();
        for (Path file : files) {
            content.put(root.relativize(file).toString(), Files.readString(file, StandardCharsets.UTF_8));
        }
        return content;
    }

    /** The tokens of the user as the token page shows them, with their expiration and their usage statistics. */
    private static List<String> tokensAndStatistics(User user) {
        return user.getProperty(ApiTokenProperty.class).getTokenList().stream()
                .map(token -> token.uuid + "|" + token.name + "|" + token.expirationDate + "|" + token.useCounter + "|" + token.lastUseDate)
                .toList();
    }

    /**
     * S10 rejected-persists-nothing. Given the maximum is enforced and the user holds the secret, when the user registers it
     * again, then nothing changes: the files of the user (the bytes of {@code config.xml} and of the usage statistics, not
     * their modification time), the tokens and their statistics are the same, so the user was not even saved.
     * <p>
     * The user authenticates and the snapshot is taken afterwards, because an authentication can write the files of the user.
     * No request between the snapshot and the comparison authenticates with a token of the same store.
     */
    @Test
    @Issue("JENKINS-27612")
    void s10_aRejectedReuseChangesNothing() throws Exception {
        WebClient wc = createClientForTokenEndpoints("user");
        User user = User.getById("user", false);
        ApiTokenPropertyConfiguration.get().setMaxTokenLifetimeDays(90);
        assertAccepted(registerSecret(wc, "user", "first", FIXED_TOKEN, "expirationDuration", "30"));
        checkTokenIsWorking("user", FIXED_TOKEN);
        // the requests of the user that follow the use of its token write nothing more
        wc.goToXml("whoAmI/api/xml");
        wc.goToXml("whoAmI/api/xml");
        Map<String, String> filesBefore = filesOf(user);
        List<String> tokensBefore = tokensAndStatistics(user);
        assertTrue(filesBefore.containsKey("config.xml"));
        assertEquals(1, tokenCount(user));

        JSONObject response = registerSecret(wc, "user", "second", FIXED_TOKEN, "expirationDuration", "30");

        assertEquals("error", response.getString("status"), response.toString());
        assertEquals(Messages.ApiTokenProperty_SecretAlreadyRegistered(), response.getString("message"));
        assertEquals(filesBefore, filesOf(user));
        assertEquals(tokensBefore, tokensAndStatistics(user));
        assertEquals(1, tokenCount(user));
    }

    /**
     * S18 over-max-and-known. Given the maximum is enforced and the user holds the secret, when the user registers it again with a
     * duration over the maximum, or with {@code never}, then it is rejected with the message about the maximum: the policy is
     * checked before the store looks for the secret.
     */
    @Test
    @Issue("JENKINS-27612")
    void s18_aRequestOverTheMaximumGetsTheMessageAboutTheMaximumEvenIfTheSecretIsKnown() throws Exception {
        WebClient wc = createClientForTokenEndpoints("user");
        User user = User.getById("user", false);
        ApiTokenPropertyConfiguration.get().setMaxTokenLifetimeDays(90);
        assertAccepted(registerSecret(wc, "user", "first", FIXED_TOKEN, "expirationDuration", "30"));

        assertRejected(wc, user, ADD_FIXED_TOKEN, Messages.ApiTokenProperty_expirationOverMaximum(90),
                "newTokenName", "over", "newTokenPlainValue", FIXED_TOKEN, "expirationDuration", "91");
        assertRejected(wc, user, ADD_FIXED_TOKEN, Messages.ApiTokenProperty_expirationNeverNotAllowed(),
                "newTokenName", "never", "newTokenPlainValue", FIXED_TOKEN, "expirationDuration", "never");
        // once the request respects the maximum, the secret is what is wrong with it
        assertRejected(wc, user, ADD_FIXED_TOKEN, Messages.ApiTokenProperty_SecretAlreadyRegistered(),
                "newTokenName", "last", "newTokenPlainValue", FIXED_TOKEN, "expirationDuration", "90");

        assertEquals(1, tokenCount(user));
    }

    // ---- JENKINS-27612: revoking the tokens that share a secret and shortening an expiration, the data layer of the compliance panel ----

    private static final String OTHER_FIXED_TOKEN = "11fedcba9876543210fedcba9876543210";

    /** Counts the writes of the configuration of a user, which is what {@link User#save()} does. */
    @TestExtension
    public static class UserSaveCounter extends SaveableListener {
        static final AtomicInteger COUNT = new AtomicInteger();

        @Override
        public void onChange(Saveable o, XmlFile file) {
            if (o instanceof User) {
                COUNT.incrementAndGet();
            }
        }
    }

    private static List<String> storedTokenUuids(User user) {
        return user.getProperty(ApiTokenProperty.class).getTokenStore().getTokenListSnapshot().stream()
                .map(ApiTokenStore.HashedToken::getUuid)
                .toList();
    }

    /**
     * Revoking a token whose secret is shared removes every live token that holds it, and what belongs to each of them: its usage
     * statistics, which are per token, are removed for every one that was revoked and kept for the others, and the user is saved
     * once for all of them, not once each. The token that has expired and holds the secret is kept.
     */
    @Test
    @Issue("JENKINS-27612")
    void revokeTokensRemovesTheLiveTokensOfTheSecretWithTheirStatisticsAndSavesTheUserOnce() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        User user = User.getById("user", true);
        ApiTokenProperty property = user.getProperty(ApiTokenProperty.class);
        LocalDate today = LocalDate.now();
        String live1 = property.addFixedNewToken("live 1", FIXED_TOKEN, null);
        String live2 = property.addFixedNewToken("live 2", FIXED_TOKEN, today.plusDays(30));
        String expired = property.addFixedNewToken("expired", FIXED_TOKEN, today.minusDays(1));
        String other = property.addFixedNewToken("other", OTHER_FIXED_TOKEN, null);
        for (String uuid : List.of(live1, live2, expired, other)) {
            property.getTokenStats().updateUsageForId(uuid);
            assertEquals(1, property.getTokenStats().findTokenStatsById(uuid).getUseCounter(), "the statistics of every token are recorded");
        }
        UserSaveCounter.COUNT.set(0);

        List<String> revoked = property.revokeTokens(live2, today);

        assertEquals(List.of(live1, live2), revoked, "whichever of the live tokens of the secret is the target");
        assertEquals(1, UserSaveCounter.COUNT.get(), "the user is saved once for the whole group");
        ApiTokenStats persistedStats = ApiTokenStats.load(user);
        for (ApiTokenStats stats : List.of(property.getTokenStats(), persistedStats)) {
            assertEquals(0, stats.findTokenStatsById(live1).getUseCounter());
            assertEquals(0, stats.findTokenStatsById(live2).getUseCounter());
            assertEquals(1, stats.findTokenStatsById(expired).getUseCounter());
            assertEquals(1, stats.findTokenStatsById(other).getUseCounter());
        }
        assertEquals(List.of(expired, other), storedTokenUuids(user));
        checkTokenIsNotWorking(user.getId(), FIXED_TOKEN);
        checkTokenIsWorking(user.getId(), OTHER_FIXED_TOKEN);

        // what is persisted is what is left
        user.load();
        assertEquals(List.of(expired, other), storedTokenUuids(user));
    }

    /** A token whose secret is not shared is revoked alone, with its statistics, and the user is saved once. */
    @Test
    @Issue("JENKINS-27612")
    void revokeTokensOfATokenThatIsAloneRevokesOnlyThatToken() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        User user = User.getById("user", true);
        ApiTokenProperty property = user.getProperty(ApiTokenProperty.class);
        String alone = property.addFixedNewToken("alone", FIXED_TOKEN, null);
        String other = property.addFixedNewToken("other", OTHER_FIXED_TOKEN, null);
        property.getTokenStats().updateUsageForId(alone);
        property.getTokenStats().updateUsageForId(other);
        UserSaveCounter.COUNT.set(0);

        assertEquals(List.of(alone), property.revokeTokens(alone, LocalDate.now()));

        assertEquals(1, UserSaveCounter.COUNT.get());
        assertEquals(0, property.getTokenStats().findTokenStatsById(alone).getUseCounter());
        assertEquals(1, property.getTokenStats().findTokenStatsById(other).getUseCounter());
        assertEquals(List.of(other), storedTokenUuids(user));
        checkTokenIsNotWorking(user.getId(), FIXED_TOKEN);
        checkTokenIsWorking(user.getId(), OTHER_FIXED_TOKEN);
    }

    /** A token that has expired is revoked when it is the target, with the live ones that have its secret. */
    @Test
    @Issue("JENKINS-27612")
    void revokeTokensOfAnExpiredTokenAlsoRevokesTheLiveTokensOfItsSecret() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        User user = User.getById("user", true);
        ApiTokenProperty property = user.getProperty(ApiTokenProperty.class);
        LocalDate today = LocalDate.now();
        String expired = property.addFixedNewToken("expired", FIXED_TOKEN, today.minusDays(1));
        String live = property.addFixedNewToken("live", FIXED_TOKEN, null);
        String alsoExpired = property.addFixedNewToken("also expired", FIXED_TOKEN, today.minusDays(9));

        assertEquals(List.of(expired, live), property.revokeTokens(expired, today));

        assertEquals(List.of(alsoExpired), storedTokenUuids(user));
        checkTokenIsNotWorking(user.getId(), FIXED_TOKEN);
    }

    @Test
    @Issue("JENKINS-27612")
    void revokeTokensOfAnUnknownUuidChangesNothingAndDoesNotSaveTheUser() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        User user = User.getById("user", true);
        ApiTokenProperty property = user.getProperty(ApiTokenProperty.class);
        String token = property.addFixedNewToken("token", FIXED_TOKEN, null);
        property.getTokenStats().updateUsageForId(token);
        UserSaveCounter.COUNT.set(0);

        assertEquals(List.of(), property.revokeTokens("no such uuid", LocalDate.now()));

        assertEquals(0, UserSaveCounter.COUNT.get());
        assertEquals(List.of(token), storedTokenUuids(user));
        assertEquals(1, property.getTokenStats().findTokenStatsById(token).getUseCounter());
    }

    /**
     * S20, data part. Given the owner has a legacy token with the value W and a fixed token {@code 11}+W, which are one secret, when
     * the group is revoked, with either of them as the target, and then the user is loaded again, as at a restart, then the
     * legacy token is not back, and neither W nor {@code 11}+W authenticates. The legacy token is created again at load from the
     * secret that the property keeps, so revoking its entry is not enough: the secret has to be dropped too.
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @Issue("JENKINS-27612")
    void s20_revokingAGroupThatHasTheLegacyTokenDoesNotBringItBackWhenTheUserIsLoaded(boolean targetIsTheLegacyToken) throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        User user = User.getById("user", true);
        ApiTokenProperty property = user.getProperty(ApiTokenProperty.class);
        property.changeApiToken();
        String w = property.getApiTokenInsecure();
        String legacyUuid = property.getTokenStore().getLegacyToken().getUuid();
        String fixedUuid = property.addFixedNewToken("fixed", "11" + w, null);
        assertEquals(Set.of(legacyUuid, fixedUuid), property.getTokenStore().getUuidsSharingSecret().keySet(), "they are a group");
        checkTokenIsWorking(user.getId(), w);
        checkTokenIsWorking(user.getId(), "11" + w);

        List<String> revoked = property.revokeTokens(targetIsTheLegacyToken ? legacyUuid : fixedUuid, LocalDate.now());

        assertEquals(List.of(legacyUuid, fixedUuid), revoked);
        // the in-memory state, which the legacy token monitor reads, before any reload
        assertFalse(property.hasLegacyToken());
        checkTokenIsNotWorking(user.getId(), w);
        checkTokenIsNotWorking(user.getId(), "11" + w);

        // what happens at the next start: the user is loaded from its config.xml
        user.load();
        ApiTokenProperty reloaded = user.getProperty(ApiTokenProperty.class);
        assertFalse(reloaded.hasLegacyToken());
        assertThat(reloaded.getTokenStore().getTokenListSnapshot(), empty());
        checkTokenIsNotWorking(user.getId(), w);
        checkTokenIsNotWorking(user.getId(), "11" + w);
    }

    /**
     * S17, data part. Given the owner has a legacy token with the value W and a fixed token {@code 11}+W, when an administrator
     * revokes the legacy token in the legacy token monitor, then {@code 11}+W still authenticates, also after the user is loaded again,
     * and the entry that is left is no longer a group: the monitor, like the token page, removes one entry.
     */
    @Test
    @Issue("JENKINS-27612")
    void s17_revokingOnlyTheLegacyTokenInTheLegacyTokenMonitorLeavesTheFixedTokenWorking() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        User user = User.getById("user", true);
        ApiTokenProperty property = user.getProperty(ApiTokenProperty.class);
        property.changeApiToken();
        String w = property.getApiTokenInsecure();
        String legacyUuid = property.getTokenStore().getLegacyToken().getUuid();
        String fixedUuid = property.addFixedNewToken("fixed", "11" + w, null);
        assertEquals(Set.of(legacyUuid, fixedUuid), property.getTokenStore().getUuidsSharingSecret().keySet(), "a group before the revocation");

        LegacyApiTokenAdministrativeMonitor monitor = j.jenkins.getExtensionList(AdministrativeMonitor.class).get(LegacyApiTokenAdministrativeMonitor.class);
        LegacyApiTokenAdministrativeMonitor.RevokeAllSelectedUserAndUuid selected = new LegacyApiTokenAdministrativeMonitor.RevokeAllSelectedUserAndUuid();
        selected.userId = user.getId();
        selected.uuid = legacyUuid;
        LegacyApiTokenAdministrativeMonitor.RevokeAllSelectedModel model = new LegacyApiTokenAdministrativeMonitor.RevokeAllSelectedModel();
        model.values = new LegacyApiTokenAdministrativeMonitor.RevokeAllSelectedUserAndUuid[] {selected};
        monitor.doRevokeAllSelected(model);

        assertEquals(List.of(fixedUuid), storedTokenUuids(user));
        assertThat(property.getTokenStore().getUuidsSharingSecret().keySet(), empty());
        checkTokenIsWorking(user.getId(), "11" + w);
        user.load();
        assertEquals(List.of(fixedUuid), storedTokenUuids(user));
        assertFalse(user.getProperty(ApiTokenProperty.class).hasLegacyToken());
        checkTokenIsWorking(user.getId(), "11" + w);
    }

    /**
     * S14, data part with the endpoint. Given the maximum is enforced and the owner holds a secret in three tokens created through
     * the Java API, two live and one expired, when the group of a live one is revoked, then the live ones are gone, the secret no
     * longer authenticates, and registering it again is still rejected, as long as the expired token is there.
     */
    @Test
    @Issue("JENKINS-27612")
    void s14_afterRevokingTheLiveTokensTheExpiredOneStillBlocksRegisteringTheSecretAgain() throws Exception {
        WebClient wc = createClientForTokenEndpoints("user");
        User user = User.getById("user", false);
        ApiTokenProperty property = user.getProperty(ApiTokenProperty.class);
        ApiTokenPropertyConfiguration.get().setMaxTokenLifetimeDays(90);
        LocalDate today = LocalDate.now();
        String live1 = property.addFixedNewToken("live 1", FIXED_TOKEN, null);
        property.addFixedNewToken("live 2", FIXED_TOKEN, today.plusDays(30));
        String expired = property.addFixedNewToken("expired", FIXED_TOKEN, today.minusDays(1));
        checkTokenIsWorking("user", FIXED_TOKEN);

        assertEquals(2, property.revokeTokens(live1, today).size());

        assertEquals(List.of(expired), storedTokenUuids(user));
        checkTokenIsNotWorking("user", FIXED_TOKEN);
        assertSecretAlreadyRegistered(wc, user, "again", FIXED_TOKEN);
        assertEquals(1, tokenCount(user));
    }

    @Test
    @Issue("JENKINS-27612")
    void shortenExpirationSavesTheUserOnlyWhenItShortenedTheExpiration() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        User user = User.getById("user", true);
        ApiTokenProperty property = user.getProperty(ApiTokenProperty.class);
        LocalDate today = LocalDate.now();
        String bounded = property.addFixedNewToken("bounded", FIXED_TOKEN, today.plusDays(100));
        String never = property.addFixedNewToken("never", OTHER_FIXED_TOKEN, null);
        UserSaveCounter.COUNT.set(0);

        assertFalse(property.shortenExpiration(bounded, today.plusDays(100)), "the same date");
        assertFalse(property.shortenExpiration(bounded, today.plusDays(200)), "a later date");
        assertFalse(property.shortenExpiration("no such uuid", today.plusDays(1)));
        assertEquals(0, UserSaveCounter.COUNT.get(), "nothing changed, nothing was saved");
        assertEquals(today.plusDays(100), findToken(user, bounded).getExpirationDate());

        assertTrue(property.shortenExpiration(bounded, today.plusDays(30)));
        assertEquals(1, UserSaveCounter.COUNT.get());
        assertTrue(property.shortenExpiration(never, today.plusDays(60)));
        assertEquals(2, UserSaveCounter.COUNT.get());

        // what is persisted is what was shortened
        user.load();
        assertEquals(today.plusDays(30), findToken(user, bounded).getExpirationDate());
        assertEquals(today.plusDays(60), findToken(user, never).getExpirationDate());
    }

    @Test
    @Issue("JENKINS-27612")
    void shortenExpirationDoesNotChangeOrSaveTheLegacyToken() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        User user = User.getById("user", true);
        ApiTokenProperty property = user.getProperty(ApiTokenProperty.class);
        property.changeApiToken();
        String legacyUuid = property.getTokenStore().getLegacyToken().getUuid();
        String w = property.getApiTokenInsecure();
        UserSaveCounter.COUNT.set(0);

        assertFalse(property.shortenExpiration(legacyUuid, LocalDate.now().plusDays(30)));
        assertFalse(property.shortenExpiration(legacyUuid, LocalDate.now().minusDays(1)));

        assertEquals(0, UserSaveCounter.COUNT.get());
        assertNull(findToken(user, legacyUuid).getExpirationDate());
        checkTokenIsWorking(user.getId(), w);
    }

    /**
     * A legacy token that was migrated has no creation date and a token that was never used has no last use date: the accessors that
     * turn them into text, which the panel uses for every row, answer an empty text and do not throw. With dates, they answer
     * what they always did.
     */
    @Test
    @Issue("JENKINS-27612")
    void theDaysAgoAccessorsAreEmptyForADateThatIsNotKnownAndUnchangedOtherwise() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        ApiTokenPropertyConfiguration.get().setTokenGenerationOnCreationEnabled(true);
        User user = User.getById("user", true);
        ApiTokenProperty property = user.getProperty(ApiTokenProperty.class);
        String freshUuid = property.generateNewToken("fresh").tokenUuid;

        ApiTokenProperty.TokenInfoAndStats legacy = tokenInfo(property, true);
        ApiTokenProperty.TokenInfoAndStats fresh = tokenInfo(property, false);
        assertNull(legacy.creationDate, "a legacy token that was already there has no creation date");
        assertNull(legacy.lastUseDate);
        assertNull(fresh.lastUseDate);
        assertEquals("", legacy.createdDaysAgo());
        assertEquals("", legacy.lastUsedDaysAgo());
        assertEquals("", fresh.lastUsedDaysAgo());
        assertEquals(Messages.ApiTokenProperty_createdToday(), fresh.createdDaysAgo());

        property.getTokenStats().updateUsageForId(freshUuid);
        ApiTokenProperty.TokenInfoAndStats used = tokenInfo(property, false);
        assertNotNull(used.lastUseDate);
        assertEquals(Messages.ApiTokenProperty_lastUsedToday(), used.lastUsedDaysAgo());
    }

    private static ApiTokenProperty.TokenInfoAndStats tokenInfo(ApiTokenProperty property, boolean legacy) {
        return property.getTokenList().stream()
                .filter(token -> token.isLegacy == legacy)
                .findFirst()
                .orElseThrow();
    }

    // test no token are generated for new user with the global configuration set to false
}
