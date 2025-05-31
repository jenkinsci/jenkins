package jenkins.security;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.xml.HasXPath.hasXPath;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Util;
import hudson.model.Cause;
import hudson.model.FreeStyleProject;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.ACLContext;
import jakarta.servlet.http.HttpServletResponse;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import jenkins.model.Jenkins;
import jenkins.security.apitoken.ApiTokenPropertyConfiguration;
import jenkins.security.apitoken.ApiTokenStore;
import jenkins.security.apitoken.TokenUuidAndPlainValue;
import net.sf.json.JSONObject;
import org.htmlunit.FailingHttpStatusCodeException;
import org.htmlunit.HttpMethod;
import org.htmlunit.Page;
import org.htmlunit.WebRequest;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.xml.XmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
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

    // test no token are generated for new user with the global configuration set to false
}
