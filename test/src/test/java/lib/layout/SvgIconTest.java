/*
 * The MIT License
 *
 * Copyright (c) 2020 CloudBees, Inc.
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

package lib.layout;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.gargoylesoftware.htmlunit.html.HtmlPage;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.model.UnprotectedRootAction;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;

public class SvgIconTest  {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    @Issue("JENKINS-60920")
    public void regularUsage() throws Exception {
        TestRootAction testRootAction = j.jenkins.getExtensionList(UnprotectedRootAction.class).get(TestRootAction.class);

        String desiredTooltip = "Hello world!";
        testRootAction.tooltipContent = desiredTooltip;

        HtmlPage p = j.createWebClient().goTo(testRootAction.getUrlName());
        assertThat(p.getWebResponse().getContentAsString(), containsString(desiredTooltip));
    }

    @Test
    @Issue("JENKINS-60920")
    public void onlyQuotesAreEscaped() throws Exception {
        TestRootAction testRootAction = j.jenkins.getExtensionList(UnprotectedRootAction.class).get(TestRootAction.class);

        String pristineTooltip = "Special tooltip with double quotes \", simple quotes ', and html characters <>&.";

        // Escaped twice, once per new h.xmlEscape then once per Jelly.
        // But as the tooltip lib interprets HTML, it's fine, the tooltip displays the original values without interpreting them
        String expectedTooltip = "Special tooltip with double quotes &quot;, simple quotes ', and html characters &amp;lt;&amp;gt;&amp;amp;.";
        testRootAction.tooltipContent = pristineTooltip;

        HtmlPage p = j.createWebClient().goTo(testRootAction.getUrlName());
        assertThat(p.getWebResponse().getContentAsString(), allOf(
                containsString(expectedTooltip),
                not(containsString(pristineTooltip))
        ));
    }

    @TestExtension
    public static class TestRootAction implements UnprotectedRootAction {
        public String tooltipContent = "";

        @Override
        public @CheckForNull String getUrlName() {
            return "test";
        }

        @Override
        public @CheckForNull String getIconFileName() {
            return null;
        }

        @Override
        public @CheckForNull String getDisplayName() {
            return null;
        }
    }
}
