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

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;

import java.net.URL;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.Issue;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import static org.mockito.Matchers.anyString;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest({JenkinsLocationConfiguration.class, Stapler.class})
public class JenkinsGetRootUrlTest {

    private Jenkins jenkins;
    private JenkinsLocationConfiguration config;

    @Before
    public void setUp() {

        jenkins = mock(Jenkins.class, Mockito.CALLS_REAL_METHODS);
        config = mock(JenkinsLocationConfiguration.class);

        mockStatic(JenkinsLocationConfiguration.class);
        when(JenkinsLocationConfiguration.get()).thenReturn(config);

        mockStatic(Stapler.class);
    }

    @Test
    public void getConfiguredRootUrl() {

        configured("http://configured.host");

        rootUrlIs("http://configured.host/");
    }

    @Test
    public void getAccessedRootUrl() {

        accessing("https://real.host/jenkins/");

        rootUrlIs("https://real.host/jenkins/");
    }

    @Test
    public void preferConfiguredOverAccessed() {

        configured("http://configured.host/");
        accessing("http://real.host/");

        rootUrlIs("http://configured.host/");
    }

    @Issue("JENKINS-16368")
    @Test
    public void doNotInheritProtocolWhenDispatchingRequest() {

        configured("http://configured.host/");
        accessing("https://real.host/");

        rootUrlIs("http://configured.host/");
    }

    @Issue("JENKINS-16511")
    @Test
    public void doNotInheritProtocolWhenDispatchingRequest2() {
        configured("https://ci/jenkins/");
        accessing("http://localhost:8080/");
        rootUrlIs("https://ci/jenkins/");
    }
    
    @Issue("JENKINS-10675")
    @Test
    public void useForwardedProtoWhenPresent() {
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
        final StaplerRequest req = Stapler.getCurrentRequest();
        when(req.getHeader(name)).thenReturn(value);
    }

    private void accessing(final String realUrl) {

        final URL url = getUrl(realUrl);

        final StaplerRequest req = mock(StaplerRequest.class);
        when(req.getScheme()).thenReturn(url.getProtocol());
        when(req.getServerName()).thenReturn(url.getHost());
        when(req.getServerPort()).thenReturn(url.getPort() == -1 ? ("https".equals(url.getProtocol()) ? 443 : 80) : url.getPort());
        when(req.getContextPath()).thenReturn(url.getPath().replaceAll("/$", ""));
        when(req.getIntHeader(anyString())).thenAnswer(new Answer<Integer>() {
            @Override public Integer answer(InvocationOnMock invocation) throws Throwable {
                String name = (String) invocation.getArguments()[0];
                String value = ((StaplerRequest) invocation.getMock()).getHeader(name);
                return value != null ? Integer.parseInt(value) : -1;
            }
        });

        when(Stapler.getCurrentRequest()).thenReturn(req);
    }

    private URL getUrl(final String realUrl) {

        try {

            return new URL(realUrl);
        } catch(Exception ex) {

            throw new RuntimeException(ex);
        }
    }
}
