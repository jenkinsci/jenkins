/*
 * The MIT License
 *
 * Copyright (c) 2013 RedHat Inc.
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

package jenkins.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.net.URL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest2;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

class JenkinsGetRootUrlTest {

    private Jenkins jenkins;
    private StaplerRequest2 staplerRequest;
    private JenkinsLocationConfiguration config;

    @BeforeEach
    void setUp() {
        jenkins = mock(Jenkins.class, Mockito.CALLS_REAL_METHODS);
        config = mock(JenkinsLocationConfiguration.class);
        staplerRequest = mock(StaplerRequest2.class);
    }

    @Test
    void getConfiguredRootUrl() {
        try (
                MockedStatic<JenkinsLocationConfiguration> mocked = mockStatic(JenkinsLocationConfiguration.class);
                MockedStatic<Stapler> mockedStapler = mockStatic(Stapler.class)
        ) {
            mocked.when(JenkinsLocationConfiguration::get).thenReturn(config);
            mockedStapler.when(Stapler::getCurrentRequest2).thenReturn(staplerRequest);

            configured("http://configured.host");

            rootUrlIs("http://configured.host/");
        }
    }

    @Test
    void getAccessedRootUrl() {
        try (
                MockedStatic<JenkinsLocationConfiguration> mocked = mockStatic(JenkinsLocationConfiguration.class);
                MockedStatic<Stapler> mockedStapler = mockStatic(Stapler.class)
        ) {
            mocked.when(JenkinsLocationConfiguration::get).thenReturn(config);
            mockedStapler.when(Stapler::getCurrentRequest2).thenReturn(staplerRequest);

            accessing("https://real.host/jenkins/");

            rootUrlIs("https://real.host/jenkins/");
        }
    }

    @Test
    void preferConfiguredOverAccessed() {
        try (
                MockedStatic<JenkinsLocationConfiguration> mocked = mockStatic(JenkinsLocationConfiguration.class);
                MockedStatic<Stapler> mockedStapler = mockStatic(Stapler.class)
        ) {
            mocked.when(JenkinsLocationConfiguration::get).thenReturn(config);
            mockedStapler.when(Stapler::getCurrentRequest2).thenReturn(staplerRequest);
            configured("http://configured.host/");
            accessing("http://real.host/");

            rootUrlIs("http://configured.host/");
        }
    }

    @Issue("JENKINS-16368")
    @Test
    void doNotInheritProtocolWhenDispatchingRequest() {
        try (
                MockedStatic<JenkinsLocationConfiguration> mocked = mockStatic(JenkinsLocationConfiguration.class);
                MockedStatic<Stapler> mockedStapler = mockStatic(Stapler.class)
        ) {
            mocked.when(JenkinsLocationConfiguration::get).thenReturn(config);
            mockedStapler.when(Stapler::getCurrentRequest2).thenReturn(staplerRequest);
            configured("http://configured.host/");
            accessing("https://real.host/");

            rootUrlIs("http://configured.host/");
        }
    }

    @Issue("JENKINS-16511")
    @Test
    void doNotInheritProtocolWhenDispatchingRequest2() {
        try (
                MockedStatic<JenkinsLocationConfiguration> mocked = mockStatic(JenkinsLocationConfiguration.class);
                MockedStatic<Stapler> mockedStapler = mockStatic(Stapler.class)
        ) {
            mocked.when(JenkinsLocationConfiguration::get).thenReturn(config);
            mockedStapler.when(Stapler::getCurrentRequest2).thenReturn(staplerRequest);
            configured("https://ci/jenkins/");
            accessing("http://localhost:8080/");
            rootUrlIs("https://ci/jenkins/");
        }
    }

    @Issue("JENKINS-10675")
    @Test
    void useForwardedProtoWhenPresent() {
        try (
                MockedStatic<JenkinsLocationConfiguration> mocked = mockStatic(JenkinsLocationConfiguration.class);
                MockedStatic<Stapler> mockedStapler = mockStatic(Stapler.class)
        ) {
            mocked.when(JenkinsLocationConfiguration::get).thenReturn(config);
            mockedStapler.when(Stapler::getCurrentRequest2).thenReturn(staplerRequest);
            configured("https://ci/jenkins/");

            // Without a forwarded protocol, it should use the request protocol
            accessing("http://ci/jenkins/");
            rootUrlFromRequestIs("http://ci/jenkins/");

            accessing("http://ci:8080/jenkins/");
            rootUrlFromRequestIs("http://ci:8080/jenkins/");

            // With a forwarded protocol, it should use the forwarded protocol
            accessing("https://ci/jenkins/");
            withHeader("X-Forwarded-Proto", "https");
            rootUrlFromRequestIs("https://ci/jenkins/");

            accessing("http://ci/jenkins/");
            withHeader("X-Forwarded-Proto", "http");
            rootUrlFromRequestIs("http://ci/jenkins/");

            // ServletRequest.getServerPort is not always meaningful.
            // http://tomcat.apache.org/tomcat-5.5-doc/config/http.html#Proxy_Support or
            // http://wiki.eclipse.org/Jetty/Howto/Configure_mod_proxy#Configuring_mod_proxy_as_a_Reverse_Proxy.5D
            // can be used to ensure that it is hardcoded or that X-Forwarded-Port is interpreted.
            // But this is not something that can be configured purely from the reverse proxy; the container must be modified too.
            // And the standard bundled Jetty in Jenkins does not work that way;
            // it will return 80 even when Jenkins is fronted by Apache with SSL.
            accessing("http://ci/jenkins/"); // as if the container is not aware of the forwarded port
            withHeader("X-Forwarded-Port", "443"); // but we tell it
            withHeader("X-Forwarded-Proto", "https");
            rootUrlFromRequestIs("https://ci/jenkins/");
        }
    }

    @Issue("JENKINS-58041")
    @Test
    void useForwardedProtoWithIPv6WhenPresent() {
        try (
                MockedStatic<JenkinsLocationConfiguration> mocked = mockStatic(JenkinsLocationConfiguration.class);
                MockedStatic<Stapler> mockedStapler = mockStatic(Stapler.class)
        ) {
            mocked.when(JenkinsLocationConfiguration::get).thenReturn(config);
            mockedStapler.when(Stapler::getCurrentRequest2).thenReturn(staplerRequest);
            configured("http://[::1]/jenkins/");

            // Without a forwarded protocol, it should use the request protocol
            accessing("http://[::1]/jenkins/");
            rootUrlFromRequestIs("http://[::1]/jenkins/");

            accessing("http://[::1]:8080/jenkins/");
            rootUrlFromRequestIs("http://[::1]:8080/jenkins/");

            // With a forwarded protocol, it should use the forwarded protocol
            accessing("http://[::1]/jenkins/");
            withHeader("X-Forwarded-Host", "[::2]");
            rootUrlFromRequestIs("http://[::2]/jenkins/");

            accessing("http://[::1]:8080/jenkins/");
            withHeader("X-Forwarded-Proto", "https");
            withHeader("X-Forwarded-Host", "[::1]:8443");
            rootUrlFromRequestIs("https://[::1]:8443/jenkins/");
        }

    }

    private void rootUrlFromRequestIs(final String expectedRootUrl) {

        assertThat(jenkins.getRootUrlFromRequest(), equalTo(expectedRootUrl));
    }

    private void rootUrlIs(final String expectedRootUrl) {

        assertThat(jenkins.getRootUrl(), equalTo(expectedRootUrl));
    }

    private void configured(final String configuredHost) {

        when(config.getUrl()).thenReturn(configuredHost);
    }

    private void withHeader(String name, final String value) {
        final StaplerRequest2 req = Stapler.getCurrentRequest2();
        when(req.getHeader(name)).thenReturn(value);
    }

    private void accessing(final String realUrl) {

        final URL url = getUrl(realUrl);

        final StaplerRequest2 req = mock(StaplerRequest2.class);
        when(req.getScheme()).thenReturn(url.getProtocol());
        when(req.getServerName()).thenReturn(url.getHost());
        when(req.getServerPort()).thenReturn(url.getPort() == -1 ? "https".equals(url.getProtocol()) ? 443 : 80 : url.getPort());
        when(req.getContextPath()).thenReturn(url.getPath().replaceAll("/$", ""));
        when(req.getIntHeader(anyString())).thenAnswer((Answer<Integer>) invocation -> {
            String name = (String) invocation.getArguments()[0];
            String value = ((StaplerRequest2) invocation.getMock()).getHeader(name);
            return value != null ? Integer.parseInt(value) : -1;
        });

        when(Stapler.getCurrentRequest2()).thenReturn(req);
    }

    private URL getUrl(final String realUrl) {

        try {

            return new URL(realUrl);
        } catch (Exception ex) {

            throw new RuntimeException(ex);
        }
    }
}
