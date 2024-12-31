/*
 * The MIT License
 *
 * Copyright (c) 2013 Red Hat, Inc.
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

package hudson.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import hudson.security.ACL;
import hudson.security.AccessDeniedException3;
import hudson.security.GlobalMatrixAuthorizationStrategy;
import hudson.agents.DumbAgent;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import jenkins.model.Jenkins;
import org.htmlunit.FailingHttpStatusCodeException;
import org.htmlunit.HttpMethod;
import org.htmlunit.WebRequest;
import org.htmlunit.WebResponse;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.FakeLauncher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.PretendAgent;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * @author ogondza
 */
public class ComputerConfigDotXmlTest {

    @Rule public final JenkinsRule rule = new JenkinsRule();

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Mock private StaplerRequest2 req;
    @Mock private StaplerResponse2 rsp;

    private Computer computer;
    private SecurityContext oldSecurityContext;
    private AutoCloseable mocks;

    @Before
    public void setUp() throws Exception {

        mocks = MockitoAnnotations.openMocks(this);
        computer = spy(rule.createAgent().toComputer());
        rule.jenkins.setSecurityRealm(rule.createDummySecurityRealm());
        oldSecurityContext = ACL.impersonate2(User.getOrCreateByIdOrFullName("user").impersonate2());
    }

    @After
    public void tearDown() throws Exception {
        mocks.close();
        SecurityContextHolder.setContext(oldSecurityContext);
    }

    @Test
    public void configXmlGetShouldFailForUnauthorized() {

        when(req.getMethod()).thenReturn("GET");

        rule.jenkins.setAuthorizationStrategy(new GlobalMatrixAuthorizationStrategy());

        assertThrows(AccessDeniedException3.class, () -> computer.doConfigDotXml(req, rsp));
    }

    @Test
    public void configXmlPostShouldFailForUnauthorized() {

        when(req.getMethod()).thenReturn("POST");

        rule.jenkins.setAuthorizationStrategy(new GlobalMatrixAuthorizationStrategy());

        assertThrows(AccessDeniedException3.class, () -> computer.doConfigDotXml(req, rsp));
    }

    @Test
    public void configXmlGetShouldYieldNodeConfiguration() throws Exception {

        when(req.getMethod()).thenReturn("GET");

        GlobalMatrixAuthorizationStrategy auth = new GlobalMatrixAuthorizationStrategy();
        rule.jenkins.setAuthorizationStrategy(auth);
        Computer.EXTENDED_READ.setEnabled(true);
        auth.add(Computer.EXTENDED_READ, "user");

        final OutputStream outputStream = captureOutput();

        computer.doConfigDotXml(req, rsp);

        final String out = outputStream.toString();
        assertThat(out, startsWith("<?xml version=\"1.1\" encoding=\"UTF-8\"?>"));
        assertThat(out, containsString("<name>agent0</name>"));
        assertThat(out, containsString("<mode>NORMAL</mode>"));
    }

    @Test
    public void configXmlPostShouldUpdateNodeConfiguration() throws Exception {

        when(req.getMethod()).thenReturn("POST");

        GlobalMatrixAuthorizationStrategy auth = new GlobalMatrixAuthorizationStrategy();
        rule.jenkins.setAuthorizationStrategy(auth);
        auth.add(Computer.CONFIGURE, "user");

        when(req.getInputStream()).thenReturn(xmlNode("node.xml"));

        computer.doConfigDotXml(req, rsp);

        final Node updatedAgent = rule.jenkins.getNode("AgentFromXML");
        assertThat(updatedAgent.getNodeName(), equalTo("AgentFromXML"));
        assertThat(updatedAgent.getNumExecutors(), equalTo(42));
    }

    @Test
    @Issue("SECURITY-343")
    public void emptyNodeMonitorDataWithoutConnect() {
        rule.jenkins.setAuthorizationStrategy(new GlobalMatrixAuthorizationStrategy());

        assertTrue(computer.getMonitorData().isEmpty());
    }

    @Test
    @Issue("SECURITY-343")
    public void populatedNodeMonitorDataWithConnect() {
        GlobalMatrixAuthorizationStrategy auth = new GlobalMatrixAuthorizationStrategy();
        rule.jenkins.setAuthorizationStrategy(auth);
        auth.add(Computer.CONNECT, "user");

        assertFalse(computer.getMonitorData().isEmpty());
    }

    @Issue("SECURITY-1721")
    @Test
    public void cannotChangeNodeType() throws Exception {
        PretendAgent agent = rule.createPretendAgent(p -> new FakeLauncher.FinishedProc(0));
        String name = agent.getNodeName();
        assertThat(name, is(not(emptyOrNullString())));
        Computer computer = agent.toComputer();
        assertThat(computer, is(notNullValue()));

        JenkinsRule.WebClient wc = rule.createWebClient().withThrowExceptionOnFailingStatusCode(false);
        WebRequest req = new WebRequest(wc.createCrumbedUrl(String.format("%s/config.xml", computer.getUrl())), HttpMethod.POST);
        req.setAdditionalHeader("Content-Type", "application/xml");
        // to ensure maximum compatibility of payload, we'll serialize a real one with the same name
        DumbAgent mole = new DumbAgent(name, temporaryFolder.newFolder().getPath(), rule.createComputerLauncher(null));
        req.setRequestBody(Jenkins.XSTREAM.toXML(mole));
        WebResponse response = wc.getPage(req).getWebResponse();
        assertThat(response.getStatusCode(), is(400));

        // verify node hasn't been transformed into a different type
        Node node = rule.jenkins.getNode(name);
        assertThat(node, instanceOf(PretendAgent.class));
    }

    @Issue("SECURITY-2021")
    @Test
    public void nodeNameReferencesParentDir() throws Exception {
        Computer computer = rule.createAgent("anything", null).toComputer();

        JenkinsRule.WebClient wc = rule.createWebClient();
        WebRequest req = new WebRequest(wc.createCrumbedUrl(String.format("%s/config.xml", computer.getUrl())), HttpMethod.POST);
        req.setAdditionalHeader("Content-Type", "application/xml");
        req.setRequestBody(VALID_XML_BAD_NAME_XML);

        FailingHttpStatusCodeException e = assertThrows(FailingHttpStatusCodeException.class, () -> wc.getPage(req));
        assertThat(e.getStatusCode(), equalTo(400));
        File configDotXml = new File(rule.jenkins.getRootDir(), "config.xml");
        String configDotXmlContents = Files.readString(configDotXml.toPath(), StandardCharsets.UTF_8);

        assertThat(configDotXmlContents, not(containsString("<name>../</name>")));
    }

    private static final String VALID_XML_BAD_NAME_XML =
            "<agent>\n" +
                    "  <name>../</name>\n" +
                    "</agent>";

    private OutputStream captureOutput() throws IOException {

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();

        when(rsp.getOutputStream()).thenReturn(new ServletOutputStream() {

            @Override
            public void write(int b) {
                baos.write(b);
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setWriteListener(WriteListener writeListener) {
                throw new UnsupportedOperationException();
            }
        });

        return baos;
    }

    private ServletInputStream xmlNode(final String name) {

        class Stream extends ServletInputStream {

            private final InputStream inner;

            Stream(final InputStream inner) {
                this.inner = inner;
            }

            @Override
            public int read() throws IOException {
                return inner.read();
            }

            @Override
            public int read(byte[] b) throws IOException {
                return inner.read(b);
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                return inner.read(b, off, len);
            }

            @Override
            public boolean isFinished() {
                return false;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
                throw new UnsupportedOperationException();
            }
        }

        return new Stream(Computer.class.getResourceAsStream(name));
    }
}
