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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertFalse;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.model.UnprotectedRootAction;
import java.util.concurrent.atomic.AtomicBoolean;
import org.htmlunit.ScriptResult;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class SvgIconTest  {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    @Issue("JENKINS-60920")
    void regularUsage() throws Exception {
        TestRootAction testRootAction = j.jenkins.getExtensionList(UnprotectedRootAction.class).get(TestRootAction.class);

        String desiredTooltip = "Hello world!";
        testRootAction.tooltipContent = desiredTooltip;

        HtmlPage p = j.createWebClient().goTo(testRootAction.getUrlName());
        assertThat(p.getWebResponse().getContentAsString(), containsString(desiredTooltip));
    }

    @Test
    @Issue("JENKINS-60920")
    void onlyQuotesAreEscaped() throws Exception {
        TestRootAction testRootAction = j.jenkins.getExtensionList(UnprotectedRootAction.class).get(TestRootAction.class);

        String pristineTooltip = "Special tooltip with double quotes \", simple quotes ', and html characters <>&.";

        String expectedTooltip = "Special tooltip with double quotes &quot;, simple quotes ', and html characters &lt;&gt;&amp;.";
        testRootAction.tooltipContent = pristineTooltip;

        HtmlPage p = j.createWebClient().goTo(testRootAction.getUrlName());
        assertThat(p.getWebResponse().getContentAsString(), allOf(
                containsString(expectedTooltip),
                not(containsString(pristineTooltip))
        ));
    }

    @Test
    @Issue("SECURITY-1955")
    void preventXssFromTooltip() throws Exception {
        TestRootAction testRootAction = j.jenkins.getExtensionList(UnprotectedRootAction.class).get(TestRootAction.class);

        String desiredTooltip = "Tooltip with <img src=x onerror=alert(123)> payload included";
        testRootAction.tooltipContent = desiredTooltip;

        ensureXssIsPrevented(testRootAction, "Tooltip with", "<img");
    }

    private void ensureXssIsPrevented(TestRootAction testRootAction, String validationPart, String dangerousPart) throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient();

        AtomicBoolean alertTriggered = new AtomicBoolean(false);
        wc.setAlertHandler((p, s) -> alertTriggered.set(true));

        HtmlPage page = wc.goTo(testRootAction.getUrlName());

        // now it's a regular title, but without the correction, the tooltip will be triggered

        // title field is modified by Yahoo tooltip, title attribute is set by the new code
        ScriptResult controlResult = page.executeJavaScript("var s = document.querySelector('#test-panel svg'); s.title || s.getAttribute('title');");
        Object jsControlResult = controlResult.getJavaScriptResult();
        assertThat(jsControlResult, instanceOf(String.class));
        String jsControlString = (String) jsControlResult;
        assertThat("The title attribute is not populated", jsControlString, containsString(validationPart));

        page.executeJavaScript("document.querySelector('#test-panel svg')._tippy.show()");
        wc.waitForBackgroundJavaScript(1000);
        ScriptResult result = page.executeJavaScript("document.querySelector('.tippy-content').innerHTML;");
        Object jsResult = result.getJavaScriptResult();
        assertThat(jsResult, instanceOf(String.class));
        String jsResultString = (String) jsResult;

        assertThat("XSS not prevented (content)", jsResultString, not(containsString(dangerousPart)));
        assertFalse(alertTriggered.get(), "XSS not prevented (alert)");
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
