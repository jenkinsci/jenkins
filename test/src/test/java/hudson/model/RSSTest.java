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

import com.gargoylesoftware.htmlunit.xml.XmlPage;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

public class RSSTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    @Issue("JENKINS-59167")
    public void absoluteURLsPresentInRSS_evenWithoutRootUrlSetup() throws Exception {
        XmlPage page = getRssAllPage();
        NodeList allLinks = page.getXmlDocument().getElementsByTagName("link");

        assertEquals(1, allLinks.getLength());
        assertAllRSSLinksContainRootUrl(allLinks);

        FreeStyleProject p = j.createFreeStyleProject();
        j.assertBuildStatusSuccess(p.scheduleBuild2(0));

        page = getRssAllPage();
        allLinks = page.getXmlDocument().getElementsByTagName("link");

        assertEquals(2, allLinks.getLength());
        assertAllRSSLinksContainRootUrl(allLinks);
    }

    @Test
    public void checkInitialContentAllRss() throws Exception {
        XmlPage page = getRssAllPage();
        Document xmlDocument = page.getXmlDocument();
        Element documentElement = xmlDocument.getDocumentElement();
        assertThat(documentElement.getNodeName(), is("rss"));
        assertThat(documentElement.getAttribute("version"), is("2.0"));
        assertThat(documentElement.getChildNodes().getLength(), is(1));
        Node channelNode = documentElement.getFirstChild();
        assertThat(channelNode.getNodeName(), is("channel"));
        checkRssBasicNodes(channelNode, "All builds", 3);
    }

    @Test
    public void checkInitialContentFailedRss() throws Exception {
        XmlPage page = (XmlPage) j.createWebClient().goTo("rssFailed?flavor=rss20", "text/xml");
        Document xmlDocument = page.getXmlDocument();
        Element documentElement = xmlDocument.getDocumentElement();
        assertThat(documentElement.getNodeName(), is("rss"));
        assertThat(documentElement.getAttribute("version"), is("2.0"));
        assertThat(documentElement.getChildNodes().getLength(), is(1));
        Node channelNode = documentElement.getFirstChild();
        assertThat(channelNode.getNodeName(), is("channel"));
        checkRssBasicNodes(channelNode, "All failed builds", 3);
    }

    @Test
    public void checkInitialContentAllAtom() throws Exception {
        XmlPage page = getRssAllAtomPage();
        Document xmlDocument = page.getXmlDocument();
        Element documentElement = xmlDocument.getDocumentElement();
        assertThat(documentElement.getNodeName(), is("feed"));
        checkAtomBasicNodes(documentElement, "All builds", 5);
    }

    @Test
    public void checkWithSingleBuildAllRss() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        j.assertBuildStatusSuccess(p.scheduleBuild2(0));

        XmlPage page = getRssAllPage();
        Document xmlDocument = page.getXmlDocument();
        Element documentElement = xmlDocument.getDocumentElement();
        assertThat(documentElement.getNodeName(), is("rss"));
        assertThat(documentElement.getAttribute("version"), is("2.0"));
        assertThat(documentElement.getChildNodes().getLength(), is(1));
        Node channelNode = documentElement.getFirstChild();
        assertThat(channelNode.getNodeName(), is("channel"));
        checkRssBasicNodes(channelNode, "All builds", 4);
        NodeList items = xmlDocument.getElementsByTagName("item");
        assertThat(items.getLength(), is(1));
        Node firstBuild = items.item(0);
        assertThat(firstBuild.getChildNodes().getLength(), is(5));
        assertThat(getSingleNode(firstBuild, "title").getTextContent(), is("test0 #1 (stable)"));
        checkRssTimeNode(firstBuild, "pubDate");
        assertNotNull(getSingleNode(firstBuild, "author").getTextContent());
        Node guidNode = getSingleNode(firstBuild, "guid");
        assertThat(guidNode.getAttributes().getNamedItem("isPermaLink").getTextContent(), is("false"));
        assertNotNull(guidNode.getTextContent());
    }

    @Test
    public void checkWithSingleBuildAllAtom() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        j.assertBuildStatusSuccess(p.scheduleBuild2(0));

        XmlPage page = getRssAllAtomPage();
        Document xmlDocument = page.getXmlDocument();
        Element documentElement = xmlDocument.getDocumentElement();
        assertThat(documentElement.getNodeName(), is("feed"));
        checkAtomBasicNodes(documentElement, "All builds", 6);
        NodeList entries = xmlDocument.getElementsByTagName("entry");
        assertThat(entries.getLength(), is(1));
        Node firstBuild = entries.item(0);
        assertThat(firstBuild.getChildNodes().getLength(), is(5));
        assertThat(getSingleNode(firstBuild, "title").getTextContent(), is("test0 #1 (stable)"));
        checkAtomTimeNode(firstBuild, "published");
        checkAtomTimeNode(firstBuild, "updated");
        assertNotNull(getSingleNode(firstBuild, "id").getTextContent());
        Node linkNode = getSingleNode(firstBuild, "link");
        assertThat(linkNode.getAttributes().getNamedItem("rel").getTextContent(), is("alternate"));
        assertThat(linkNode.getAttributes().getNamedItem("type").getTextContent(), is("text/html"));
        assertThat(linkNode.getAttributes().getNamedItem("href").getTextContent(), containsString(j.getURL().toString()));
    }

    @Test
    @Issue("JENKINS-59167")
    public void absoluteURLsPresentInAtom_evenWithoutRootUrlSetup() throws Exception {
        XmlPage page = getRssAllAtomPage();
        NodeList allLinks = page.getXmlDocument().getElementsByTagName("link");

        assertEquals(1, allLinks.getLength());
        assertAllAtomLinksContainRootUrl(allLinks);

        FreeStyleProject p = j.createFreeStyleProject();
        j.assertBuildStatusSuccess(p.scheduleBuild2(0));

        page = getRssAllAtomPage();
        allLinks = page.getXmlDocument().getElementsByTagName("link");

        assertEquals(2, allLinks.getLength());
        assertAllAtomLinksContainRootUrl(allLinks);
    }

    @Issue("JENKINS-60577")
    @Test
    public void latestBuilds() throws Exception {
        XmlPage page = getRssLatestPage();
        NodeList allLinks = page.getXmlDocument().getElementsByTagName("link");

        assertEquals(1, allLinks.getLength());

        FreeStyleProject p = j.createFreeStyleProject("test1");
        j.assertBuildStatusSuccess(p.scheduleBuild2(0));
        j.assertBuildStatusSuccess(p.scheduleBuild2(0));

        p = j.createFreeStyleProject("test2");
        j.assertBuildStatusSuccess(p.scheduleBuild2(0));
        j.assertBuildStatusSuccess(p.scheduleBuild2(0));
        j.assertBuildStatusSuccess(p.scheduleBuild2(0));

        page = getRssLatestPage();
        allLinks = page.getXmlDocument().getElementsByTagName("link");

        assertEquals(3, allLinks.getLength());
        assertLatestRSSLinks(allLinks);

        page = getRssAllPage();
        allLinks = page.getXmlDocument().getElementsByTagName("link");
        assertEquals(6, allLinks.getLength());
    }

    private void checkRssBasicNodes(Node channelNode, String expectedTitle, int expectedNodes) throws IOException {
        assertThat(channelNode.getChildNodes().getLength(), is(expectedNodes));
        assertThat(getSingleNode(channelNode, "link").getTextContent(), is(j.getURL().toString()));
        assertThat(getSingleNode(channelNode, "description").getTextContent(), is(expectedTitle));
        assertThat(getSingleNode(channelNode, "title").getTextContent(), is(expectedTitle));
    }

    private void checkAtomBasicNodes(Node feedNode, String expectedTitle, int expectedNodes) throws IOException {
        assertThat(feedNode.getChildNodes().getLength(), is(expectedNodes));
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

    private XmlPage getRssAllPage() throws Exception {
        return (XmlPage) j.createWebClient().goTo("rssAll?flavor=rss20", "text/xml");
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

    private XmlPage getRssLatestPage() throws Exception {
        return (XmlPage) j.createWebClient().goTo("rssLatest?flavor=rss20", "text/xml");
    }

    private void assertLatestRSSLinks(NodeList allLinks) throws Exception {
        List<String> urls = new ArrayList<>(allLinks.getLength());
        for (int i = 0; i < allLinks.getLength(); i++) {
            Node item = allLinks.item(i);
            String url = item.getTextContent();
            urls.add(url);
        }

        assertThat(urls, containsInAnyOrder(
                j.getURL().toString(),
                j.getURL().toString() + "job/test1/2/",
                j.getURL().toString() + "job/test2/3/"
        ));
    }
}
