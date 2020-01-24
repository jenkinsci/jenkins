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
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

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
