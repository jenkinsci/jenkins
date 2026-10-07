/*
 * The MIT License
 *
 * Copyright 2014 Jesse Glick.
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

package hudson.diagnosis;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URL;
import java.util.List;
import jenkins.model.JenkinsLocationConfiguration;
import org.htmlunit.FailingHttpStatusCodeException;
import org.htmlunit.WebRequest;
import org.htmlunit.util.NameValuePair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class ReverseProxySetupMonitorTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void localhost_correct() throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient();
        WebRequest request = new WebRequest(new URL(j.getURL(), getAdminMonitorTestUrl(j)));
        request.setAdditionalHeader("Referer", j.getURL() + "manage");
        wc.getPage(request);
    }

    @Test
    void localhost_testingForContext() throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient();
        WebRequest request = new WebRequest(new URL(j.getURL(), getAdminMonitorTestUrl(j)));
        request.setAdditionalHeader("Referer", j.getURL() + "manage");

        // As the context was already set inside the referer, adding another one will fail
        request.setRequestParameters(List.of(new NameValuePair("testWithContext", "true")));
        assertThrows(FailingHttpStatusCodeException.class, () -> wc.getPage(request));
    }

    @Test
    void localhost_withoutReferer() throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient();
        WebRequest request = new WebRequest(new URL(j.getURL(), getAdminMonitorTestUrl(j)));
        // no referer
        assertThrows(FailingHttpStatusCodeException.class, () -> wc.getPage(request));
    }

    @Test
    void localhost_withRefererNotComingFromManage() throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient();
        WebRequest request = new WebRequest(new URL(j.getURL(), getAdminMonitorTestUrl(j)));
        // wrong referer
        request.setAdditionalHeader("Referer", j.getURL() + "configure");
        assertThrows(FailingHttpStatusCodeException.class, () -> wc.getPage(request));
    }

    @Test
    void withRootURL_localhost_missingContext() throws Exception {
        String fullRootUrl = j.getURL().toString();
        String rootUrlWithoutContext = fullRootUrl.replace("/jenkins", "");
        JenkinsLocationConfiguration.get().setUrl(rootUrlWithoutContext);

        JenkinsRule.WebClient wc = j.createWebClient();
        WebRequest request = new WebRequest(new URL(j.getURL(), getAdminMonitorTestUrl(j)));
        request.setAdditionalHeader("Referer", j.getURL() + "manage");

        // As the rootURL is missing the context, a regular test will fail
        assertThrows(FailingHttpStatusCodeException.class, () -> wc.getPage(request));

        // When testing with the context, it will be OK, allowing to display an additional message
        request.setRequestParameters(List.of(new NameValuePair("testWithContext", "true")));
        wc.getPage(request);
    }

    @Test
    void withRootURL_localhost_wrongContext() throws Exception {
        String fullRootUrl = j.getURL().toString();
        String rootUrlWithoutContext = fullRootUrl.replace("/jenkins", "/wrong");
        JenkinsLocationConfiguration.get().setUrl(rootUrlWithoutContext);

        JenkinsRule.WebClient wc = j.createWebClient();
        WebRequest request = new WebRequest(new URL(j.getURL(), getAdminMonitorTestUrl(j)));
        request.setAdditionalHeader("Referer", j.getURL() + "manage");

        assertThrows(FailingHttpStatusCodeException.class, () -> wc.getPage(request));

        request.setRequestParameters(List.of(new NameValuePair("testWithContext", "true")));
        assertThrows(FailingHttpStatusCodeException.class, () -> wc.getPage(request));
    }

    @Test
    void desiredContextPathEmpty_localhost() throws Throwable {
        j.contextPath = "";

        j.restart();

        JenkinsRule.WebClient wc = j.createWebClient();
        WebRequest request = new WebRequest(new URL(j.getURL(), getAdminMonitorTestUrl(j)));
        request.setAdditionalHeader("Referer", j.getURL() + "manage");

        wc.getPage(request);

        // adding the context does not have any impact as there is no configured context
        request.setRequestParameters(List.of(new NameValuePair("testWithContext", "true")));
        wc.getPage(request);
    }

    @Test
    void usingIp_butRefererUsingRootUrl() throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient();
        WebRequest request = new WebRequest(new URL(getRootUrlWithIp(j), getAdminMonitorTestUrl(j)));
        request.setAdditionalHeader("Referer", j.getURL() + "manage");
        wc.getPage(request);
    }

    @Test
    void usingIp_withoutReferer() throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient();
        WebRequest request = new WebRequest(new URL(getRootUrlWithIp(j), getAdminMonitorTestUrl(j)));
        // no referer
        assertThrows(FailingHttpStatusCodeException.class, () -> wc.getPage(request));
    }

    @Test
    void usingIp_withRefererIp() throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient();
        WebRequest request = new WebRequest(new URL(getRootUrlWithIp(j), getAdminMonitorTestUrl(j)));
        // referer using IP
        request.setAdditionalHeader("Referer", getRootUrlWithIp(j) + "manage");

        // by default the JenkinsRule set the rootURL to localhost:<port>/jenkins
        // even with similar request and referer, if the root URL is set, this will show a wrong proxy setting
        assertThrows(FailingHttpStatusCodeException.class, () -> wc.getPage(request));
    }

    @Test
    void withRootURL_usingIp_withRefererIp() throws Exception {
        JenkinsLocationConfiguration.get().setUrl(getRootUrlWithIp(j).toString());

        JenkinsRule.WebClient wc = j.createWebClient();
        WebRequest request = new WebRequest(new URL(getRootUrlWithIp(j), getAdminMonitorTestUrl(j)));
        // referer using IP
        request.setAdditionalHeader("Referer", getRootUrlWithIp(j) + "manage");

        wc.getPage(request);
    }

    @Test
    void withRootURL_usingIp_missingContext_withRefererIp() throws Exception {
        String fullRootUrl = getRootUrlWithIp(j).toString();
        String rootUrlWithoutContext = fullRootUrl.replace("/jenkins", "");
        JenkinsLocationConfiguration.get().setUrl(rootUrlWithoutContext);

        JenkinsRule.WebClient wc = j.createWebClient();
        WebRequest request = new WebRequest(new URL(getRootUrlWithIp(j), getAdminMonitorTestUrl(j)));
        // referer using IP
        request.setAdditionalHeader("Referer", getRootUrlWithIp(j) + "manage");

        // As the rootURL is missing the context, a regular test will fail
        assertThrows(FailingHttpStatusCodeException.class, () -> wc.getPage(request));

        // When testing with the context, it will be OK, allowing to display an additional message
        request.setRequestParameters(List.of(new NameValuePair("testWithContext", "true")));
        wc.getPage(request);
    }

    private String getAdminMonitorTestUrl(JenkinsRule j) {
        return j.jenkins.getAdministrativeMonitor(ReverseProxySetupMonitor.class.getName()).getUrl() + "/test";
    }

    private URL getRootUrlWithIp(JenkinsRule j) throws Exception {
        return new URL(j.getURL().toString().replace("localhost", "127.0.0.1"));
    }
}
