/*
 * The MIT License
 *
 * Copyright (c) 2026
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
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.User;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;
import jenkins.model.Jenkins;
import jenkins.security.ApiTokenProperty;
import org.htmlunit.WebRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class ScopedApiTokenIntegrationTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void unscopedTokenHasFullUserPermissions() throws Exception {
        configureAdmins();
        User admin = User.getById("admin", true);
        String token = generateUnscopedToken(admin);

        FreeStyleProject project = j.createFreeStyleProject("job1");

        // Read should work (admin has everything)
        assertEquals(HttpURLConnection.HTTP_OK, request("admin", token, "/job/" + project.getName() + "/api/xml"));
        // Build trigger should work
        assertEquals(HttpURLConnection.HTTP_CREATED, postRequest("admin", token, "/job/" + project.getName() + "/build"));
    }

    @Test
    void readScopedTokenAllowsReadOnly() throws Exception {
        configureAdmins();
        User admin = User.getById("admin", true);
        // Read-only scope needs both Jenkins.READ (top-level) and Item.READ (per-item);
        // these are siblings in the impliedBy hierarchy so both must be listed explicitly.
        String token = generateScopedToken(admin, Set.of(Jenkins.READ.getId(), Item.READ.getId()));

        FreeStyleProject project = j.createFreeStyleProject("job2");

        // Read should work
        assertEquals(HttpURLConnection.HTTP_OK, request("admin", token, "/job/" + project.getName() + "/api/xml"));
        // Build trigger should be rejected by the scope gate
        assertEquals(HttpURLConnection.HTTP_FORBIDDEN, postRequest("admin", token, "/job/" + project.getName() + "/build"));
    }

    @Test
    void administerScopedTokenAllowsEverythingViaImpliedBy() throws Exception {
        configureAdmins();
        User admin = User.getById("admin", true);
        String token = generateScopedToken(admin, Set.of(Jenkins.ADMINISTER.getId()));

        FreeStyleProject project = j.createFreeStyleProject("job3");

        assertEquals(HttpURLConnection.HTTP_OK, request("admin", token, "/job/" + project.getName() + "/api/xml"));
        assertEquals(HttpURLConnection.HTTP_CREATED, postRequest("admin", token, "/job/" + project.getName() + "/build"));
    }

    @Test
    void scopedTokenRejectsPermissionsTheUserNoLongerHolds() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.ADMINISTER).everywhere().to("user1"));
        User user1 = User.getById("user1", true);
        String token = generateScopedToken(user1, Set.of(Jenkins.READ.getId(), Item.READ.getId(), Item.BUILD.getId()));
        FreeStyleProject project = j.createFreeStyleProject("j1");

        assertEquals(HttpURLConnection.HTTP_CREATED, postRequest("user1", token, "/job/" + project.getName() + "/build"));

        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ, Item.READ).everywhere().to("user1"));
        assertEquals(HttpURLConnection.HTTP_FORBIDDEN, postRequest("user1", token, "/job/" + project.getName() + "/build"));
        assertEquals(HttpURLConnection.HTTP_OK, request("user1", token, "/job/" + project.getName() + "/api/xml"));
    }

    @Test
    void emptyScopeSetRejected() throws Exception {
        configureAdmins();
        User admin = User.getById("admin", true);
        assertThrows(IllegalArgumentException.class, () ->
                admin.getProperty(ApiTokenProperty.class).generateNewToken("bad", null, Set.of()));
    }

    @Test
    void unknownPermissionIdRejected() throws Exception {
        configureAdmins();
        User admin = User.getById("admin", true);
        assertThrows(IllegalArgumentException.class, () ->
                admin.getProperty(ApiTokenProperty.class).generateNewToken("bad", null, Set.of("not.a.real.Permission")));
    }

    @Test
    void scopeIsPersistedOnTokenInfo() throws Exception {
        configureAdmins();
        User admin = User.getById("admin", true);
        admin.getProperty(ApiTokenProperty.class).generateNewToken("scoped", null, Set.of(Item.READ.getId()));

        ApiTokenProperty.TokenInfoAndStats stored = admin.getProperty(ApiTokenProperty.class)
                .getTokenList().stream()
                .filter(t -> "scoped".equals(t.name))
                .findFirst()
                .orElse(null);

        assertNotNull(stored);
        assertTrue(stored.scoped);
        assertThat(stored.scopes, equalTo(Set.of(Item.READ.getId())));
    }

    private void configureAdmins() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.ADMINISTER).everywhere().to("admin"));
    }

    private String generateUnscopedToken(User user) throws Exception {
        return user.getProperty(ApiTokenProperty.class).generateNewToken("t", null).plainValue;
    }

    private String generateScopedToken(User user, Set<String> scopes) throws Exception {
        return user.getProperty(ApiTokenProperty.class).generateNewToken("t", null, scopes).plainValue;
    }

    private int request(String username, String token, String path) throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient();
        wc.getOptions().setThrowExceptionOnFailingStatusCode(false);
        WebRequest r = new WebRequest(new URI(j.jenkins.getRootUrl() + path.substring(1)).toURL());
        r.setAdditionalHeader("Authorization", basic(username, token));
        return wc.getPage(r).getWebResponse().getStatusCode();
    }

    private int postRequest(String username, String token, String path) throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient();
        wc.getOptions().setThrowExceptionOnFailingStatusCode(false);
        WebRequest r = new WebRequest(new URI(j.jenkins.getRootUrl() + path.substring(1)).toURL(),
                org.htmlunit.HttpMethod.POST);
        r.setAdditionalHeader("Authorization", basic(username, token));
        return wc.getPage(r).getWebResponse().getStatusCode();
    }

    private static String basic(String u, String p) {
        return "Basic " + Base64.getEncoder().encodeToString((u + ":" + p).getBytes(StandardCharsets.UTF_8));
    }
}
