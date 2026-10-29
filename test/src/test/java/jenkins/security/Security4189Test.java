package jenkins.security;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import hudson.security.FullControlOnceLoggedInAuthorizationStrategy;
import hudson.security.HudsonPrivateSecurityRealm;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import net.sf.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class Security4189Test {

    private JenkinsRule j;
    private HttpClient client;
    private String base;

    private record Crumb(String field, String value) {}

    private record IssuedToken(String value, String uuid) {}

    private interface AfterCookieIssued {
        void run(String rememberMeCookie) throws Exception;
    }

    @BeforeEach
    void setUp(JenkinsRule rule) throws Exception {
        j = rule;
        client = HttpClient.newHttpClient();
        base = j.getURL().toString();
        createSecuredRealmAndUser();
    }

    @Issue("SECURITY-4189")
    @Test
    void tokenBasicAuthMustNotIssueRememberMeCookie() throws Exception {
        var basicAlicePassword = basicAuthHeader("alice", "alice");
        var crumb = fetchCrumb(basicAlicePassword);
        var token = createToken(basicAlicePassword, crumb);
        var basicAliceToken = basicAuthHeader("alice", token.value());

        assertBasicAuthDoesNotPersistRememberMeCookie(basicAliceToken, rememberMeCookie -> {
            revokeToken(basicAlicePassword, crumb, token.uuid());

            var revokedTokenResponse = get("whoAmI/api/json", "Authorization", basicAliceToken);
            var cookieOnlyResponse = get("whoAmI/api/json", "Cookie", "remember-me=" + rememberMeCookie);
            assertEquals(401, revokedTokenResponse.statusCode(), "the revoked token itself must stop authenticating");
            assertEquals(403, cookieOnlyResponse.statusCode(), "a remember-me cookie should not continue to authenticate");
        });
    }

    @Issue("SECURITY-4189")
    @Test
    void passwordBasicAuthMustNotIssueRememberMeCookie() throws Exception {
        var basicAlicePassword = basicAuthHeader("alice", "alice");

        assertBasicAuthDoesNotPersistRememberMeCookie(basicAlicePassword, rememberMeCookie -> { });
    }

    private void assertBasicAuthDoesNotPersistRememberMeCookie(String authorization, AfterCookieIssued afterCookieIssued) throws Exception {
        var attackResponse = get("whoAmI/api/json?remember_me=on", "Authorization", authorization);
        assertEquals(200, attackResponse.statusCode());
        var rememberMeCookie = cookieValue(attackResponse.headers().allValues("Set-Cookie"), "remember-me");

        if (rememberMeCookie.isPresent()) {
            afterCookieIssued.run(rememberMeCookie.get());
        }
        assertThat("a remember-me cookie should not be issued for this Basic auth credential", rememberMeCookie.isEmpty());
    }

    private void createSecuredRealmAndUser() throws Exception {
        var realm = new HudsonPrivateSecurityRealm(false, false, null);
        realm.createAccount("alice", "alice");
        j.jenkins.setSecurityRealm(realm);

        var authStrategy = new FullControlOnceLoggedInAuthorizationStrategy();
        authStrategy.setAllowAnonymousRead(false);
        j.jenkins.setAuthorizationStrategy(authStrategy);
    }

    private Crumb fetchCrumb(String authorization) throws Exception {
        var response = get("crumbIssuer/api/json", "Authorization", authorization);
        assertEquals(200, response.statusCode());
        var json = JSONObject.fromObject(response.body());
        return new Crumb(json.getString("crumbRequestField"), json.getString("crumb"));
    }

    private IssuedToken createToken(String authorization, Crumb crumb) throws Exception {
        var response = client.send(
                HttpRequest.newBuilder(URI.create(base + "user/alice/descriptorByName/jenkins.security.ApiTokenProperty/generateNewToken?newTokenName=black-box"))
                        .header("Authorization", authorization)
                        .header(crumb.field(), crumb.value())
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        var data = JSONObject.fromObject(response.body()).getJSONObject("data");
        return new IssuedToken(data.getString("tokenValue"), data.getString("tokenUuid"));
    }

    private void revokeToken(String authorization, Crumb crumb, String tokenUuid) throws Exception {
        var response = client.send(
                HttpRequest.newBuilder(URI.create(base + "user/alice/descriptorByName/jenkins.security.ApiTokenProperty/revoke?tokenUuid=" + tokenUuid))
                        .header("Authorization", authorization)
                        .header(crumb.field(), crumb.value())
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
    }

    private HttpResponse<String> get(String path, String headerName, String headerValue) throws Exception {
        return client.send(
                HttpRequest.newBuilder(URI.create(base + path))
                        .header(headerName, headerValue)
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static String basicAuthHeader(String username, String password) {
        var credentials = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    private static Optional<String> cookieValue(List<String> setCookieHeaders, String name) {
        var prefix = name + "=";
        return setCookieHeaders.stream()
                .filter(header -> header.startsWith(prefix))
                .findFirst()
                .map(header -> {
                    var rest = header.substring(prefix.length());
                    var semicolon = rest.indexOf(';');
                    return semicolon >= 0 ? rest.substring(0, semicolon) : rest;
                });
    }
}
