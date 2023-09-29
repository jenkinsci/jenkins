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

import static org.junit.Assert.assertThrows;

import java.net.URL;
import java.util.List;
import jenkins.model.JenkinsLocationConfiguration;
import org.htmlunit.FailingHttpStatusCodeException;
import org.htmlunit.WebRequest;
import org.htmlunit.util.NameValuePair;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.RestartableJenkinsRule;

public class ReverseProxySetupMonitorTest {

    @Rule
    public RestartableJenkinsRule rr = new RestartableJenkinsRule() {
        @Override
        protected JenkinsRule createJenkinsRule(Description description) {
            JenkinsRule j = super.createJenkinsRule(description);
            j.contextPath = desiredContextPath;
            return j;
        }
    };

    private String desiredContextPath;

    @Before
    public void resetContextPath() {
        this.desiredContextPath = "/jenkins";
    }

    @Test
    public void localhost_correct() {
        rr.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                JenkinsRule j = rr.j;
                JenkinsRule.WebClient wc = rr.j.createWebClient();
                WebRequest request = new WebRequest(new URL(j.getURL(), getAdminMonitorTestUrl(j)));
                request.setAdditionalHeader("Referer", j.getURL() + "manage");
                wc.getPage(request);
            }
        });
    }

    @Test
    public void localhost_testingForContext() {
        rr.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                JenkinsRule j = rr.j;
                JenkinsRule.WebClient wc = rr.j.createWebClient();
                WebRequest request = new WebRequest(new URL(j.getURL(), getAdminMonitorTestUrl(j)));
                request.setAdditionalHeader("Referer", j.getURL() + "manage");

                // As the context was already set inside the referer, adding another one will fail
                request.setRequestParameters(List.of(new NameValuePair("testWithContext", "true")));
                assertThrows(FailingHttpStatusCodeException.class, () -> wc.getPage(request));
            }
        });
    }

    @Test
    public void localhost_withoutReferer() {
        rr.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                JenkinsRule j = rr.j;
                JenkinsRule.WebClient wc = rr.j.createWebClient();
                WebRequest request = new WebRequest(new URL(j.getURL(), getAdminMonitorTestUrl(j)));
                // no referer
                assertThrows(FailingHttpStatusCodeException.class, () -> wc.getPage(request));
            }
        });
    }

    @Test
    public void localhost_withRefererNotComingFromManage() {
        rr.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                JenkinsRule j = rr.j;
                JenkinsRule.WebClient wc = rr.j.createWebClient();
                WebRequest request = new WebRequest(new URL(j.getURL(), getAdminMonitorTestUrl(j)));
                // wrong referer
                request.setAdditionalHeader("Referer", j.getURL() + "configure");
                assertThrows(FailingHttpStatusCodeException.class, () -> wc.getPage(request));
            }
        });
    }

    @Test
    public void withRootURL_localhost_missingContext() {
        rr.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                JenkinsRule j = rr.j;

                String fullRootUrl = j.getURL().toString();
                String rootUrlWithoutContext = fullRootUrl.replace("/jenkins", "");
                JenkinsLocationConfiguration.get().setUrl(rootUrlWithoutContext);

                JenkinsRule.WebClient wc = rr.j.createWebClient();
                WebRequest request = new WebRequest(new URL(j.getURL(), getAdminMonitorTestUrl(j)));
                request.setAdditionalHeader("Referer", j.getURL() + "manage");

                // As the rootURL is missing the context, a regular test will fail
                assertThrows(FailingHttpStatusCodeException.class, () -> wc.getPage(request));

                // When testing with the context, it will be OK, allowing to display an additional message
                request.setRequestParameters(List.of(new NameValuePair("testWithContext", "true")));
                wc.getPage(request);
            }
        });
    }

    @Test
    public void withRootURL_localhost_wrongContext() {
        rr.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                JenkinsRule j = rr.j;

                String fullRootUrl = j.getURL().toString();
                String rootUrlWithoutContext = fullRootUrl.replace("/jenkins", "/wrong");
                JenkinsLocationConfiguration.get().setUrl(rootUrlWithoutContext);

                JenkinsRule.WebClient wc = rr.j.createWebClient();
                WebRequest request = new WebRequest(new URL(j.getURL(), getAdminMonitorTestUrl(j)));
                request.setAdditionalHeader("Referer", j.getURL() + "manage");

                assertThrows(FailingHttpStatusCodeException.class, () -> wc.getPage(request));

                request.setRequestParameters(List.of(new NameValuePair("testWithContext", "true")));
                assertThrows(FailingHttpStatusCodeException.class, () -> wc.getPage(request));
            }
        });
    }

    @Test
    public void desiredContextPathEmpty_localhost() {
        desiredContextPath = "";
        rr.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                JenkinsRule j = rr.j;

                JenkinsRule.WebClient wc = rr.j.createWebClient();
                WebRequest request = new WebRequest(new URL(j.getURL(), getAdminMonitorTestUrl(j)));
                request.setAdditionalHeader("Referer", j.getURL() + "manage");

                wc.getPage(request);

                // adding the context does not have any impact as there is no configured context
                request.setRequestParameters(List.of(new NameValuePair("testWithContext", "true")));
                wc.getPage(request);
            }
        });
    }

    @Test
    public void usingIp_butRefererUsingRootUrl() {
        rr.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                JenkinsRule j = rr.j;
                JenkinsRule.WebClient wc = rr.j.createWebClient();
                WebRequest request = new WebRequest(new URL(getRootUrlWithIp(j), getAdminMonitorTestUrl(j)));
                request.setAdditionalHeader("Referer", j.getURL() + "manage");
                wc.getPage(request);
            }
        });
    }

    @Test
    public void usingIp_withoutReferer() {
        rr.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                JenkinsRule j = rr.j;
                JenkinsRule.WebClient wc = rr.j.createWebClient();
                WebRequest request = new WebRequest(new URL(getRootUrlWithIp(j), getAdminMonitorTestUrl(j)));
                // no referer
                assertThrows(FailingHttpStatusCodeException.class, () -> wc.getPage(request));
            }
        });
    }

    @Test
    public void usingIp_withRefererIp() {
        rr.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                JenkinsRule j = rr.j;
                JenkinsRule.WebClient wc = rr.j.createWebClient();
                WebRequest request = new WebRequest(new URL(getRootUrlWithIp(j), getAdminMonitorTestUrl(j)));
                // referer using IP
                request.setAdditionalHeader("Referer", getRootUrlWithIp(j) + "manage");

                // by default the JenkinsRule set the rootURL to localhost:<port>/jenkins
                // even with similar request and referer, if the root URL is set, this will show a wrong proxy setting
                assertThrows(FailingHttpStatusCodeException.class, () -> wc.getPage(request));
            }
        });
    }

    @Test
    public void withRootURL_usingIp_withRefererIp() {
        rr.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                JenkinsRule j = rr.j;
                JenkinsLocationConfiguration.get().setUrl(getRootUrlWithIp(j).toString());

                JenkinsRule.WebClient wc = rr.j.createWebClient();
                WebRequest request = new WebRequest(new URL(getRootUrlWithIp(j), getAdminMonitorTestUrl(j)));
                // referer using IP
                request.setAdditionalHeader("Referer", getRootUrlWithIp(j) + "manage");

                wc.getPage(request);
            }
        });
    }

    @Test
    public void withRootURL_usingIp_missingContext_withRefererIp() {
        rr.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                JenkinsRule j = rr.j;

                String fullRootUrl = getRootUrlWithIp(j).toString();
                String rootUrlWithoutContext = fullRootUrl.replace("/jenkins", "");
                JenkinsLocationConfiguration.get().setUrl(rootUrlWithoutContext);

                JenkinsRule.WebClient wc = rr.j.createWebClient();
                WebRequest request = new WebRequest(new URL(getRootUrlWithIp(j), getAdminMonitorTestUrl(j)));
                // referer using IP
                request.setAdditionalHeader("Referer", getRootUrlWithIp(j) + "manage");

                // As the rootURL is missing the context, a regular test will fail
                assertThrows(FailingHttpStatusCodeException.class, () -> wc.getPage(request));

                // When testing with the context, it will be OK, allowing to display an additional message
                request.setRequestParameters(List.of(new NameValuePair("testWithContext", "true")));
                wc.getPage(request);
            }
        });
    }

    private String getAdminMonitorTestUrl(JenkinsRule j) {
        return j.jenkins.getAdministrativeMonitor(ReverseProxySetupMonitor.class.getName()).getUrl() + "/test";
    }

    private URL getRootUrlWithIp(JenkinsRule j) throws Exception {
        return new URL(j.getURL().toString().replace("localhost", "127.0.0.1"));
    }
}
