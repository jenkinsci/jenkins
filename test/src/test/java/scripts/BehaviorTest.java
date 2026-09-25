/*
 * The MIT License
 *
 * Copyright (c) 2011, CloudBees, Inc.
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

package scripts;

import static org.junit.jupiter.api.Assertions.assertEquals;

import hudson.model.InvisibleAction;
import hudson.model.RootAction;
import org.htmlunit.ScriptResult;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Tests <code>behaviour.js</code>
 *
 * @author Kohsuke Kawaguchi
 */
@WithJenkins
class BehaviorTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void testCssSelectors() throws Exception {
        HtmlPage p = j.createWebClient().goTo("self/testCssSelectors");

        // basic class selector, that we use the most often
        assertEquals(2, asInt(p.executeJavaScript("findElementsBySelector(document.getElementById('test1'),'.a',true).length")));
        assertEquals(1, asInt(p.executeJavaScript("findElementsBySelector(document.getElementById('test1'),'.a',false).length")));

        // 'includeSelf' should only affect the first axis and not afterward
        assertEquals(1, asInt(p.executeJavaScript("findElementsBySelector(document.getElementById('test2'),'.a .b',true).length")));
        assertEquals(1, asInt(p.executeJavaScript("findElementsBySelector(document.getElementById('test2'),'.a .b',false).length")));

        // tag.class. Should exclude itself anyway even if it's included
        assertEquals(1, asInt(p.executeJavaScript("findElementsBySelector(document.getElementById('test3'),'P.a',true).length")));
        assertEquals(1, asInt(p.executeJavaScript("findElementsBySelector(document.getElementById('test3'),'P.a',false).length")));
    }

    private int asInt(ScriptResult r) {
        return ((Double) r.getJavaScriptResult()).intValue();
    }

    @Issue("JENKINS-14495")
    @Test
    void testDuplicateRegistrations() throws Exception {
        HtmlPage p = j.createWebClient().goTo("self/testDuplicateRegistrations");
        ScriptResult r = p.executeJavaScript("document.getElementsBySelector('DIV.a')[0].innerHTML");
        assertEquals("initial and appended yet different", r.getJavaScriptResult().toString());
    }

    @Test
    void testSelectorOrdering() throws Exception {
        HtmlPage p = j.createWebClient().goTo("self/testSelectorOrdering");
        ScriptResult r = p.executeJavaScript("document.getElementsBySelector('DIV.a')[0].innerHTML");
        assertEquals("initial early counted! generic weevils! late", r.getJavaScriptResult().toString());
    }

    @TestExtension
    public static final class RootActionImpl extends InvisibleAction implements RootAction {
        @Override
        public String getUrlName() {
            return "self";
        }
    }
}
