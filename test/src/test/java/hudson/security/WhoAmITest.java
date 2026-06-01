/*
 * The MIT License
 *
 * Copyright (c) 2020, CloudBees, Inc.
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

package hudson.security;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import hudson.model.User;
import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import jenkins.model.Jenkins;
import jenkins.security.ApiTokenProperty;
import jenkins.security.apitoken.TokenUuidAndPlainValue;
import org.htmlunit.Page;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.Stapler;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@WithJenkins
class WhoAmITest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    @Issue("SECURITY-1695")
    void whoAmI_regular_doesNotProvideSensitiveInformation() throws Exception {
        j.jenkins.setSecurityRealm(new SecurityRealmImpl());

        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ).everywhere().to("user")
        );

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.login("user");

        HtmlPage whoAmIPage = wc.goTo("whoAmI");
        String content = whoAmIPage.getWebResponse().getContentAsString();

        String sessionId = wc.executeOnServer(() -> {
            HttpSession session = Stapler.getCurrentRequest2().getSession(false);
            return session != null ? session.getId() : null;
        });

        assertThat(sessionId, not(nullValue()));

        // dangerous stuff in Regular Login mode:
        /*
         * <td>Details:</td>
         * <td>org.acegisecurity.ui.WebAuthenticationDetails@12afc: RemoteIpAddress: 127.0.0.1; SessionId: node0gbmv9ly0f3h517eppoupykq6n0</td>
         *
         * <td>toString:</td>
         * <td>org.acegisecurity.providers.UsernamePasswordAuthenticationToken@d35a1467: Username: [toString()=S3cr3t];
         *     Password: [PROTECTED]; Authenticated: true; Details:
         *     org.acegisecurity.ui.WebAuthenticationDetails@12afc: RemoteIpAddress: 127.0.0.1; SessionId:
         *     node0gbmv9ly0f3h517eppoupykq6n0; Granted Authorities:
         * </td>
         */
        assertThat(content, not(anyOf(
                containsString("S3cr3t"),
                containsString("SessionId"),
                containsString(sessionId)
        )));
    }

    @Test
    @Issue("SECURITY-1695")
    void whoAmI_regularApi_doesNotProvideSensitiveInformation() throws Exception {
        j.jenkins.setSecurityRealm(new SecurityRealmImpl());

        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ).everywhere().to("user")
        );

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.login("user");

        Page whoAmIPage = wc.goTo("whoAmI/api/json", "application/json");
        String content = whoAmIPage.getWebResponse().getContentAsString();

        String sessionId = wc.executeOnServer(() -> {
            HttpSession session = Stapler.getCurrentRequest2().getSession(false);
            return session != null ? session.getId() : null;
        });

        assertThat(sessionId, not(nullValue()));

        // dangerous stuff in Regular Login mode with the api/json call:
        /*
         * {
         *    "_class": "hudson.security.WhoAmI",
         *    "anonymous": false,
         *    "authenticated": true,
         *    "authorities": [],
         *    "details": "org.acegisecurity.ui.WebAuthenticationDetails@fffc7f0c: RemoteIpAddress: 127.0.0.1; SessionId: node0g4xbfaaq1qb91pwyv0ctilrfu0",
         *    "name": "user",
         *    "toString": "org.acegisecurity.providers.UsernamePasswordAuthenticationToken@66074b8a:
         *        Username: [toString()=S3cr3t]; Password: [PROTECTED]; Authenticated: true;
         *        Details: org.acegisecurity.ui.WebAuthenticationDetails@fffc7f0c: RemoteIpAddress: 127.0.0.1; SessionId: node0g4xbfaaq1qb91pwyv0ctilrfu0;
         *        Granted Authorities: "
         * }
         */
        assertThat(content, not(anyOf(
                containsString("S3cr3t"),
                containsString("SessionId"),
                containsString(sessionId)
        )));
    }

    @Test
    @Issue("SECURITY-1697")
    void whoAmI_basic_doesNotProvideSensitiveInformation() throws Exception {
        j.jenkins.setSecurityRealm(new SecurityRealmImpl());

        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ).everywhere().to("user")
        );

        JenkinsRule.WebClient wc = j.createWebClient().withBasicCredentials("user", "user");

        HtmlPage whoAmIPage = wc.goTo("whoAmI");
        String content = whoAmIPage.getWebResponse().getContentAsString();

        // dangerous stuff in Basic mode:
        /*
         * <td>toString:</td>
         * <td>org.acegisecurity.providers.UsernamePasswordAuthenticationToken@e8fd00a7: Username: [toString()=S3cr3t];
         *
         * <td rowspan="1">Authorization</td>
         * <td>Basic dXNlcjp1c2Vy</td>
         */
        assertThat(content, not(anyOf(
                containsString("S3cr3t"),
                containsString("SessionId"),
                // base64 of user:user
                containsString(Base64.getEncoder().encodeToString("user:user".getBytes(StandardCharsets.UTF_8)))
        )));
    }

    @Test
    @Issue("SECURITY-1697")
    void whoAmI_apiToken_doesNotProvideSensitiveInformation() throws Exception {
        j.jenkins.setSecurityRealm(new SecurityRealmImpl());

        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ).everywhere().to("user")
        );

        User user = User.getById("user", true);
        ApiTokenProperty prop = user.getProperty(ApiTokenProperty.class);
        TokenUuidAndPlainValue token = prop.getTokenStore().generateNewToken("test");

        JenkinsRule.WebClient wc = j.createWebClient().withBasicCredentials("user", token.plainValue);
        String base64ApiToken = new String(Base64.getEncoder().encode(("user:" + token.plainValue).getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);

        HtmlPage whoAmIPage = wc.goTo("whoAmI");
        String content = whoAmIPage.getWebResponse().getContentAsString();

        // dangerous stuff in API Token mode:
        /*
         * <td rowspan="1">Authorization</td>
         * <td>Basic dXNlcjoxMTRiNGRmMWNhZTVkNDQ2MjgxZTJkZWEzMDY1NTEyZDBi</td>
         */
        assertThat(content, not(anyOf(
                containsString("S3cr3t"),
                containsString("SessionId"),
                containsString(base64ApiToken)
        )));
    }

    private static class SecurityRealmImpl extends AbstractPasswordBasedSecurityRealm {

        @Override
        protected UserDetails authenticate2(String username, String password) throws AuthenticationException {
            return createUserDetails(username);
        }

        @Override public UserDetails loadUserByUsername2(String username) throws UsernameNotFoundException {
            return createUserDetails(username);
        }

        @Override public GroupDetails loadGroupByGroupname2(String groupname, boolean fetchMembers) throws UsernameNotFoundException {
            return null;
        }

        private UserDetails createUserDetails(String username) {
            return new UserDetails() {

                @Override public String getUsername() {
                    return username;
                }

                @Override
                public String toString() {
                    return "[toString()=S3cr3t]";
                }

                @Override public Collection<? extends GrantedAuthority> getAuthorities() {
                    return Collections.emptySet();
                }

                @Override public String getPassword() {
                    return null;
                }

                @Override public boolean isAccountNonExpired() {
                    return true;
                }

                @Override public boolean isAccountNonLocked() {
                    return true;
                }

                @Override public boolean isCredentialsNonExpired() {
                    return true;
                }

                @Override public boolean isEnabled() {
                    return true;
                }
            };
        }
    }
}
