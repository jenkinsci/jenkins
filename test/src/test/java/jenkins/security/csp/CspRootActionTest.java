/*
 * The MIT License
 *
 * Copyright (c) 2020, CloudBees, Inc.
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
package jenkins.security.csp;

import com.gargoylesoftware.htmlunit.BrowserVersionFeatures;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.model.RootAction;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

// only partial support in 2.40 (https://github.com/HtmlUnit/htmlunit/commit/56bd6c3a151896d3a84c5c02870dd4fe286d2b71)
// partial = frame only :'(
public class CspRootActionTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void isWebClientCspCapable() throws Exception {
        CspRootAction cspRootAction = j.jenkins.getExtensionList(RootAction.class).get(CspRootAction.class);
        cspRootAction.setDesiredPayload("payload<script>alert(123)</script>injected");
        cspRootAction.setReporterOnly(false);

        JenkinsRule.WebClient wc = j.createWebClient();
        Assume.assumeFalse(wc.getBrowserVersion().hasFeature(BrowserVersionFeatures.CONTENT_SECURITY_POLICY_IGNORED));

        AtomicBoolean alertReceived = new AtomicBoolean(false);
        wc.setAlertHandler((page, s) -> 
                alertReceived.set(true)
        );

        HtmlPage page = wc.goTo("csp-poc/testPayload");
        assertFalse("XSS not prevented by CSP", alertReceived.get());
    }

    @Test
    public void canWebClientReportCsp() throws Exception {
        CspRootAction cspRootAction = j.jenkins.getExtensionList(RootAction.class).get(CspRootAction.class);
        cspRootAction.setDesiredPayload("payload<script>alert(123)</script>injected");
        cspRootAction.setReporterOnly(true);

        JenkinsRule.WebClient wc = j.createWebClient();
        Assume.assumeFalse(wc.getBrowserVersion().hasFeature(BrowserVersionFeatures.CONTENT_SECURITY_POLICY_IGNORED));

        AtomicBoolean alertReceived = new AtomicBoolean(false);
        wc.setAlertHandler((page, s) -> alertReceived.set(true));

        int beforeSize = cspRootAction.reports.size();
        
        HtmlPage page = wc.goTo("csp-poc/testPayload");
        assertTrue("XSS should not be prevented due to report-only", alertReceived.get());
        assertThat("CSP report not sent by the WebClient", cspRootAction.reports.size(), greaterThan(beforeSize));
    }
}
