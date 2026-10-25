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
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.xml.HasXPath.hasXPath;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.User;
import java.io.File;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import jenkins.security.ApiTokenProperty;
import net.sf.json.JSONObject;
import org.htmlunit.FailingHttpStatusCodeException;
import org.htmlunit.HttpMethod;
import org.htmlunit.Page;
import org.htmlunit.WebRequest;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.html.HtmlSpan;
import org.htmlunit.util.NameValuePair;
import org.htmlunit.xml.XmlPage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.For;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.jvnet.hudson.test.junit.jupiter.JenkinsSessionExtension;

@For(ApiTokenStats.class)
class ApiTokenStatsRestartTest {

    @RegisterExtension
    private final JenkinsSessionExtension sessions = new JenkinsSessionExtension();

    @Test
    @Issue("SECURITY-1072")
    void roundtripWithRestart() throws Throwable {
        AtomicReference<String> tokenValue = new AtomicReference<>();
        AtomicReference<String> tokenUuid = new AtomicReference<>();
        String TOKEN_NAME = "New Token Name";
        int NUM_CALL_WITH_TOKEN = 5;

        sessions.then(j -> {
                   j.jenkins.setCrumbIssuer(null);
                   j.jenkins.setSecurityRealm(j.createDummySecurityRealm());

                   User u = User.getById("foo", true);

                   ApiTokenProperty t = u.getProperty(ApiTokenProperty.class);
                   assertNotNull(t.getTokenStore());
                   assertNotNull(t.getTokenStats());

                   // test the authentication via Token
                   WebClient wc = j.createWebClient().withBasicCredentials(u.getId());
                   wc.getOptions().setThrowExceptionOnFailingStatusCode(false);

                   WebRequest request = new WebRequest(new URI(j.getURL() + "user/" + u.getId() + "/descriptorByName/" + ApiTokenProperty.class.getName() + "/generateNewToken").toURL(), HttpMethod.POST);
                   request.setRequestParameters(List.of(new NameValuePair("newTokenName", TOKEN_NAME)));

                   Page page = wc.getPage(request);
                   assertEquals(200, page.getWebResponse().getStatusCode());
                   String responseContent = page.getWebResponse().getContentAsString();
                   JSONObject jsonObject = JSONObject.fromObject(responseContent);
                   JSONObject jsonData = jsonObject.getJSONObject("data");
                   String tokenName = jsonData.getString("tokenName");
                   tokenValue.set(jsonData.getString("tokenValue"));
                   tokenUuid.set(jsonData.getString("tokenUuid"));

                   assertEquals(TOKEN_NAME, tokenName);

                   WebClient restWc = j.createWebClient().withBasicCredentials(u.getId(), tokenValue.get());
                   checkUserIsConnected(restWc, u.getId());

                   HtmlPage config = wc.goTo(u.getUrl() + "/security/");
                   assertEquals(200, config.getWebResponse().getStatusCode());
                   assertThat(config.getWebResponse().getContentAsString(), containsString(tokenUuid.get()));
                   assertThat(config.getWebResponse().getContentAsString(), containsString(tokenName));

                   // one is already done with checkUserIsConnected
                   for (int i = 1; i < NUM_CALL_WITH_TOKEN; i++) {
                       restWc.goToXml("whoAmI/api/xml");
                   }

                   HtmlPage configWithStats = wc.goTo(u.getUrl() + "/security/");
                   assertEquals(200, configWithStats.getWebResponse().getStatusCode());
                   HtmlSpan useCounterSpan = configWithStats.getDocumentElement().getOneHtmlElementByAttribute("span", "class", "token-use-counter");
                   assertThat(useCounterSpan.getTextContent(), containsString("" + NUM_CALL_WITH_TOKEN));

                   File apiTokenStatsFile = new File(u.getUserFolder(), "apiTokenStats.xml");
                   assertTrue(apiTokenStatsFile.exists(), "apiTokenStats.xml file should exist");
           });

        sessions.then(j -> {
                j.jenkins.setCrumbIssuer(null);

                User u = User.getById("foo", false);
                assertNotNull(u);

                WebClient wc = j.createWebClient().login(u.getId());
                checkUserIsConnected(wc, u.getId());

                HtmlPage config = wc.goTo(u.getUrl() + "/security/");
                assertEquals(200, config.getWebResponse().getStatusCode());
                assertThat(config.getWebResponse().getContentAsString(), containsString(tokenUuid.get()));
                assertThat(config.getWebResponse().getContentAsString(), containsString(TOKEN_NAME));
                HtmlSpan useCounterSpan = config.getDocumentElement().getOneHtmlElementByAttribute("span", "class", "token-use-counter");
                assertThat(useCounterSpan.getTextContent(), containsString("" + NUM_CALL_WITH_TOKEN));

                revokeToken(j, wc, u.getId(), tokenUuid.get());

                // token is no more valid
                WebClient restWc = j.createWebClient().withBasicCredentials(u.getId(), tokenValue.get());
                checkUserIsNotConnected(restWc);

                HtmlPage configWithoutToken = wc.goTo(u.getUrl() + "/security/");
                assertEquals(200, configWithoutToken.getWebResponse().getStatusCode());
                assertThat(configWithoutToken.getWebResponse().getContentAsString(), not(containsString(tokenUuid.get())));
                assertThat(configWithoutToken.getWebResponse().getContentAsString(), not(containsString(TOKEN_NAME)));
        });
    }

    private static void checkUserIsConnected(WebClient wc, String username) throws Exception {
        XmlPage xmlPage = wc.goToXml("whoAmI/api/xml");
        assertThat(xmlPage, hasXPath("//name", is(username)));
        assertThat(xmlPage, hasXPath("//anonymous", is("false")));
        assertThat(xmlPage, hasXPath("//authenticated", is("true")));
        assertThat(xmlPage, hasXPath("//authority", is("authenticated")));
    }

    private static void checkUserIsNotConnected(WebClient wc) {
        FailingHttpStatusCodeException e = assertThrows(FailingHttpStatusCodeException.class, () -> wc.goToXml("whoAmI/api/xml"));
        assertEquals(401, e.getStatusCode());
    }

    private static void revokeToken(JenkinsRule j, WebClient wc, String login, String tokenUuid) throws Exception {
        WebRequest request = new WebRequest(
                new URL(j.getURL(), "user/" + login + "/descriptorByName/" + ApiTokenProperty.class.getName() + "/revoke/?tokenUuid=" + tokenUuid),
                HttpMethod.POST
        );
        Page p = wc.getPage(request);
        assertEquals(200, p.getWebResponse().getStatusCode());
    }
}
