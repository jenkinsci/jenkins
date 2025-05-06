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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import hudson.model.InvisibleAction;
import hudson.model.RootAction;
import hudson.widgets.RenderOnDemandClosure;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.logging.Level;
import org.htmlunit.ScriptResult;
import org.htmlunit.WebClientUtil;
import org.htmlunit.html.HtmlPage;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.MemoryAssert;
import org.jvnet.hudson.test.TestExtension;

/**
 * Tests &lt;renderOnDemand> tag.
 *
 * @author Kohsuke Kawaguchi
 */
public class RenderOnDemandTest {

    @Rule public JenkinsRule j = new JenkinsRule();
    @Rule public LoggerRule logging = new LoggerRule().record(RenderOnDemandClosure.class, Level.FINE);

    /**
     * Makes sure that the behavior rules are applied to newly inserted nodes,
     * even when multiple nodes are added.
     */
    @Test
    public void testBehaviour() throws Exception {
        HtmlPage p = j.createWebClient().goTo("self/testBehaviour");

        p.executeJavaScript("renderOnDemand(document.getElementsBySelector('.lazy')[0])");
        WebClientUtil.waitForJSExec(p.getWebClient());
        // all AJAX calls complete before the above method returns

        ScriptResult r = p.executeJavaScript("var r=document.getElementsBySelector('DIV.a'); r[0].innerHTML+r[1].innerHTML+r[2].innerHTML");
        WebClientUtil.waitForJSExec(p.getWebClient());
        assertEquals("AlphaBravoCharlie", r.getJavaScriptResult().toString());
    }

    @Ignore("just informational")
    @Issue("JENKINS-16341")
    @Test
    public void testMemoryConsumption() throws Exception {
        var wc = j.createWebClient();
        callTestBehaviour(wc); // prime caches
        int total = 0;
        int count = 50;
        for (var element : MemoryAssert.increasedMemory(() -> {
            for (int i = 0; i < count; i++) {
                System.err.println("#" + i);
                callTestBehaviour(wc);
            }
            var o = new Object();
            var sr = new SoftReference<>(o);
            var wr = new WeakReference<>(o);
            o = null;
            MemoryAssert.assertGC(wr, true);
            return null;
        }, (obj, referredFrom, reference) -> !obj.getClass().getName().contains("htmlunit"))) {
            total += element.byteSize;
            if (element.instanceCount == count) {
                System.out.print("⚠ ️");
            }
            System.out.println(element.className + " ×" + element.instanceCount + ": " + element.byteSize);
        }
        System.out.println("total: " + total);
    }

    private void callTestBehaviour(JenkinsRule.WebClient wc) throws Exception {
        var p = wc.goTo("self/testBehaviour");
        p.executeJavaScript("renderOnDemand(document.getElementsBySelector('.lazy')[0])");
        WebClientUtil.waitForJSExec(p.getWebClient());
    }

    /**
     * Makes sure that scripts get evaluated.
     */
    @Test
    public void testScript() throws Exception {
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
