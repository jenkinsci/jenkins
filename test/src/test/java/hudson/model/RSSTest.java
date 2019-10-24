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

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

public class RSSTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    @Issue("JENKINS-59167")
    public void absoluteURLsPresentInRSS_evenWithoutRootUrlSetup() throws Exception {
        XmlPage page = getRssPage();
        NodeList allLinks = page.getXmlDocument().getElementsByTagName("link");

        assertEquals(1, allLinks.getLength());
        assertAllRSSLinksContainRootUrl(allLinks);

        FreeStyleProject p = j.createFreeStyleProject();
        j.assertBuildStatusSuccess(p.scheduleBuild2(0));

        page = getRssPage();
        allLinks = page.getXmlDocument().getElementsByTagName("link");

        assertEquals(2, allLinks.getLength());
        assertAllRSSLinksContainRootUrl(allLinks);
    }

    private XmlPage getRssPage() throws Exception {
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
        XmlPage page = getAtomPage();
        NodeList allLinks = page.getXmlDocument().getElementsByTagName("link");

        assertEquals(1, allLinks.getLength());
        assertAllAtomLinksContainRootUrl(allLinks);

        FreeStyleProject p = j.createFreeStyleProject();
        j.assertBuildStatusSuccess(p.scheduleBuild2(0));

        page = getAtomPage();
        allLinks = page.getXmlDocument().getElementsByTagName("link");

        assertEquals(2, allLinks.getLength());
        assertAllAtomLinksContainRootUrl(allLinks);
    }

    private XmlPage getAtomPage() throws Exception {
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
}
