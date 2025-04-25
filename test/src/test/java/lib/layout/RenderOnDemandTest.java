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

package lib.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import hudson.model.InvisibleAction;
import hudson.model.RootAction;
import org.htmlunit.ScriptResult;
import org.htmlunit.WebClientUtil;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Tests &lt;renderOnDemand> tag.
 *
 * @author Kohsuke Kawaguchi
 */
@WithJenkins
class RenderOnDemandTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    /**
     * Makes sure that the behavior rules are applied to newly inserted nodes,
     * even when multiple nodes are added.
     */
    @Test
    void testBehaviour() throws Exception {
        HtmlPage p = j.createWebClient().goTo("self/testBehaviour");

        p.executeJavaScript("renderOnDemand(document.getElementsBySelector('.lazy')[0])");
        WebClientUtil.waitForJSExec(p.getWebClient());
        // all AJAX calls complete before the above method returns

        ScriptResult r = p.executeJavaScript("var r=document.getElementsBySelector('DIV.a'); r[0].innerHTML+r[1].innerHTML+r[2].innerHTML");
        WebClientUtil.waitForJSExec(p.getWebClient());
        assertEquals("AlphaBravoCharlie", r.getJavaScriptResult().toString());
    }

    /*
    @Test
    public void testMemoryConsumption() throws Exception {
        j.createWebClient().goTo("self/testBehaviour"); // prime caches
        int total = 0;
        for (MemoryAssert.HistogramElement element : MemoryAssert.increasedMemory(new Callable<Void>() {
            @Override public Void call() throws Exception {
                j.createWebClient().goTo("self/testBehaviour");
                return null;
            }
        }, new Filter() {
            @Override public boolean accept(Object obj, Object referredFrom, Field reference) {
                return !obj.getClass().getName().contains("htmlunit");
            }
        })) {
            total += element.byteSize;
            System.out.println(element.className + " ×" + element.instanceCount + ": " + element.byteSize);
        }
        System.out.println("total: " + total);
    }
    */

    /**
     * Makes sure that scripts get evaluated.
     */
    @Test
    void testScript() throws Exception {
        HtmlPage p = j.createWebClient().goTo("self/testScript");
        assertNull(p.getElementById("loaded"));

        p.getElementById("button").click();
        WebClientUtil.waitForJSExec(p.getWebClient());
        // all AJAX calls complete before the above method returns
        assertNotNull(p.getElementById("loaded"));
        ScriptResult r = p.executeJavaScript("x");
        WebClientUtil.waitForJSExec(p.getWebClient());

        assertEquals("xxx", r.getJavaScriptResult().toString());

        r = p.executeJavaScript("y");
        WebClientUtil.waitForJSExec(p.getWebClient());
        assertEquals("yyy", r.getJavaScriptResult().toString());

        // if you want to test this in the browser
        /*
        System.out.println("Try http://localhost:"+localPort+"/self/testScript");
        j.interactiveBreak();
        */
    }

    @TestExtension
    public static final class RootActionImpl extends InvisibleAction implements RootAction {
        @Override
        public String getUrlName() {
            return "self";
        }
    }
}
