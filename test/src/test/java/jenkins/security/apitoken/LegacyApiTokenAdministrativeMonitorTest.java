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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.AdministrativeMonitor;
import hudson.model.User;
import java.io.IOException;
import jenkins.security.ApiTokenProperty;
import org.apache.commons.lang3.StringUtils;
import org.hamcrest.Matchers;
import org.htmlunit.Page;
import org.htmlunit.html.DomNodeList;
import org.htmlunit.html.HtmlAnchor;
import org.htmlunit.html.HtmlButton;
import org.htmlunit.html.HtmlDivision;
import org.htmlunit.html.HtmlElement;
import org.htmlunit.html.HtmlElementUtil;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;


@WithJenkins
class LegacyApiTokenAdministrativeMonitorTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    private enum SelectFilter {
        ALL(0),
        ONLY_FRESH(1),
        ONLY_RECENT(2);

        final int index;

        SelectFilter(int index) {
            this.index = index;
        }
    }

    @Test
    void isActive() throws Exception {
        ApiTokenPropertyConfiguration config = ApiTokenPropertyConfiguration.get();
        config.setCreationOfLegacyTokenEnabled(true);
        config.setTokenGenerationOnCreationEnabled(false);

        // user created without legacy token
        User user = User.getById("user", true);
        ApiTokenProperty apiTokenProperty = user.getProperty(ApiTokenProperty.class);
        assertFalse(apiTokenProperty.hasLegacyToken());

        LegacyApiTokenAdministrativeMonitor monitor = j.jenkins.getExtensionList(AdministrativeMonitor.class).get(LegacyApiTokenAdministrativeMonitor.class);
        assertFalse(monitor.isActivated());

        TokenUuidAndPlainValue tokenInfo = apiTokenProperty.getTokenStore().generateNewToken("Not Legacy");
        // "new" token does not trigger the monitor
        assertFalse(monitor.isActivated());

        apiTokenProperty.getTokenStore().revokeToken(tokenInfo.tokenUuid);
        assertFalse(monitor.isActivated());

        apiTokenProperty.changeApiToken();
        assertTrue(monitor.isActivated());
    }

    @Test
    @Issue("JENKINS-52441")
    void takeCareOfUserWithIdNull() throws Exception {
        ApiTokenPropertyConfiguration config = ApiTokenPropertyConfiguration.get();
        config.setCreationOfLegacyTokenEnabled(true);
        config.setTokenGenerationOnCreationEnabled(false);

        // user created without legacy token
        User user = User.getById("null", true);
        ApiTokenProperty apiTokenProperty = user.getProperty(ApiTokenProperty.class);
        assertFalse(apiTokenProperty.hasLegacyToken());

        LegacyApiTokenAdministrativeMonitor monitor = j.jenkins.getExtensionList(AdministrativeMonitor.class).get(LegacyApiTokenAdministrativeMonitor.class);
        assertFalse(monitor.isActivated());

        apiTokenProperty.changeApiToken();
        assertTrue(monitor.isActivated());

        { //revoke the legacy token
            JenkinsRule.WebClient wc = j.createWebClient();

            HtmlPage page = wc.goTo(monitor.getUrl() + "/manage");
            { // select all (only one user normally)
                HtmlAnchor filterAll = getFilterByIndex(page, SelectFilter.ALL);
                HtmlElementUtil.click(filterAll);
            }
            // revoke them
            HtmlButton revokeSelected = getRevokeSelected(page);
            HtmlElementUtil.click(revokeSelected);
        }

        assertFalse(monitor.isActivated());
    }

    @Test
    void listOfUserWithLegacyTokenIsCorrect() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());

        ApiTokenPropertyConfiguration config = ApiTokenPropertyConfiguration.get();
        config.setCreationOfLegacyTokenEnabled(true);
        config.setTokenGenerationOnCreationEnabled(false);

        LegacyApiTokenAdministrativeMonitor monitor = j.jenkins.getExtensionList(AdministrativeMonitor.class).get(LegacyApiTokenAdministrativeMonitor.class);
        JenkinsRule.WebClient wc = j.createWebClient();

        int numToken = 0;
        int numFreshToken = 0;
        int numRecentToken = 0;

        { // no user
            checkUserWithLegacyTokenListIsEmpty(wc, monitor);
        }
        { // with user without any token
            User user = User.getById("user", true);
            ApiTokenProperty apiTokenProperty = user.getProperty(ApiTokenProperty.class);
            assertFalse(apiTokenProperty.hasLegacyToken());

            checkUserWithLegacyTokenListIsEmpty(wc, monitor);
        }
        { // with user with token but without legacy token
            User user = User.getById("user", true);
            ApiTokenProperty apiTokenProperty = user.getProperty(ApiTokenProperty.class);
            assertFalse(apiTokenProperty.hasLegacyToken());

            apiTokenProperty.getTokenStore().generateNewToken("Not legacy");

            checkUserWithLegacyTokenListIsEmpty(wc, monitor);
            checkUserWithLegacyTokenListHasSizeOf(wc, monitor, numToken, numFreshToken, numRecentToken);
        }
        { // one user with just legacy token
            createUserWithToken(true, false, false);

            numToken++;

            checkUserWithLegacyTokenListHasSizeOf(wc, monitor, numToken, numFreshToken, numRecentToken);
        }
        { // one user with a fresh token
            // fresh = created after the last use of the legacy token (or its creation)
            createUserWithToken(true, true, false);

            numToken++;
            numFreshToken++;

            checkUserWithLegacyTokenListHasSizeOf(wc, monitor, numToken, numFreshToken, numRecentToken);
        }
        { // one user with a recent token (that is not fresh)
            // recent = last use after the last use of the legacy token (or its creation)
            createUserWithToken(true, false, true);

            numToken++;
            numRecentToken++;

            checkUserWithLegacyTokenListHasSizeOf(wc, monitor, numToken, numFreshToken, numRecentToken);
        }
        { // one user with a fresh + recent token
            createUserWithToken(true, true, true);

            numToken++;
            numFreshToken++;
            numRecentToken++;

            checkUserWithLegacyTokenListHasSizeOf(wc, monitor, numToken, numFreshToken, numRecentToken);
        }
    }

    @Test
    void monitorManagePageFilterAreWorking() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());

        ApiTokenPropertyConfiguration config = ApiTokenPropertyConfiguration.get();
        config.setCreationOfLegacyTokenEnabled(true);
        config.setTokenGenerationOnCreationEnabled(false);

        // create 1 user with legacy, 2 with fresh, 3 with recent and 4 with fresh+recent
        prepareUsersForFilters();

        LegacyApiTokenAdministrativeMonitor monitor = j.jenkins.getExtensionList(AdministrativeMonitor.class).get(LegacyApiTokenAdministrativeMonitor.class);
        JenkinsRule.WebClient wc = j.createWebClient();

        HtmlPage page = wc.goTo(monitor.getUrl() + "/manage");
        checkUserWithLegacyTokenListHasSizeOf(page, 1 + 2 + 3 + 4, 2 + 4, 3 + 4);

        HtmlElement document = page.getDocumentElement();
        HtmlElement filterDiv = document.getOneHtmlElementByAttribute("div", "class", "selection-panel");
        DomNodeList<HtmlElement> filters = filterDiv.getElementsByTagName("a");
        assertEquals(3, filters.size());
        HtmlAnchor filterAll = (HtmlAnchor) filters.get(0);
        HtmlAnchor filterOnlyFresh = (HtmlAnchor) filters.get(1);
        HtmlAnchor filterOnlyRecent = (HtmlAnchor) filters.get(2);

        { // test just the filterAll
            checkNumberOfSelectedTr(document, 0);

            HtmlElementUtil.click(filterAll);
            checkNumberOfSelectedTr(document, 1 + 2 + 3 + 4);

            HtmlElementUtil.click(filterAll);
            checkNumberOfSelectedTr(document, 0);
        }
        { // test just the filterOnlyFresh
            HtmlElementUtil.click(filterOnlyFresh);
            checkNumberOfSelectedTr(document, 2 + 4);

            HtmlElementUtil.click(filterOnlyFresh);
            checkNumberOfSelectedTr(document, 0);
        }
        { // test just the filterOnlyRecent
            HtmlElementUtil.click(filterOnlyRecent);
            checkNumberOfSelectedTr(document, 3 + 4);

            HtmlElementUtil.click(filterOnlyRecent);
            checkNumberOfSelectedTr(document, 0);
        }
        { // test interaction
            HtmlElementUtil.click(filterOnlyFresh);
            checkNumberOfSelectedTr(document, 2 + 4);

            // the 4 (recent+fresh) are still selected
            HtmlElementUtil.click(filterOnlyRecent);
            checkNumberOfSelectedTr(document, 3 + 4);

            HtmlElementUtil.click(filterAll);
            checkNumberOfSelectedTr(document, 1 + 2 + 3 + 4);
        }
    }

    private void prepareUsersForFilters() throws Exception {
        // 1 user with just legacy token
        createUserWithToken(true, false, false);

        // 2 users fresh but not recent
        createUserWithToken(true, true, false);
        createUserWithToken(true, true, false);

        // 3 users recent but not fresh
        createUserWithToken(true, false, true);
        createUserWithToken(true, false, true);
        createUserWithToken(true, false, true);

        // 4 users fresh and recent
        createUserWithToken(true, true, true);
        createUserWithToken(true, true, true);
        createUserWithToken(true, true, true);
        createUserWithToken(true, true, true);
    }

    private void checkNumberOfSelectedTr(HtmlElement document, int expectedCount) {
        DomNodeList<HtmlElement> trList = document.getElementsByTagName("tr");
        long amount = trList.stream().filter(htmlElement -> htmlElement.getAttribute("class").contains("selected")).count();
        assertEquals(expectedCount, amount);
    }

    @Test
    void monitorManagePageCanRevokeToken() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());

        ApiTokenPropertyConfiguration config = ApiTokenPropertyConfiguration.get();
        config.setCreationOfLegacyTokenEnabled(true);
        config.setTokenGenerationOnCreationEnabled(false);

        // create 1 user with legacy, 2 with fresh, 3 with recent and 4 with fresh+recent
        prepareUsersForFilters();

        LegacyApiTokenAdministrativeMonitor monitor = j.jenkins.getExtensionList(AdministrativeMonitor.class).get(LegacyApiTokenAdministrativeMonitor.class);
        assertTrue(monitor.isActivated());

        JenkinsRule.WebClient wc = j.createWebClient();

        HtmlPage page = wc.goTo(monitor.getUrl() + "/manage");
        checkUserWithLegacyTokenListHasSizeOf(page, 1 + 2 + 3 + 4, 2 + 4, 3 + 4);

        { // select 2
            HtmlAnchor filterOnlyFresh = getFilterByIndex(page, SelectFilter.ONLY_FRESH);
            HtmlElementUtil.click(filterOnlyFresh);
        }
        // revoke them
        HtmlButton revokeSelected = getRevokeSelected(page);
        HtmlElementUtil.click(revokeSelected);

        HtmlPage newPage = checkUserWithLegacyTokenListHasSizeOf(wc, monitor, 1 + 3, 0, 3);
        assertTrue(monitor.isActivated());

        { // select 1 + 3
            HtmlAnchor filterAll = getFilterByIndex(newPage, SelectFilter.ALL);
            HtmlElementUtil.click(filterAll);
        }
        // revoke them
        revokeSelected = getRevokeSelected(newPage);
        HtmlElementUtil.click(revokeSelected);
        checkUserWithLegacyTokenListHasSizeOf(wc, monitor, 0, 0, 0);
        assertFalse(monitor.isActivated());
    }

    private HtmlAnchor getFilterByIndex(HtmlPage page, SelectFilter selectFilter) {
        HtmlElement document = page.getDocumentElement();
        HtmlDivision filterDiv = document.getOneHtmlElementByAttribute("div", "class", "selection-panel");
        DomNodeList<HtmlElement> filters = filterDiv.getElementsByTagName("a");
        assertEquals(3, filters.size());

        HtmlAnchor filter = (HtmlAnchor) filters.get(selectFilter.index);
        assertNotNull(filter);
        return filter;
    }

    private HtmlButton getRevokeSelected(HtmlPage page) throws IOException {
        HtmlElement document = page.getDocumentElement();

        HtmlButton revokeSelected = document.querySelector("button.action-revoke-selected");
        assertNotNull(revokeSelected);
        HtmlElementUtil.click(revokeSelected);
        HtmlButton revokeButtonSelected = document.getOneHtmlElementByAttribute("button", "data-id", "ok");
        assertNotNull(revokeButtonSelected);
        return revokeButtonSelected;
    }

    private void checkUserWithLegacyTokenListIsEmpty(JenkinsRule.WebClient wc, LegacyApiTokenAdministrativeMonitor monitor) throws Exception {
        HtmlPage page = wc.goTo(monitor.getUrl() + "/manage");
        String pageContent = page.getWebResponse().getContentAsString();
        assertThat(pageContent, Matchers.containsString("no-token-line"));
    }

    private HtmlPage checkUserWithLegacyTokenListHasSizeOf(
            JenkinsRule.WebClient wc, LegacyApiTokenAdministrativeMonitor monitor,
            int countOfToken, int countOfFreshToken, int countOfRecentToken) throws Exception {
        HtmlPage page = wc.goTo(monitor.getUrl() + "/manage");
        checkUserWithLegacyTokenListHasSizeOf(page, countOfToken, countOfFreshToken, countOfRecentToken);
        return page;
    }

    private void checkUserWithLegacyTokenListHasSizeOf(
            Page page,
            int countOfToken, int countOfFreshToken, int countOfRecentToken) {
        String pageContent = page.getWebResponse().getContentAsString();

        int actualCountOfToken = StringUtils.countMatches(pageContent, "token-to-revoke");
        assertEquals(countOfToken, actualCountOfToken);

        int actualCountOfFreshToken = StringUtils.countMatches(pageContent, "fresh-token");
        assertEquals(countOfFreshToken, actualCountOfFreshToken);

        int actualCountOfRecentToken = StringUtils.countMatches(pageContent, "recent-token");
        assertEquals(countOfRecentToken, actualCountOfRecentToken);
    }

    private void simulateUseOfLegacyToken(User user) throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient();
        wc.withBasicCredentials(user.getId(), user.getProperty(ApiTokenProperty.class).getApiToken());

        wc.goTo("whoAmI/api/xml", null);
    }

    private void simulateUseOfToken(User user, String tokenPlainValue) throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient();
        wc.withBasicCredentials(user.getId(), tokenPlainValue);

        wc.goTo("whoAmI/api/xml", null);
    }

    private int nextId = 0;

    private void createUserWithToken(boolean legacy, boolean fresh, boolean recent) throws Exception {
        User user = User.getById(String.format("user %b %b %b %d", legacy, fresh, recent, nextId++), true);
        if (!legacy) {
            return;
        }

        ApiTokenProperty apiTokenProperty = user.getProperty(ApiTokenProperty.class);
        apiTokenProperty.changeApiToken();

        if (fresh) {
            if (recent) {
                simulateUseOfLegacyToken(user);
                Thread.sleep(1);

                TokenUuidAndPlainValue tokenInfo = apiTokenProperty.getTokenStore().generateNewToken("Fresh and recent token");
                simulateUseOfToken(user, tokenInfo.plainValue);
            } else {
                simulateUseOfLegacyToken(user);
                Thread.sleep(1);

                apiTokenProperty.getTokenStore().generateNewToken("Fresh token");
            }
        } else {
            if (recent) {
                TokenUuidAndPlainValue tokenInfo = apiTokenProperty.getTokenStore().generateNewToken("Recent token");
                Thread.sleep(1);

                simulateUseOfLegacyToken(user);
                Thread.sleep(1);

                simulateUseOfToken(user, tokenInfo.plainValue);
            }
            //else: no other token to generate
        }
    }
}
