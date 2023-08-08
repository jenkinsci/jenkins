/*
 * The MIT License
 *
 * Copyright (c) 2019 CloudBees, Inc.
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

package hudson.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import jenkins.model.Jenkins;
import org.htmlunit.xml.XmlPage;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.FailureBuilder;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class RSSTest {

    private static final String ROOT_PATH_PREFIX = "";
    private static final String FAILED_BUILD_TITLE = "test0 #1 (broken since this build)";
    private static final String STABLE_BUILD_TITLE = "test0 #1 (stable)";
    private static final String ALL_BUILD_TYPE = "all";
    private static final String FAILED_BUILD_TYPE = "failed";
    private static final String LATEST_BUILD_TYPE = "latest";

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    @Issue("JENKINS-59167")
    public void absoluteURLsPresentInRSS_evenWithoutRootUrlSetup_View() throws Exception {
        String pathPrefix = ROOT_PATH_PREFIX;
        XmlPage page = getRssAllPage(pathPrefix);
        NodeList allLinks = page.getXmlDocument().getElementsByTagName("link");

        assertEquals(1, allLinks.getLength());
        assertAllRSSLinksContainRootUrl(allLinks);

        runSuccessfulBuild();

        page = getRssAllPage(pathPrefix);
        allLinks = page.getXmlDocument().getElementsByTagName("link");

        assertEquals(2, allLinks.getLength());
        assertAllRSSLinksContainRootUrl(allLinks);
    }

    @Test
    public void checkInitialContent_Rss_All_AllView() throws Exception {
        String pathPrefix = ROOT_PATH_PREFIX;
        int expectedNodes = 3;
        XmlPage page = getRssAllPage(pathPrefix);
        Node channelNode = checkRssWrapperNodes(page.getXmlDocument());
        checkRssBasicNodes(channelNode, "Jenkins:All (all builds)", expectedNodes, pathPrefix);
    }

    @Test
    public void checkInitialContent_Rss_Failed_AllView() throws Exception {
        String pathPrefix = ROOT_PATH_PREFIX;
        int expectedNodes = 3;
        XmlPage page = getRssFailedPage(pathPrefix);
        Node channelNode = checkRssWrapperNodes(page.getXmlDocument());
        checkRssBasicNodes(channelNode, "Jenkins:All (failed builds)", expectedNodes, pathPrefix);
    }

    @Test
    public void checkInitialContent_Atom_All_AllView() throws Exception {
        int expectedNodes = 5;
        XmlPage page = getRssAllAtomPage();
        Element documentElement = page.getXmlDocument().getDocumentElement();
        assertThat(documentElement.getNodeName(), is("feed"));
        checkAtomBasicNodes(documentElement, "Jenkins:All (all builds)", expectedNodes);
    }

    @Test
    public void checkWithSingleBuild_Rss_All_AllView() throws Exception {
        runSuccessfulBuild();

        String pathPrefix = ROOT_PATH_PREFIX;
        String displayName = "All";
        String buildType = ALL_BUILD_TYPE;
        String buildTitle = STABLE_BUILD_TITLE;
        XmlPage page = getRssAllPage(pathPrefix);
        checkSingleBuild_Rss(page, pathPrefix, displayName, buildType, buildTitle);
    }

    @Test
    public void checkWithSingleBuild_Rss_Failed_AllView() throws Exception {
        runFailingBuild();

        String pathPrefix = ROOT_PATH_PREFIX;
        String displayName = "All";
        String buildType = FAILED_BUILD_TYPE;
        String buildTitle = FAILED_BUILD_TITLE;
        XmlPage page = getRssFailedPage(pathPrefix);
        checkSingleBuild_Rss(page, pathPrefix, displayName, buildType, buildTitle);
    }

    @Test
    public void checkWithSingleBuild_Atom_All_AllView() throws Exception {
        runSuccessfulBuild();

        String displayName = "All";
        String buildType = ALL_BUILD_TYPE;
        String buildTitle = STABLE_BUILD_TITLE;
        XmlPage page = getRssAllAtomPage();
        checkSingleBuild_Atom(page, displayName, buildType, buildTitle);
    }

    @Test
    @Issue("JENKINS-59167")
    public void absoluteURLsPresentInAtom_evenWithoutRootUrlSetup_View() throws Exception {
        XmlPage page = getRssAllAtomPage();
        NodeList allLinks = page.getXmlDocument().getElementsByTagName("link");

        assertEquals(1, allLinks.getLength());
        assertAllAtomLinksContainRootUrl(allLinks);

        runSuccessfulBuild();

        page = getRssAllAtomPage();
        allLinks = page.getXmlDocument().getElementsByTagName("link");

        assertEquals(2, allLinks.getLength());
        assertAllAtomLinksContainRootUrl(allLinks);
    }

    @Issue("JENKINS-60577")
    @Test
    public void latestBuilds_AllView() throws Exception {
        String pathPrefix = ROOT_PATH_PREFIX;
        String displayName = "All";
        String buildType = LATEST_BUILD_TYPE;
        int expectedLinks = 3;
        int expectedAllLinks = 6;
        checkLatestBuilds(j.createWebClient(), pathPrefix, displayName, buildType, null, expectedLinks, expectedAllLinks);
    }

    @Test
    public void checkWithSingleBuild_Rss_All_Computer() throws Exception {
        runSuccessfulBuild();

        String pathPrefix = "computer/(built-in)/";
        String displayName = Messages.Hudson_Computer_DisplayName();
        String buildType = ALL_BUILD_TYPE;
        String buildTitle = STABLE_BUILD_TITLE;
        XmlPage page = getRssAllPage(pathPrefix);
        checkSingleBuild_Rss(page, pathPrefix, displayName, buildType, buildTitle);
    }

    @Test
    public void checkWithSingleBuild_Rss_Failed_Computer() throws Exception {
        runFailingBuild();

        String pathPrefix = "computer/(built-in)/";
        String displayName = Messages.Hudson_Computer_DisplayName();
        String buildType = FAILED_BUILD_TYPE;
        String buildTitle = FAILED_BUILD_TITLE;
        XmlPage page = getRssFailedPage(pathPrefix);
        checkSingleBuild_Rss(page, pathPrefix, displayName, buildType, buildTitle);
    }

    @Test
    public void latestBuilds_Computer() throws Exception {
        String pathPrefix = "computer/(built-in)/";
        String displayName = Messages.Hudson_Computer_DisplayName();
        String buildType = LATEST_BUILD_TYPE;
        int expectedLinks = 3;
        int expectedAllLinks = 6;
        checkLatestBuilds(j.createWebClient(), pathPrefix, displayName, buildType, null, expectedLinks, expectedAllLinks);
    }

    @Test
    public void checkWithSingleBuild_Rss_All_Job() throws Exception {
        runSuccessfulBuild();

        String pathPrefix = "job/test0/";
        String displayName = "test0";
        String buildType = ALL_BUILD_TYPE;
        String buildTitle = STABLE_BUILD_TITLE;
        XmlPage page = getRssAllPage(pathPrefix);
        checkSingleBuild_Rss(page, pathPrefix, displayName, buildType, buildTitle);
    }

    @Test
    public void checkWithSingleBuild_Rss_Failed_Job() throws Exception {
        runFailingBuild();

        String pathPrefix = "job/test0/";
        String displayName = "test0";
        String buildType = FAILED_BUILD_TYPE;
        String buildTitle = FAILED_BUILD_TITLE;
        XmlPage page = getRssFailedPage(pathPrefix);
        checkSingleBuild_Rss(page, pathPrefix, displayName, buildType, buildTitle);
    }

    @Test
    public void checkWithSingleBuild_Rss_All_NewView() throws Exception {
        FreeStyleProject p = runSuccessfulBuild();

        ListView newView = new ListView("newView");
        j.jenkins.addView(newView);
        newView.add(p);

        String pathPrefix = "view/newView/";
        String displayName = "newView";
        String buildType = ALL_BUILD_TYPE;
        String buildTitle = STABLE_BUILD_TITLE;
        XmlPage page = getRssAllPage(pathPrefix);
        checkSingleBuild_Rss(page, pathPrefix, displayName, buildType, buildTitle);
    }

    @Test
    public void checkWithSingleBuild_Rss_All_User() throws Exception {
        String userId = "alice";
        JenkinsRule.WebClient wc = loginAsUser(userId);

        runSuccessfulBuild(userId);

        String pathPrefix = "user/alice";
        String displayName = userId;
        String buildType = ALL_BUILD_TYPE;
        String buildTitle = STABLE_BUILD_TITLE;
        XmlPage page = getRssAllPage(wc, pathPrefix);
        checkSingleBuild_Rss(page, pathPrefix, displayName, buildType, buildTitle);
    }

    @Test
    public void checkWithSingleBuild_Rss_Failed_User() throws Exception {
        String userId = "alice";
        JenkinsRule.WebClient wc = loginAsUser(userId);

        runFailingBuild(userId);

        String pathPrefix = "user/alice";
        String displayName = userId;
        String buildType = FAILED_BUILD_TYPE;
        String buildTitle = FAILED_BUILD_TITLE;
        XmlPage page = getRssFailedPage(wc, pathPrefix);
        checkSingleBuild_Rss(page, pathPrefix, displayName, buildType, buildTitle);
    }

    @Test
    public void latestBuilds_User() throws Exception {
        String userId = "alice";
        JenkinsRule.WebClient wc = loginAsUser(userId);
        String pathPrefix = "user/alice";
        String displayName = userId;
        String buildType = LATEST_BUILD_TYPE;
        int expectedLinks = 3;
        int expectedAllLinks = 6;
        checkLatestBuilds(wc, pathPrefix, displayName, buildType, userId, expectedLinks, expectedAllLinks);
    }

    @Test
    public void latestBuilds_User_NotCaused() throws Exception {
        String userId = "alice";
        JenkinsRule.WebClient wc = loginAsUser(userId);
        String pathPrefix = "user/alice";
        String displayName = userId;
        String buildType = LATEST_BUILD_TYPE;
        int expectedLinks = 1;
        int expectedAllLinks = 1;
        checkLatestBuilds(wc, pathPrefix, displayName, buildType, null, expectedLinks, expectedAllLinks);
    }

    @Test
    public void checkInitialContent_Atom_AllLog() throws Exception {
        JenkinsRule.WebClient webClient = j.createWebClient();
        XmlPage page = (XmlPage) webClient.goTo("log/rss", "application/atom+xml");
        Document xmlDocument = page.getXmlDocument();
        Element documentElement = xmlDocument.getDocumentElement();
        assertThat(documentElement.getNodeName(), is("feed"));
        int expectedNodes = 5;
        checkAtomBasicNodes(documentElement, "Jenkins:log (all entries)", expectedNodes);
    }

    @Test
    public void checkInitialContent_Atom_SevereLog() throws Exception {
        JenkinsRule.WebClient webClient = j.createWebClient();
        XmlPage page = (XmlPage) webClient.goTo("log/rss?level=SEVERE", "application/atom+xml");
        Document xmlDocument = page.getXmlDocument();
        Element documentElement = xmlDocument.getDocumentElement();
        assertThat(documentElement.getNodeName(), is("feed"));
        int expectedNodes = 5;
        checkAtomBasicNodes(documentElement, "Jenkins:log (SEVERE entries)", expectedNodes);
    }

    @Test
    public void checkInitialContent_Atom_WarningLog() throws Exception {
        JenkinsRule.WebClient webClient = j.createWebClient();
        XmlPage page = (XmlPage) webClient.goTo("log/rss?level=WARNING", "application/atom+xml");
        Document xmlDocument = page.getXmlDocument();
        Element documentElement = xmlDocument.getDocumentElement();
        assertThat(documentElement.getNodeName(), is("feed"));
        int expectedNodes = 5;
        checkAtomBasicNodes(documentElement, "Jenkins:log (WARNING entries)", expectedNodes);
    }

    private JenkinsRule.WebClient loginAsUser(String userId) throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(Jenkins.ADMINISTER).everywhere().to(userId));
        User alice = User.getById(userId, true);
        JenkinsRule.WebClient wc = j.createWebClient();
        wc.login(alice.getId());
        return wc;
    }

    private Node checkRssWrapperNodes(Document xmlDocument) {
        Element documentElement = xmlDocument.getDocumentElement();
        assertThat(documentElement.getNodeName(), is("rss"));
        assertThat(documentElement.getAttribute("version"), is("2.0"));
        assertThat(documentElement.getChildNodes().getLength(), is(1));
        Node channelNode = documentElement.getFirstChild();
        assertThat(channelNode.getNodeName(), is("channel"));
        return channelNode;
    }

    private XmlPage getRssFailedPage(String pathPrefix) throws IOException, SAXException {
        return getRssFailedPage(j.createWebClient(), pathPrefix);
    }

    private XmlPage getRssFailedPage(JenkinsRule.WebClient webClient, String pathPrefix) throws IOException, SAXException {
        String prefix = computeAdjustedPathPrefix(pathPrefix);
        return (XmlPage) webClient.goTo(prefix + "rssFailed?flavor=rss20", "text/xml");
    }

    private void checkRssBasicNodes(Node channelNode, String expectedTitle, int expectedNodes, String path) throws IOException {
        assertThat(channelNode.getChildNodes().getLength(), is(expectedNodes));
        assertThat(getSingleNode(channelNode, "link").getTextContent(), is(j.getURL().toString() + path));
        assertThat(getSingleNode(channelNode, "description").getTextContent(), is(expectedTitle));
        assertThat(getSingleNode(channelNode, "title").getTextContent(), is(expectedTitle));
    }

    private void checkAtomBasicNodes(Node feedNode, String expectedTitle, int expectedNodes) throws IOException {
        if (expectedNodes >= 0) {
            assertThat(feedNode.getChildNodes().getLength(), is(expectedNodes));
        }
        Node linkNode = getSingleNode(feedNode, "link");
        assertThat(linkNode.getAttributes().getNamedItem("rel").getTextContent(), is("alternate"));
        assertThat(linkNode.getAttributes().getNamedItem("type").getTextContent(), is("text/html"));
        assertThat(linkNode.getAttributes().getNamedItem("href").getTextContent(), is(j.getURL().toString()));
        assertNotNull(getSingleNode(feedNode, "updated"));
        assertThat(getSingleNode(feedNode, "title").getTextContent(), is(expectedTitle));
        Node authorNode = getSingleNode(feedNode, "author");
        NodeList authorNodes = authorNode.getChildNodes();
        assertThat(authorNodes.getLength(), is(1));
        Node nameNode = authorNodes.item(0);
        assertThat(nameNode.getTextContent(), is("Jenkins Server"));
        Node idNode = getSingleNode(feedNode, "id");
        assertFalse(idNode.getTextContent().isEmpty());
    }

    private Node getSingleNode(Node parentNode, String nodeName) {
        Node childNode = null;
        NodeList childNodes = parentNode.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            if (childNodes.item(i).getNodeName().equals(nodeName)) {
                if (childNode == null) {
                    childNode = childNodes.item(i);
                } else {
                    fail("Too many children.");
                }
            }
        }
        return childNode;
    }

    private void checkRssTimeNode(Node firstBuild, String nodeName) throws ParseException {
        String pubDate = getSingleNode(firstBuild, nodeName).getTextContent();
        assertThat(pubDate, not(emptyString()));
        DateFormat formatter = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz");
        Date date = formatter.parse(pubDate);
        assertNotNull(date);
    }

    private void checkAtomTimeNode(Node firstBuild, String nodeName) {
        String publishedString = getSingleNode(firstBuild, nodeName).getTextContent();
        assertNotNull(publishedString);
        assertThat(publishedString, is(not(emptyString())));
        OffsetDateTime dateTime = OffsetDateTime.parse(publishedString);
        assertNotNull(dateTime);
    }

    private XmlPage getRssAllPage(String pathPrefix) throws Exception {
        return getRssAllPage(j.createWebClient(), pathPrefix);
    }

    private XmlPage getRssAllPage(JenkinsRule.WebClient webClient, String pathPrefix) throws Exception {
        String prefix = computeAdjustedPathPrefix(pathPrefix);
        return (XmlPage) webClient.goTo(prefix + "rssAll?flavor=rss20", "text/xml");
    }

    private String computeAdjustedPathPrefix(String pathPrefix) {
        return pathPrefix.isEmpty() || pathPrefix.endsWith("/") ? pathPrefix : pathPrefix + "/";
    }

    private void assertAllRSSLinksContainRootUrl(NodeList allLinks) throws Exception {
        for (int i = 0; i < allLinks.getLength(); i++) {
            Node item = allLinks.item(i);
            String url = item.getTextContent();
            assertThat(url, containsString(j.getURL().toString()));
        }
    }

    private XmlPage getRssAllAtomPage() throws Exception {
        return (XmlPage) j.createWebClient().goTo("rssAll", "application/atom+xml");
    }

    private void assertAllAtomLinksContainRootUrl(NodeList allLinks) throws Exception {
        for (int i = 0; i < allLinks.getLength(); i++) {
            Node item = allLinks.item(i);
            Node hrefAttr = item.getAttributes().getNamedItem("href");
            String url = hrefAttr.getNodeValue();
            assertThat(url, containsString(j.getURL().toString()));
        }
    }

    private XmlPage getRssLatestPage(JenkinsRule.WebClient webClient, String pathPrefix) throws Exception {
        String prefix = computeAdjustedPathPrefix(pathPrefix);
        return (XmlPage) webClient.goTo(prefix + "rssLatest?flavor=rss20", "text/xml");
    }

    private FreeStyleProject runSuccessfulBuild() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        j.buildAndAssertSuccess(p);
        return p;
    }

    private FreeStyleProject runSuccessfulBuild(String userId) throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        j.assertBuildStatusSuccess(p.scheduleBuild2(0, new Cause.UserIdCause(userId)));
        return p;
    }

    private void runFailingBuild() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.getBuildersList().add(new FailureBuilder());
        j.buildAndAssertStatus(Result.FAILURE, p);
    }

    private void runFailingBuild(String userId) throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.getBuildersList().add(new FailureBuilder());
        j.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0, new Cause.UserIdCause(userId)).get());
    }

    private void assertLatestRSSLinks(NodeList allLinks, String path) throws Exception {
        List<String> urls = new ArrayList<>(allLinks.getLength());
        for (int i = 0; i < allLinks.getLength(); i++) {
            Node item = allLinks.item(i);
            String url = item.getTextContent();
            urls.add(url);
        }

        assertThat(urls, containsInAnyOrder(
                j.getURL().toString() + path,
                j.getURL().toString() + "job/test1/2/",
                j.getURL().toString() + "job/test2/3/"
        ));
    }

    private void checkSingleBuild_Rss(XmlPage page, String pathPrefix, String displayName, String buildType, String buildTitle) throws Exception {
        Document xmlDocument = page.getXmlDocument();
        Node channelNode = checkRssWrapperNodes(xmlDocument);
        checkRssBasicNodes(channelNode, "Jenkins:" + displayName + " (" + buildType + " builds)", 4, pathPrefix);
        NodeList items = xmlDocument.getElementsByTagName("item");
        assertThat(items.getLength(), is(1));
        Node firstBuild = items.item(0);
        assertThat(firstBuild.getChildNodes().getLength(), is(5));
        assertThat(getSingleNode(firstBuild, "title").getTextContent(), is(buildTitle));
        checkRssTimeNode(firstBuild, "pubDate");
        assertNotNull(getSingleNode(firstBuild, "author").getTextContent());
        Node guidNode = getSingleNode(firstBuild, "guid");
        assertThat(guidNode.getAttributes().getNamedItem("isPermaLink").getTextContent(), is("false"));
        assertNotNull(guidNode.getTextContent());
    }

    private void checkSingleBuild_Atom(XmlPage page, String displayName, String buildType, String buildTitle) throws IOException {
        Document xmlDocument = page.getXmlDocument();
        Element documentElement = xmlDocument.getDocumentElement();
        assertThat(documentElement.getNodeName(), is("feed"));
        checkAtomBasicNodes(documentElement, "Jenkins:" + displayName + " (" + buildType + " builds)", 6);
        NodeList entries = xmlDocument.getElementsByTagName("entry");
        assertThat(entries.getLength(), is(1));
        Node firstBuild = entries.item(0);
        assertThat(firstBuild.getChildNodes().getLength(), is(5));
        assertThat(getSingleNode(firstBuild, "title").getTextContent(), is(buildTitle));
        checkAtomTimeNode(firstBuild, "published");
        checkAtomTimeNode(firstBuild, "updated");
        assertNotNull(getSingleNode(firstBuild, "id").getTextContent());
        Node linkNode = getSingleNode(firstBuild, "link");
        assertThat(linkNode.getAttributes().getNamedItem("rel").getTextContent(), is("alternate"));
        assertThat(linkNode.getAttributes().getNamedItem("type").getTextContent(), is("text/html"));
        assertThat(linkNode.getAttributes().getNamedItem("href").getTextContent(), containsString(j.getURL().toString()));
    }

    private void checkLatestBuilds(JenkinsRule.WebClient webClient, String pathPrefix, String displayName, String buildType, String userId,
                                   int expectedLatesLinks, int expectedAllLinks) throws Exception {
        XmlPage page = getRssLatestPage(webClient, pathPrefix);
        NodeList allLinks = page.getXmlDocument().getElementsByTagName("link");

        assertEquals(1, allLinks.getLength());
        Node channelNode = checkRssWrapperNodes(page.getXmlDocument());
        checkRssBasicNodes(channelNode, "Jenkins:" + displayName + " (" + buildType + " builds)", 3, pathPrefix);

        FreeStyleProject p = j.createFreeStyleProject("test1");
        j.assertBuildStatusSuccess(p.scheduleBuild2(0, new Cause.UserIdCause(userId)));
        j.assertBuildStatusSuccess(p.scheduleBuild2(0, new Cause.UserIdCause(userId)));

        p = j.createFreeStyleProject("test2");
        j.assertBuildStatusSuccess(p.scheduleBuild2(0, new Cause.UserIdCause(userId)));
        j.assertBuildStatusSuccess(p.scheduleBuild2(0, new Cause.UserIdCause(userId)));
        j.assertBuildStatusSuccess(p.scheduleBuild2(0, new Cause.UserIdCause(userId)));

        page = getRssLatestPage(webClient, pathPrefix);
        allLinks = page.getXmlDocument().getElementsByTagName("link");

        assertEquals(expectedLatesLinks, allLinks.getLength());
        if (expectedLatesLinks > 1) {
            assertLatestRSSLinks(allLinks, pathPrefix);
        }

        page = getRssAllPage(webClient, pathPrefix);
        allLinks = page.getXmlDocument().getElementsByTagName("link");
        assertEquals(expectedAllLinks, allLinks.getLength());
    }

}
