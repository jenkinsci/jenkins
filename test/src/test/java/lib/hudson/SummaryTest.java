/*
 * The MIT License
 *
 * Copyright (c) 2026, Jenkins project contributors
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

package lib.hudson;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

import hudson.model.InvisibleAction;
import hudson.model.RootAction;
import org.htmlunit.html.DomNodeList;
import org.htmlunit.html.HtmlElement;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class SummaryTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void iconOnlyLinksIconButNotBody() throws Exception {
        HtmlPage page = j.createWebClient().goTo("summary");

        HtmlElement summary = page.getHtmlElementById("icon-only-summary");
        DomNodeList<HtmlElement> anchors = summary.getElementsByTagName("a");
        assertThat(anchors.size(), is(1));
        assertThat(anchors.get(0).getAttribute("href"), is("target"));
        assertThat(anchors.get(0).getElementsByTagName("svg").getFirst(), notNullValue());
        assertThat(anchors.get(0).getTextContent(), not(containsString("Icon-only body")));
        assertThat(summary.getTextContent(), containsString("Icon-only body"));
    }

    @Test
    void hrefLinksIconAndBodyByDefault() throws Exception {
        HtmlPage page = j.createWebClient().goTo("summary");

        HtmlElement summary = page.getHtmlElementById("default-summary");
        DomNodeList<HtmlElement> anchors = summary.getElementsByTagName("a");
        assertThat(anchors.size(), is(2));
        assertThat(anchors.get(0).getAttribute("href"), is("target"));
        assertThat(anchors.get(0).getElementsByTagName("svg").getFirst(), notNullValue());
        assertThat(anchors.get(1).getAttribute("href"), is("target"));
        assertThat(anchors.get(1).getTextContent(), containsString("Default body"));
    }

    @Test
    void withoutHrefDoesNotLinkIconOrBody() throws Exception {
        HtmlPage page = j.createWebClient().goTo("summary");

        HtmlElement summary = page.getHtmlElementById("no-href-summary");
        assertThat(summary.getElementsByTagName("a").size(), is(0));
        assertThat(summary.getTextContent(), containsString("No href body"));
    }

    @TestExtension
    public static class TestRootAction extends InvisibleAction implements RootAction {

        @Override
        public String getUrlName() {
            return "summary";
        }
    }
}
